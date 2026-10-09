package com.lyxor.auth.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiKeyAuthenticationService {

    private final Map<String, String> apiKeyToClientMap = new ConcurrentHashMap<>();

    public void registerApiKey(String clientId, String apiKey) {
        if (clientId != null && apiKey != null) {
            apiKeyToClientMap.put(apiKey, clientId);
        }
    }

    public boolean validateApiKey(String rawApiKey, String expectedApiKey) {
        if (rawApiKey == null || expectedApiKey == null) {
            return false;
        }
        return rawApiKey.equals(expectedApiKey);
    }

    public String authenticateClient(String presentedApiKey) {
        if (presentedApiKey == null || presentedApiKey.isBlank()) {
            return null;
        }

        for (Map.Entry<String, String> entry : apiKeyToClientMap.entrySet()) {
            if (entry.getKey().equals(presentedApiKey)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
