package com.lyxor.auth.controller;

import com.lyxor.auth.context.SecurityContextHolder;
import com.lyxor.auth.model.ApiKeyMetadata;
import com.lyxor.auth.model.AuthToken;
import com.lyxor.auth.model.UserPrincipal;
import com.lyxor.auth.service.ApiKeyAuthenticationService;
import com.lyxor.auth.service.AuthRateLimiter;
import com.lyxor.auth.service.JwtTokenService;
import com.lyxor.auth.service.PasswordResetTokenService;
import com.lyxor.auth.service.RefreshTokenVault;
import com.lyxor.auth.service.RoleHierarchyService;
import com.lyxor.auth.service.SecretKeyRotationManager;
import com.lyxor.auth.service.TokenRevocationService;
import com.lyxor.auth.service.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final ApiKeyAuthenticationService apiKeyService;
    private final RoleHierarchyService roleHierarchyService;
    private final TokenRevocationService revocationService;
    private final RefreshTokenVault refreshTokenVault;
    private final AuthRateLimiter rateLimiter;
    private final PasswordResetTokenService resetTokenService;
    private final SecretKeyRotationManager rotationManager;

    public AuthenticationController(
            UserRepository userRepository,
            JwtTokenService jwtTokenService,
            ApiKeyAuthenticationService apiKeyService,
            RoleHierarchyService roleHierarchyService,
            TokenRevocationService revocationService,
            RefreshTokenVault refreshTokenVault,
            AuthRateLimiter rateLimiter,
            PasswordResetTokenService resetTokenService,
            SecretKeyRotationManager rotationManager) {
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
        this.roleHierarchyService = roleHierarchyService;
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
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String clientIp = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        if (!rateLimiter.tryAcquire(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded"));
        }

        Optional<UserPrincipal> userOpt = userRepository.findByUsername(request.username());
        if (userOpt.isEmpty() || !userRepository.verifyPassword(request.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        UserPrincipal principal = userOpt.get();
        SecurityContextHolder.setContext(principal);

        AuthToken token = jwtTokenService.generateToken(principal);
        refreshTokenVault.storeRefreshToken(token.refreshToken(), principal);

        return ResponseEntity.ok(Map.of(
                "accessToken", token.accessToken(),
                "refreshToken", token.refreshToken(),
                "tokenType", token.tokenType(),
                "expiresIn", token.expiresInSeconds()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshRequest request) {
        Optional<UserPrincipal> principalOpt = refreshTokenVault.findPrincipalByToken(request.refreshToken());
        if (principalOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid refresh token"));
        }

        String newRefreshToken = refreshTokenVault.rotateToken(request.refreshToken());
        UserPrincipal principal = principalOpt.get();
        AuthToken token = jwtTokenService.generateToken(principal);
        refreshTokenVault.storeRefreshToken(newRefreshToken, principal);

        return ResponseEntity.ok(Map.of(
                "accessToken", token.accessToken(),
                "refreshToken", newRefreshToken
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            revocationService.revokeToken(token);
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        String token = resetTokenService.generateResetToken(request.email());
        return ResponseEntity.ok(Map.of("resetToken", token, "status", "RESET_TOKEN_GENERATED"));
    }

    @GetMapping("/verify-key")
    public ResponseEntity<Map<String, Object>> verifyKey(@RequestHeader("X-API-KEY") String apiKey) {
        Optional<ApiKeyMetadata> metadataOpt = apiKeyService.authenticateApiKey(apiKey);
        if (metadataOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid API key"));
        }
        ApiKeyMetadata metadata = metadataOpt.get();
        return ResponseEntity.ok(Map.of("clientId", metadata.getClientId(), "keyId", metadata.getKeyId(), "status", "AUTHENTICATED"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<String>> getActiveSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(List.of("session-1", "session-2"));
    }
}
