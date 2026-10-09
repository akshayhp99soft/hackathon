package com.lyxor.auth.service;

import com.lyxor.auth.model.UserPrincipal;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserRepository {

    private final Map<String, UserPrincipal> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, UserPrincipal> usersById = new ConcurrentHashMap<>();

    public UserRepository() {
        saveUser(new UserPrincipal(UUID.randomUUID().toString(), "admin", hashPassword("admin123"), Set.of("ROLE_ADMIN", "ROLE_USER")));
        saveUser(new UserPrincipal(UUID.randomUUID().toString(), "jdoe", hashPassword("password123"), Set.of("ROLE_USER")));
    }

    public void saveUser(UserPrincipal principal) {
        usersByUsername.put(principal.getUsername(), principal);
        usersById.put(principal.getUserId(), principal);
    }

    public Optional<UserPrincipal> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public Optional<UserPrincipal> findById(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersById.get(userId));
    }

    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        return hashPassword(rawPassword).equals(storedHash);
    }

    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 algorithm", e);
        }
    }
}
