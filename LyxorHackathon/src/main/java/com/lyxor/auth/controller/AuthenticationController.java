package com.lyxor.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final Map<String, String> activeRefreshTokens = new ConcurrentHashMap<>();

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String accessToken = "jwt-access-" + UUID.randomUUID();
        String refreshToken = "jwt-refresh-" + UUID.randomUUID();
        activeRefreshTokens.put(refreshToken, username);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || !activeRefreshTokens.containsKey(refreshToken)) {
            return ResponseEntity.status(401).build();
        }

        String username = activeRefreshTokens.get(refreshToken);
        String newAccessToken = "jwt-access-" + UUID.randomUUID();

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken
        ));
    }

    public boolean isRefreshTokenActive(String token) {
        return activeRefreshTokens.containsKey(token);
    }
}
