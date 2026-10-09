package com.lyxor.auth;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationVaultTests {

    private JwtTokenService jwtTokenService;
    private ApiKeyAuthenticationService apiKeyService;
    private RbacAuthorizationService rbacService;
    private TokenRevocationService revocationService;
    private RefreshTokenVault refreshTokenVault;
    private AuthRateLimiter rateLimiter;
    private PasswordResetTokenService resetTokenService;
    private SecretKeyRotationManager rotationManager;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService();
        apiKeyService = new ApiKeyAuthenticationService();
        rbacService = new RbacAuthorizationService();
        revocationService = new TokenRevocationService();
        refreshTokenVault = new RefreshTokenVault();
        rateLimiter = new AuthRateLimiter(5, 1);
        resetTokenService = new PasswordResetTokenService();
        rotationManager = new SecretKeyRotationManager();
    }

    @Test
    void testTokenGenerationAndValidation() {
        UserPrincipal principal = new UserPrincipal("u100", "testuser", Set.of("ROLE_USER"));
        AuthToken token = jwtTokenService.generateToken(principal);

        assertNotNull(token);
        assertNotNull(token.accessToken());
        assertNotNull(token.refreshToken());
        assertTrue(jwtTokenService.validateToken(token.accessToken()));
    }

    @Test
    void testApiKeyAuthentication() {
        apiKeyService.registerApiKey("client-abc", "sec-key-12345");
        String authenticated = apiKeyService.authenticateClient("sec-key-12345");
        assertEquals("client-abc", authenticated);

        assertNull(apiKeyService.authenticateClient("wrong-key"));
    }

    @Test
    void testRbacPermissions() {
        UserPrincipal user = new UserPrincipal("u2", "admin-user", Set.of("ROLE_ADMIN", "ROLE_USER"));
        assertTrue(rbacService.hasRole(user, "ADMIN"));
        assertTrue(rbacService.hasRole(user, "USER"));
        assertFalse(rbacService.hasRole(user, "SUPERADMIN"));
    }

    @Test
    void testTokenRevocation() {
        String tokenId = "token-uuid-1234";
        assertFalse(revocationService.isRevoked(tokenId));
        assertTrue(revocationService.revokeToken(tokenId));
        assertTrue(revocationService.isRevoked(tokenId));
    }

    @Test
    void testRefreshTokenRotation() {
        refreshTokenVault.issueToken("u1", "refresh-token-alpha");
        assertTrue(refreshTokenVault.isValid("refresh-token-alpha"));

        String nextToken = refreshTokenVault.rotateToken("refresh-token-alpha");
        assertNotNull(nextToken);
        assertTrue(refreshTokenVault.isValid(nextToken));
        assertFalse(refreshTokenVault.isValid("refresh-token-alpha"));
    }

    @Test
    void testPasswordResetToken() {
        String token = resetTokenService.generateResetToken("user@example.com");
        assertNotNull(token);
        assertTrue(resetTokenService.validateResetToken("user@example.com", token));
    }

    @Test
    void testSecretKeyRotation() {
        String key = rotationManager.resolveKeyForKeyId("order-service-key");
        assertNotNull(key);
        assertTrue(rotationManager.getKeyRingSize() >= 3);
    }

    @Test
    void testRateLimiter() {
        String ip = "192.168.1.50";
        assertTrue(rateLimiter.tryAcquire(ip));
    }

    @Test
    void testSecurityContext() {
        UserPrincipal principal = new UserPrincipal("u50", "context-user", Set.of("ROLE_OPERATOR"));
        SecurityContextHolder.setContext(principal);
        assertEquals(principal, SecurityContextHolder.getContext());
        SecurityContextHolder.clearContext();
        assertNull(SecurityContextHolder.getContext());
    }
}
