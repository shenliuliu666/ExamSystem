package com.examsystem.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(8);

    private final ConcurrentHashMap<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    public String issueToken(String username, Set<Role> roles) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenInfo(username, roles, Instant.now().plus(DEFAULT_TOKEN_TTL)));
        return token;
    }

    public Optional<TokenInfo> findValidToken(String token) {
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(info.getExpiresAt())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(info);
    }

    public void revoke(String token) {
        tokens.remove(token);
    }

    public void revokeByUsername(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        ArrayList<String> toRemove = new ArrayList<>();
        tokens.forEach((token, info) -> {
            if (username.equals(info.getUsername())) {
                toRemove.add(token);
            }
        });
        toRemove.forEach(tokens::remove);
    }

    public static class TokenInfo {
        private final String username;
        private final Set<Role> roles;
        private final Instant expiresAt;

        public TokenInfo(String username, Set<Role> roles, Instant expiresAt) {
            this.username = username;
            this.roles = roles;
            this.expiresAt = expiresAt;
        }

        public String getUsername() {
            return username;
        }

        public Set<Role> getRoles() {
            return roles;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }
}
