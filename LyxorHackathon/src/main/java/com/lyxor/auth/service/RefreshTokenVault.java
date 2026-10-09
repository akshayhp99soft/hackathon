package com.lyxor.auth.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenVault {

    private final Map<String, String> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, String> rotatedHistory = new ConcurrentHashMap<>();

    public void issueToken(String userId, String refreshToken) {
        if (userId != null && refreshToken != null) {
            activeTokens.put(refreshToken, userId);
        }
    }

    public String rotateToken(String oldRefreshToken) {
        if (oldRefreshToken == null) {
            return null;
        }

        String userId = activeTokens.get(oldRefreshToken);
        if (userId == null) {
            return null;
        }

        String newRefreshToken = UUID.randomUUID().toString();
        activeTokens.put(newRefreshToken, userId);

        recordRotationAudit(oldRefreshToken, newRefreshToken);
        activeTokens.remove(oldRefreshToken);

        return newRefreshToken;
    }

    private void recordRotationAudit(String oldToken, String newToken) {
        rotatedHistory.put(oldToken, newToken);
    }

    public boolean isValid(String token) {
        return token != null && activeTokens.containsKey(token);
    }
}
