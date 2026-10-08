package com.lyxor.auth.model;

import java.util.Map;

public class AuthToken {
    private final String header;
    private final String payload;
    private final String signature;
    private final Map<String, Object> claims;

    public AuthToken(String header, String payload, String signature, Map<String, Object> claims) {
        this.header = header;
        this.payload = payload;
        this.signature = signature;
        this.claims = claims;
    }

    public String getHeader() {
        return header;
    }

    public String getPayload() {
        return payload;
    }

    public String getSignature() {
        return signature;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }
}
