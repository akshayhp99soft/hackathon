package com.lyxor.auth.model;

import java.util.Set;

public record UserPrincipal(String userId, String username, Set<String> roles) {
}
