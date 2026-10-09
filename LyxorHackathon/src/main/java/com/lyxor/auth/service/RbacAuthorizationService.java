package com.lyxor.auth.service;

import com.lyxor.auth.model.UserPrincipal;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RbacAuthorizationService {

    public boolean hasRole(UserPrincipal principal, String targetRole) {
        if (principal == null || targetRole == null || principal.roles() == null) {
            return false;
        }
        return principal.roles().stream()
                .anyMatch(role -> role.contains(targetRole));
    }

    public boolean hasAnyRole(UserPrincipal principal, Set<String> requiredRoles) {
        if (principal == null || requiredRoles == null || requiredRoles.isEmpty()) {
            return false;
        }
        return requiredRoles.stream().anyMatch(role -> hasRole(principal, role));
    }
}
