package com.lyxor.auth;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationVaultTests {

    private UserRepository userRepository;
    private JwtTokenService jwtTokenService;
    private ApiKeyAuthenticationService apiKeyService;
    private RoleHierarchyService roleHierarchyService;
    private TokenRevocationService revocationService;
    private RefreshTokenVault refreshTokenVault;
    private AuthRateLimiter rateLimiter;
    private PasswordResetTokenService resetTokenService;
    private SecretKeyRotationManager rotationManager;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        jwtTokenService = new JwtTokenService("test-secret-key-32-chars-long-2026-auth", 3600, 60);
        apiKeyService = new ApiKeyAuthenticationService();
        roleHierarchyService = new RoleHierarchyService();
        revocationService = new TokenRevocationService();
        refreshTokenVault = new RefreshTokenVault();
        rateLimiter = new AuthRateLimiter(5, 1);
        resetTokenService = new PasswordResetTokenService();
        rotationManager = new SecretKeyRotationManager();
    }

    @Test
    void testUserAuthentication() {
        Optional<UserPrincipal> user = userRepository.findByUsername("admin");
        assertTrue(user.isPresent());
        assertTrue(userRepository.verifyPassword("admin123", user.get().getPasswordHash()));
        assertFalse(userRepository.verifyPassword("wrongpass", user.get().getPasswordHash()));
    }

    @Test
    void testTokenGenerationAndValidation() {
        UserPrincipal principal = new UserPrincipal("u100", "testuser", "hash", Set.of("ROLE_USER"));
        AuthToken token = jwtTokenService.generateToken(principal);

        assertNotNull(token);
        assertNotNull(token.accessToken());
        assertNotNull(token.refreshToken());
        assertTrue(jwtTokenService.validateToken(token.accessToken()));
    }

    @Test
    void testApiKeyAuthentication() {
        apiKeyService.registerApiKey("key-1", "client-abc", "sec-key-12345");
        Optional<ApiKeyMetadata> authenticated = apiKeyService.authenticateApiKey("sec-key-12345");
        assertTrue(authenticated.isPresent());
        assertEquals("client-abc", authenticated.get().getClientId());

        assertTrue(apiKeyService.authenticateApiKey("wrong-key").isEmpty());
    }

    @Test
    void testRoleHierarchy() {
        UserPrincipal adminUser = new UserPrincipal("u1", "admin", "hash", Set.of("ROLE_ADMIN"));
        assertTrue(roleHierarchyService.isAuthorized(adminUser, "ROLE_ADMIN"));
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
        UserPrincipal principal = new UserPrincipal("u1", "jdoe", "hash", Set.of("ROLE_USER"));
        refreshTokenVault.storeRefreshToken("refresh-token-alpha", principal);
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
        UserPrincipal principal = new UserPrincipal("u50", "context-user", "hash", Set.of("ROLE_OPERATOR"));
        SecurityContextHolder.setContext(principal);
        assertEquals(principal, SecurityContextHolder.getContext());
        SecurityContextHolder.clearContext();
        assertNull(SecurityContextHolder.getContext());
    }
}
