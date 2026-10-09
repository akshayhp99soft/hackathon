package com.lyxor.auth.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class TokenRevocationService {

    private final Set<String> blacklist = new HashSet<>();

    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return true;
        }
        return blacklist.contains(tokenId);
    }

    public boolean revokeToken(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        if (!blacklist.contains(tokenId)) {
            blacklist.add(tokenId);
            return true;
        }
        return false;
    }

    public int getRevokedTokenCount() {
        return blacklist.size();
    }
}
