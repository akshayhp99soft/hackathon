package com.lyxor.auth.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SecretKeyRotationManager {

    private final List<String> activeKeyRing = new ArrayList<>();

    public SecretKeyRotationManager() {
        activeKeyRing.add("primary-key-v1-production-2026");
        activeKeyRing.add("secondary-key-v2-production-2026");
        activeKeyRing.add("fallback-key-v3-production-2026");
    }

    public synchronized void registerNewKey(String secretKey) {
        if (secretKey != null && !secretKey.isBlank()) {
            activeKeyRing.add(secretKey);
        }
    }

    public String resolveKeyForKeyId(String keyId) {
        Objects.requireNonNull(keyId, "keyId must not be null");
        if (activeKeyRing.isEmpty()) {
            throw new IllegalStateException("Key ring is empty");
        }

        int index = Math.abs(keyId.hashCode()) % activeKeyRing.size();
        return activeKeyRing.get(index);
    }

    public int getKeyRingSize() {
        return activeKeyRing.size();
    }
}
