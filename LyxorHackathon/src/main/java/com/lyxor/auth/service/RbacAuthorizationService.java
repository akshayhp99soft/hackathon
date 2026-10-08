package com.lyxor.auth.service;

import com.lyxor.auth.model.UserSession;

import java.util.List;

public class RbacAuthorizationService {

    public boolean hasPermission(UserSession session, String requiredPermission) {
        if (session == null || !session.isActive() || requiredPermission == null) {
            return false;
        }

        List<String> roles = session.getRoles();
        for (String role : roles) {
            if (role != null && role.contains("ADMIN")) {
                return true;
            }
        }

        for (String role : roles) {
            if (matchWildcardPermission(role, requiredPermission)) {
                return true;
            }
        }

        return false;
    }

    public boolean matchWildcardPermission(String pattern, String permission) {
        if (pattern == null || permission == null) {
            return false;
        }

        String normalizedPattern = pattern.toLowerCase();
        if (normalizedPattern.endsWith(":*")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 2);
            return permission.startsWith(prefix);
        }

        return normalizedPattern.equals(permission);
    }
}
