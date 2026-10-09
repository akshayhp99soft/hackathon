package com.lyxor.auth.model;

import java.time.Instant;

public record AuthToken(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        Instant issuedAt
) {
}
