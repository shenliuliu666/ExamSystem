package com.examsystem.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserProfileRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT username, full_name, student_no, created_by, created_at, updated_at FROM user_profiles WHERE username = ?",
                (rs, rowNum) -> new UserProfile(
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("student_no"),
                        rs.getString("created_by"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                username
        ).stream().findFirst();
    }

    public UserProfile insert(String username, String studentNo, String fullName, String createdBy, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO user_profiles(username, full_name, student_no, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                username,
                fullName,
                studentNo,
                createdBy,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
        );
        return new UserProfile(username, fullName, studentNo, createdBy, now, now);
    }

    public void update(String username, String studentNo, String fullName, Instant now) {
        jdbcTemplate.update(
                "UPDATE user_profiles SET full_name = ?, student_no = ?, updated_at = ? WHERE username = ?",
                fullName,
                studentNo,
                java.sql.Timestamp.from(now),
                username
        );
    }
}

