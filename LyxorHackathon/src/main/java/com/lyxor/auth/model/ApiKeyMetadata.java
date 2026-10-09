package com.lyxor.auth.model;

import java.time.Instant;
import java.util.Objects;

public class ApiKeyMetadata {
    private final String keyId;
    private final String clientId;
    private final Instant createdAt;

    public ApiKeyMetadata(String keyId, String clientId) {
        this.keyId = Objects.requireNonNull(keyId);
        this.clientId = Objects.requireNonNull(clientId);
        this.createdAt = Instant.now();
    }

    public String getKeyId() {
        return keyId;
    }

    public String getClientId() {
        return clientId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiKeyMetadata that = (ApiKeyMetadata) o;
        return Objects.equals(keyId, that.keyId);
    }
}
