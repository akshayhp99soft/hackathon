package com.lyxor.auth.model;

import java.util.Objects;
import java.util.Set;

public class UserPrincipal {
    private final String userId;
    private final String username;
    private final String passwordHash;
    private final Set<String> roles;

    public UserPrincipal(String userId, String username, String passwordHash, Set<String> roles) {
        this.userId = Objects.requireNonNull(userId);
        this.username = Objects.requireNonNull(username);
        this.passwordHash = passwordHash != null ? passwordHash : "";
        this.roles = roles != null ? Set.copyOf(roles) : Set.of();
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
