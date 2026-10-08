package com.lyxor.auth;

import com.lyxor.auth.controller.AuthenticationController;
import com.lyxor.auth.model.ApiKeyRecord;
import com.lyxor.auth.model.UserSession;
import com.lyxor.auth.service.ApiKeyVaultService;
import com.lyxor.auth.service.JwtTokenValidator;
import com.lyxor.auth.service.RbacAuthorizationService;
import com.lyxor.auth.service.SecurityContextHolderHelper;
import com.lyxor.auth.service.UserSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AuthSecurityVaultTests {

    @Test
    void testJwtTokenValidator() {
        JwtTokenValidator validator = new JwtTokenValidator("super-secret-key-12345");
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"user123\"}".getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".dummySignature";

        assertTrue(validator.validateToken(token));
    }

    @Test
    void testRbacAuthorization() {
        RbacAuthorizationService rbac = new RbacAuthorizationService();
        UserSession session = new UserSession("S1", "U1", "T1", List.of("ROLE_USER", "order:read"), 30);

        assertTrue(rbac.hasPermission(session, "order:read"));
        assertFalse(rbac.hasPermission(session, "order:delete"));
    }

    @Test
    void testSecurityContextHelper() {
        UserSession session = new UserSession("S2", "U2", "T1", List.of("ROLE_ADMIN"), 60);
        SecurityContextHolderHelper.setSession(session);
        assertEquals(session, SecurityContextHolderHelper.getSession());

        SecurityContextHolderHelper.clearContext();
        assertNull(SecurityContextHolderHelper.getSession());
    }

    @Test
    void testUserSessionManager() {
        UserSessionManager manager = new UserSessionManager();
        UserSession session = new UserSession("S3", "U3", "T1", List.of("ROLE_USER"), 15);
        manager.registerSession(session);

        assertNotNull(manager.getSession("S3"));
        assertTrue(manager.revokeSession("S3"));
    }

    @Test
    void testApiKeyVaultOperations() {
        ApiKeyVaultService vault = new ApiKeyVaultService();
        ApiKeyRecord record = new ApiKeyRecord("KEY-1", "secret-hash-99", "USR-1", "READ_WRITE");
        vault.storeKey(record);

        assertTrue(vault.validateApiKey("KEY-1", "secret-hash-99"));
        assertTrue(vault.rotateKey("KEY-1", "new-secret-hash-100"));
    }

    @Test
    void testAuthenticationControllerEndpoints() {
        AuthenticationController controller = new AuthenticationController();
        ResponseEntity<Map<String, String>> loginResponse = controller.login(Map.of("username", "admin"));
        assertEquals(200, loginResponse.getStatusCode().value());

        String refreshToken = loginResponse.getBody().get("refreshToken");
        assertNotNull(refreshToken);

        ResponseEntity<Map<String, String>> refreshResponse = controller.refresh(Map.of("refreshToken", refreshToken));
        assertEquals(200, refreshResponse.getStatusCode().value());
    }
}
