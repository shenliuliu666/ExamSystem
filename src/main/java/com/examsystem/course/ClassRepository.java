package com.examsystem.course;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ClassRepository {
    private final JdbcTemplate jdbcTemplate;

    public ClassRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteUsers(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }
        for (String username : usernames) {
            jdbcTemplate.update("DELETE FROM attempt_heartbeats WHERE username = ?", username);
            jdbcTemplate.update("DELETE FROM proctor_events WHERE username = ?", username);
            jdbcTemplate.update("DELETE FROM question_bank_members WHERE username = ?", username);
            jdbcTemplate.update("DELETE FROM class_members WHERE username = ?", username);
            jdbcTemplate.update("DELETE FROM users WHERE username = ?", username);
        }
    }

    public Classroom create(String name, String ownerUsername, String inviteCode) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO classes(name, invite_code, owner_username, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, inviteCode);
            ps.setString(3, ownerUsername);
            ps.setTimestamp(4, java.sql.Timestamp.from(now));
            ps.setTimestamp(5, java.sql.Timestamp.from(now));
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        return new Classroom(id, name, inviteCode, ownerUsername, now, now);
    }

    public Optional<Classroom> findById(long id) {
        try {
            Classroom c = jdbcTemplate.queryForObject(
                    "SELECT id, name, invite_code, owner_username, created_at, updated_at FROM classes WHERE id = ?",
                    (rs, rowNum) -> new Classroom(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("invite_code"),
                            rs.getString("owner_username"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    id
            );
            return Optional.ofNullable(c);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Classroom> findByInviteCode(String inviteCode) {
        try {
            Classroom c = jdbcTemplate.queryForObject(
                    "SELECT id, name, invite_code, owner_username, created_at, updated_at FROM classes WHERE invite_code = ?",
                    (rs, rowNum) -> new Classroom(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("invite_code"),
                            rs.getString("owner_username"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    inviteCode
            );
            return Optional.ofNullable(c);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Classroom> listByOwner(String ownerUsername) {
        return jdbcTemplate.query(
                "SELECT c.id, c.name, c.invite_code, c.owner_username, c.created_at, c.updated_at, " +
                        "(SELECT COUNT(1) FROM class_members m WHERE m.class_id = c.id) as member_count " +
                        "FROM classes c WHERE c.owner_username = ? ORDER BY c.id DESC",
                (rs, rowNum) -> new Classroom(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("invite_code"),
                        rs.getString("owner_username"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("member_count")
                ),
                ownerUsername
        );
    }

    public void addMember(long classId, String username) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO class_members(class_id, username, joined_at) VALUES (?, ?, ?)",
                classId, username, java.sql.Timestamp.from(now)
        );
    }

    public boolean userExists(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE username = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    public boolean isMember(long classId, String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM class_members WHERE class_id = ? AND username = ?",
                Integer.class,
                classId, username
        );
        return count != null && count > 0;
    }

    public List<ClassMember> listMembers(long classId) {
        return jdbcTemplate.query(
                "SELECT id, class_id, username, joined_at FROM class_members WHERE class_id = ?",
                (rs, rowNum) -> new ClassMember(
                        rs.getLong("id"),
                        rs.getLong("class_id"),
                        rs.getString("username"),
                        rs.getTimestamp("joined_at").toInstant()
                ),
                classId
        );
    }

    public List<Classroom> listJoinedClasses(String username) {
        return jdbcTemplate.query(
                "SELECT c.id, c.name, c.invite_code, c.owner_username, c.created_at, c.updated_at, " +
                        "(SELECT COUNT(1) FROM class_members m2 WHERE m2.class_id = c.id) as member_count " +
                        "FROM classes c JOIN class_members m ON c.id = m.class_id " +
                        "WHERE m.username = ? ORDER BY m.joined_at DESC",
                (rs, rowNum) -> new Classroom(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("invite_code"),
                        rs.getString("owner_username"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("member_count")
                ),
                username
        );
    }

    public void removeMember(long classId, String username) {
        jdbcTemplate.update(
                "DELETE FROM class_members WHERE class_id = ? AND username = ?",
                classId, username
        );
    }

    public void delete(long classId) {
        jdbcTemplate.update("DELETE FROM classes WHERE id = ?", classId);
    }

    public List<String> getMembersOnlyInClass(long classId) {
        return jdbcTemplate.queryForList(
                "SELECT m.username FROM class_members m " +
                        "WHERE m.class_id = ? " +
                        "AND NOT EXISTS (SELECT 1 FROM class_members m2 WHERE m2.username = m.username AND m2.class_id <> ?)",
                String.class,
                classId, classId
        );
    }
}
