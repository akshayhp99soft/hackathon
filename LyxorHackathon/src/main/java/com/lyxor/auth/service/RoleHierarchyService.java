package com.lyxor.auth.service;

import com.lyxor.auth.model.UserPrincipal;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoleHierarchyService {

    private final Map<String, Set<String>> hierarchyMap = new ConcurrentHashMap<>();

    public RoleHierarchyService() {
        hierarchyMap.put("ROLE_ADMIN", Set.of("ROLE_MANAGER", "ROLE_OPERATOR", "ROLE_USER"));
        hierarchyMap.put("ROLE_MANAGER", Set.of("ROLE_OPERATOR", "ROLE_USER"));
        hierarchyMap.put("ROLE_OPERATOR", Set.of("ROLE_USER"));
        hierarchyMap.put("ROLE_USER", Set.of());
    }

    public boolean isAuthorized(UserPrincipal principal, String requiredRole) {
        if (principal == null || requiredRole == null || principal.getRoles() == null) {
            return false;
        }

        if (principal.getRoles().contains(requiredRole)) {
            return true;
        }

        for (String assignedRole : principal.getRoles()) {
            Set<String> subRoles = hierarchyMap.get(requiredRole);
            if (subRoles != null && subRoles.contains(assignedRole)) {
                return true;
            }
        }

        return false;
    }
}
