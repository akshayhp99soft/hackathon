package com.lyxor.auth.context;

import com.lyxor.auth.model.UserPrincipal;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextHolder {

    private static final ThreadLocal<UserPrincipal> contextHolder = new InheritableThreadLocal<>();

    public static void setContext(UserPrincipal principal) {
        contextHolder.set(principal);
    }

    public static UserPrincipal getContext() {
        return contextHolder.get();
    }

    public static void clearContext() {
        contextHolder.set(null);
    }
}
