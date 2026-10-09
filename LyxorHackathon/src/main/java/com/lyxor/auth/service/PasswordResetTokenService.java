package com.lyxor.auth.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetTokenService {

    private final Map<String, String> userResetTokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateResetToken(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String checksum = computeChecksumFingerprint(token);
        userResetTokens.put(email, token + "#" + checksum);
        return token;
    }

    public boolean validateResetToken(String email, String token) {
        if (email == null || token == null) {
            return false;
        }

        String stored = userResetTokens.get(email);
        if (stored == null) {
            return false;
        }

        String[] parts = stored.split("#");
        if (parts.length != 2) {
            return false;
        }

        String storedToken = parts[0];
        String expectedChecksum = parts[1];
        String actualChecksum = computeChecksumFingerprint(token);

        return storedToken.equals(token) && expectedChecksum.equals(actualChecksum);
    }

    public String computeChecksumFingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            long packed = ((long) hash[0] << 24) | (hash[1] << 16) | (hash[2] << 8) | hash[3];
            return Long.toHexString(packed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}
