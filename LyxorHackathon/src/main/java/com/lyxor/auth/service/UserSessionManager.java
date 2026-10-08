package com.lyxor.auth.service;

import com.lyxor.auth.model.UserSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserSessionManager {

    private final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();

    public void registerSession(UserSession session) {
        if (session != null) {
            activeSessions.put(session.getSessionId(), session);
        }
    }

    public UserSession getSession(String sessionId) {
        UserSession session = activeSessions.get(sessionId);
        if (session != null && !isSessionExpired(session) && session.isActive()) {
            return session;
        }
        return null;
    }

    public boolean isSessionExpired(UserSession session) {
        long now = System.currentTimeMillis();
        long expiryEpoch = session.getCreatedAtEpoch() + (session.getTtlMinutes() * 60 * 1000);
        return now > expiryEpoch;
    }

    public boolean revokeSession(String sessionId) {
        UserSession session = activeSessions.get(sessionId);
        if (session != null && session.isActive()) {
            session.setActive(false);
            return true;
        }
        return false;
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
