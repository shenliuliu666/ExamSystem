package com.examsystem.security;

import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final InMemoryUserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;

    public AuthService(InMemoryUserStore userStore, PasswordEncoder passwordEncoder, AuthTokenService tokenService) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public Optional<LoginResult> login(String username, String password) {
        return userStore.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> new LoginResult(
                        user.getUsername(),
                        tokenService.issueToken(user.getUsername(), user.getRoles()),
                        user.getRoles()
                ));
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }

    public Optional<LoginResult> registerStudent(String username, String password) {
        String u = username == null ? "" : username.trim();
        String p = password == null ? "" : password;
        if (u.isEmpty() || p.isEmpty()) {
            throw new IllegalArgumentException("username/password required");
        }
        boolean created = userStore.createUser(u, p, Set.of(Role.STUDENT));
        if (!created) {
            return Optional.empty();
        }
        return userStore.findByUsername(u).map(user -> new LoginResult(
                user.getUsername(),
                tokenService.issueToken(user.getUsername(), user.getRoles()),
                user.getRoles()
        ));
    }

    public static class LoginResult {
        private final String username;
        private final String token;
        private final java.util.Set<Role> roles;

        public LoginResult(String username, String token, java.util.Set<Role> roles) {
            this.username = username;
            this.token = token;
            this.roles = roles;
        }

        public String getUsername() {
            return username;
        }

        public String getToken() {
            return token;
        }

        public java.util.Set<Role> getRoles() {
            return roles;
        }
    }
}
