package com.examsystem.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InMemoryUserStore {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public InMemoryUserStore(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        ensureDemoUser("student", "student123", Set.of(Role.STUDENT));
        ensureDemoUser("teacher", "teacher123", Set.of(Role.TEACHER));
        ensureDemoUser("admin", "admin123", Set.of(Role.ADMIN));
    }

    public boolean exists(String username) {
        String u = username == null ? "" : username.trim();
        if (u.isEmpty()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE username = ?",
                Integer.class,
                u
        );
        return count != null && count > 0;
    }

    public Optional<AuthUser> findByUsername(String username) {
        try {
            String passwordHash = jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM users WHERE username = ? AND enabled = TRUE",
                    String.class,
                    username
            );
            if (passwordHash == null) {
                return Optional.empty();
            }
            Set<Role> roles = jdbcTemplate.query(
                    "SELECT role FROM user_roles WHERE username = ?",
                    (rs, rowNum) -> Role.valueOf(rs.getString("role")),
                    username
            ).stream().collect(Collectors.toSet());
            return Optional.of(new AuthUser(username, passwordHash, roles));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean createUser(String username, String rawPassword, Set<Role> roles) {
        String u = username == null ? "" : username.trim();
        String p = rawPassword == null ? "" : rawPassword;
        if (u.isEmpty() || p.isEmpty()) {
            throw new IllegalArgumentException("username/password required");
        }
        Set<Role> r = (roles == null || roles.isEmpty()) ? Set.of(Role.STUDENT) : roles;
        if (exists(u)) {
            return false;
        }
        try {
            java.time.Instant now = java.time.Instant.now();
            jdbcTemplate.update(
                    "INSERT INTO users(username, password_hash, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    u,
                    passwordEncoder.encode(p),
                    true,
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now)
            );
            jdbcTemplate.batchUpdate(
                    "INSERT INTO user_roles(username, role) VALUES (?, ?)",
                    r.stream().map(role -> new Object[] { u, role.name() }).collect(Collectors.toList())
            );
            return true;
        } catch (DataAccessException e) {
            throw e;
        }
    }

    public void updatePassword(String username, String rawPassword) {
        String u = username == null ? "" : username.trim();
        String p = rawPassword == null ? "" : rawPassword;
        if (u.isEmpty() || p.isEmpty()) {
            throw new IllegalArgumentException("username/password required");
        }
        java.time.Instant now = java.time.Instant.now();
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, updated_at = ? WHERE username = ?",
                passwordEncoder.encode(p),
                java.sql.Timestamp.from(now),
                u
        );
    }

    private void ensureDemoUser(String username, String rawPassword, Set<Role> roles) {
        createUser(username, rawPassword, roles);
    }
}
