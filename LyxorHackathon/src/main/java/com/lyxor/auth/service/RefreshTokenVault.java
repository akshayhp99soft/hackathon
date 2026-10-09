package com.lyxor.auth.service;

import com.lyxor.auth.model.UserPrincipal;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenVault {

    private final Map<String, UserPrincipal> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, String> tokenLineage = new ConcurrentHashMap<>();

    public void storeRefreshToken(String refreshToken, UserPrincipal principal) {
        if (refreshToken != null && principal != null) {
            activeTokens.put(refreshToken, principal);
        }
    }

    public Optional<UserPrincipal> findPrincipalByToken(String refreshToken) {
        if (refreshToken == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeTokens.get(refreshToken));
    }

    public String rotateToken(String oldRefreshToken) {
        if (oldRefreshToken == null) {
            return null;
        }

        UserPrincipal principal = activeTokens.get(oldRefreshToken);
        if (principal == null) {
            return null;
        }

        String newRefreshToken = UUID.randomUUID().toString();
        activeTokens.put(newRefreshToken, principal);
        tokenLineage.put(oldRefreshToken, newRefreshToken);
        activeTokens.remove(oldRefreshToken);

        return newRefreshToken;
    }

    public boolean isValid(String refreshToken) {
        return refreshToken != null && activeTokens.containsKey(refreshToken);
    }
}
