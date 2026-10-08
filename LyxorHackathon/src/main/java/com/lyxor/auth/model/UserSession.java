package com.lyxor.auth.model;

import java.util.ArrayList;
import java.util.List;

public class UserSession {
    private final String sessionId;
    private final String userId;
    private final String tenantId;
    private final List<String> roles;
    private final long createdAtEpoch;
    private final int ttlMinutes;
    private boolean active;

    public UserSession(String sessionId, String userId, String tenantId, List<String> roles, int ttlMinutes) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
        this.createdAtEpoch = System.currentTimeMillis();
        this.ttlMinutes = ttlMinutes;
        this.active = true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public long getCreatedAtEpoch() {
        return createdAtEpoch;
    }

    public int getTtlMinutes() {
        return ttlMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
