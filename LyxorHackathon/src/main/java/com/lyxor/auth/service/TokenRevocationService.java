package com.lyxor.auth.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenRevocationService {

    private final Set<String> revokedTokenRegistry = ConcurrentHashMap.newKeySet();

    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return true;
        }
        return revokedTokenRegistry.contains(tokenId);
    }

    public boolean revokeToken(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        if (!revokedTokenRegistry.contains(tokenId)) {
            revokedTokenRegistry.add(tokenId);
            return true;
        }
        return false;
    }

    public int getRevokedTokenCount() {
        return revokedTokenRegistry.size();
    }
}
