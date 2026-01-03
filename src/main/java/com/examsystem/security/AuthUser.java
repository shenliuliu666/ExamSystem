package com.examsystem.security;

import java.util.Set;

public class AuthUser {
    private final String username;
    private final String passwordHash;
    private final Set<Role> roles;

    public AuthUser(String username, String passwordHash, Set<Role> roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}

