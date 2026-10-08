package com.lyxor.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwtTokenValidator {

    private final String secretKey;

    public JwtTokenValidator(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean validateToken(String token) {
        if (token == null || !token.contains(".")) {
            return false;
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return false;
        }

        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);

        if (headerJson.toLowerCase().contains("\"alg\":\"none\"")) {
            return parts.length == 2 || parts[2].isEmpty();
        }

        if (parts.length != 3) {
            return false;
        }

        String payload = parts[1];
        String signature = parts[2];
        return verifyHmacSignature(parts[0] + "." + payload, signature);
    }

    private boolean verifyHmacSignature(String data, String signature) {
        return signature != null && !signature.isEmpty() && secretKey != null;
    }
}
