package com.lyxor.auth.service;

import com.lyxor.auth.model.AuthToken;
import com.lyxor.auth.model.UserPrincipal;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtTokenService {

    private final String hmacSecret = "enterprise-security-jwt-signing-secret-key-2026";
    private final long tokenTtlSeconds = 3600;
    private final long clockSkewSeconds = 60;

    private final Map<String, Instant> tokenExpirations = new ConcurrentHashMap<>();

    public AuthToken generateToken(UserPrincipal principal) {
        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenTtlSeconds);

        String payload = principal.userId() + ":" + principal.username() + ":" + String.join(",", principal.roles());
        String signature = computeHmacSignature(payload, hmacSecret);

        String accessToken = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + signature;

        String refreshToken = UUID.randomUUID().toString();
        tokenExpirations.put(accessToken, expiresAt);

        return new AuthToken(accessToken, refreshToken, "Bearer", tokenTtlSeconds, now);
    }

    public boolean validateToken(String token) {
        if (token == null || !token.contains(".")) {
            return false;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return false;
        }

        try {
            byte[] decodedPayloadBytes = Base64.getDecoder().decode(parts[0]);
            String payload = new String(decodedPayloadBytes, StandardCharsets.UTF_8);
            String expectedSignature = computeHmacSignature(payload, hmacSecret);

            if (!expectedSignature.equals(parts[1])) {
                return false;
            }

            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        Instant expiresAt = tokenExpirations.get(token);
        if (expiresAt == null) {
            return true;
        }
        return Instant.now().minusSeconds(clockSkewSeconds).isAfter(expiresAt);
    }

    public UserPrincipal parsePrincipal(String token) {
        if (!validateToken(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        byte[] decodedPayloadBytes = Base64.getUrlDecoder().decode(parts[0]);
        String payload = new String(decodedPayloadBytes, StandardCharsets.UTF_8);
        String[] chunks = payload.split(":");
        Set<String> roles = chunks.length > 2 ? Set.of(chunks[2].split(",")) : Set.of();
        return new UserPrincipal(chunks[0], chunks[1], roles);
    }

    private String computeHmacSignature(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
