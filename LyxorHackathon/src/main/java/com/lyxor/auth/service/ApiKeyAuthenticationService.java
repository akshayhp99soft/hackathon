package com.lyxor.auth.service;

import com.lyxor.auth.model.ApiKeyMetadata;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiKeyAuthenticationService {

    private final Map<String, ApiKeyMetadata> apiKeyRegistry = new ConcurrentHashMap<>();

    public void registerApiKey(String keyId, String clientId, String apiKey) {
        if (keyId != null && clientId != null && apiKey != null) {
            apiKeyRegistry.put(apiKey, new ApiKeyMetadata(keyId, clientId));
        }
    }

    public Optional<ApiKeyMetadata> authenticateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(apiKeyRegistry.get(rawApiKey));
    }

    public boolean compareApiKeys(String providedKey, String expectedKey) {
        if (providedKey == null || expectedKey == null) {
            return false;
        }
        if (providedKey.length() != expectedKey.length()) {
            return false;
        }
        for (int i = 0; i < providedKey.length(); i++) {
            if (providedKey.charAt(i) != expectedKey.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
