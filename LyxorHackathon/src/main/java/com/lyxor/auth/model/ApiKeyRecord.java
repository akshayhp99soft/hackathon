package com.lyxor.auth.model;

public class ApiKeyRecord {
    private final String keyId;
    private final String keySecretHash;
    private final String ownerId;
    private final String scope;
    private boolean active;

    public ApiKeyRecord(String keyId, String keySecretHash, String ownerId, String scope) {
        this.keyId = keyId;
        this.keySecretHash = keySecretHash;
        this.ownerId = ownerId;
        this.scope = scope;
        this.active = true;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getKeySecretHash() {
        return keySecretHash;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getScope() {
        return scope;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
