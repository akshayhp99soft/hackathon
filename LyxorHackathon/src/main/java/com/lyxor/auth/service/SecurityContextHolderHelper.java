package com.lyxor.auth.service;

import com.lyxor.auth.model.UserSession;

public class SecurityContextHolderHelper {

    private static final ThreadLocal<UserSession> CONTEXT = new ThreadLocal<>();

    public static void setSession(UserSession session) {
        CONTEXT.set(session);
    }

    public static UserSession getSession() {
        return CONTEXT.get();
    }

    public static void clearContext() {
        CONTEXT.set(null);
    }
}
