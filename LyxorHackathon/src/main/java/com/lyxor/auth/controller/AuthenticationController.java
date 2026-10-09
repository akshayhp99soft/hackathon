package com.lyxor.auth.controller;

import com.lyxor.auth.context.SecurityContextHolder;
import com.lyxor.auth.model.AuthToken;
import com.lyxor.auth.model.UserPrincipal;
import com.lyxor.auth.service.ApiKeyAuthenticationService;
import com.lyxor.auth.service.AuthRateLimiter;
import com.lyxor.auth.service.JwtTokenService;
import com.lyxor.auth.service.PasswordResetTokenService;
import com.lyxor.auth.service.RbacAuthorizationService;
import com.lyxor.auth.service.RefreshTokenVault;
import com.lyxor.auth.service.SecretKeyRotationManager;
import com.lyxor.auth.service.TokenRevocationService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final JwtTokenService jwtTokenService;
    private final ApiKeyAuthenticationService apiKeyService;
    private final RbacAuthorizationService rbacService;
    private final TokenRevocationService revocationService;
    private final RefreshTokenVault refreshTokenVault;
    private final AuthRateLimiter rateLimiter;
    private final PasswordResetTokenService resetTokenService;
    private final SecretKeyRotationManager rotationManager;

    public AuthenticationController(
            JwtTokenService jwtTokenService,
            ApiKeyAuthenticationService apiKeyService,
            RbacAuthorizationService rbacService,
            TokenRevocationService revocationService,
            RefreshTokenVault refreshTokenVault,
            AuthRateLimiter rateLimiter,
            PasswordResetTokenService resetTokenService,
            SecretKeyRotationManager rotationManager) {
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
        this.rbacService = rbacService;
        this.revocationService = revocationService;
        this.refreshTokenVault = refreshTokenVault;
        this.rateLimiter = rateLimiter;
        this.resetTokenService = resetTokenService;
        this.rotationManager = rotationManager;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record PasswordResetRequest(@NotBlank String email) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Forwarded-For", defaultValue = "127.0.0.1") String clientIp) {

        if (!rateLimiter.tryAcquire(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded"));
        }

        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }

        UserPrincipal principal = new UserPrincipal(UUID.randomUUID().toString(), request.username(), Set.of("ROLE_USER"));
        SecurityContextHolder.setContext(principal);

        AuthToken token = jwtTokenService.generateToken(principal);
        refreshTokenVault.issueToken(principal.userId(), token.refreshToken());

        return ResponseEntity.ok(Map.of(
                "accessToken", token.accessToken(),
                "refreshToken", token.refreshToken(),
                "tokenType", token.tokenType(),
                "expiresIn", token.expiresInSeconds()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody RefreshRequest request) {
        if (!refreshTokenVault.isValid(request.refreshToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid refresh token"));
        }

        String newRefreshToken = refreshTokenVault.rotateToken(request.refreshToken());
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID().toString(), "refreshed-user", Set.of("ROLE_USER"));
        AuthToken token = jwtTokenService.generateToken(principal);

        return ResponseEntity.ok(Map.of(
                "accessToken", token.accessToken(),
                "refreshToken", newRefreshToken
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestParam String tokenId) {
        revocationService.revokeToken(tokenId);
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        String token = resetTokenService.generateResetToken(request.email());
        return ResponseEntity.ok(Map.of("resetToken", token, "status", "RESET_TOKEN_GENERATED"));
    }

    @GetMapping("/verify-key")
    public ResponseEntity<Map<String, Object>> verifyKey(@RequestHeader("X-API-KEY") String apiKey) {
        String clientId = apiKeyService.authenticateClient(apiKey);
        if (clientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid API key"));
        }
        return ResponseEntity.ok(Map.of("clientId", clientId, "status", "AUTHENTICATED"));
    }
}
