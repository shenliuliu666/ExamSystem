package com.examsystem.question;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionBankRepository {
    private final JdbcTemplate jdbcTemplate;

    public QuestionBankRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QuestionBank create(String name, String ownerUsername, String visibility) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO question_banks(name, owner_username, visibility, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, ownerUsername);
            ps.setString(3, visibility);
            ps.setTimestamp(4, java.sql.Timestamp.from(now));
            ps.setTimestamp(5, java.sql.Timestamp.from(now));
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        return new QuestionBank(id, name, ownerUsername, visibility, now, now);
    }

    public Optional<QuestionBank> findById(long id) {
        try {
            QuestionBank bank = jdbcTemplate.queryForObject(
                    "SELECT id, name, owner_username, visibility, created_at, updated_at FROM question_banks WHERE id = ?",
                    (rs, rowNum) -> new QuestionBank(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("owner_username"),
                            rs.getString("visibility"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    id
            );
            return Optional.ofNullable(bank);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<QuestionBank> listForUser(String username) {
        return jdbcTemplate.query(
                "SELECT DISTINCT b.id, b.name, b.owner_username, b.visibility, b.created_at, b.updated_at " +
                        "FROM question_banks b " +
                        "LEFT JOIN question_bank_members m ON b.id = m.bank_id " +
                        "WHERE b.owner_username = ? OR m.username = ? " +
                        "ORDER BY b.id DESC",
                (rs, rowNum) -> new QuestionBank(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("owner_username"),
                        rs.getString("visibility"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                username,
                username
        );
    }

    public List<QuestionBankMember> listMembers(long bankId) {
        return jdbcTemplate.query(
                "SELECT id, bank_id, username, role, joined_at FROM question_bank_members WHERE bank_id = ? ORDER BY id ASC",
                (rs, rowNum) -> new QuestionBankMember(
                        rs.getLong("id"),
                        rs.getLong("bank_id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getTimestamp("joined_at").toInstant()
                ),
                bankId
        );
    }

    public void addMember(long bankId, String username, String role) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO question_bank_members(bank_id, username, role, joined_at) VALUES (?, ?, ?, ?)",
                bankId,
                username,
                role,
                java.sql.Timestamp.from(now)
        );
    }

    public boolean isOwner(long bankId, String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM question_banks WHERE id = ? AND owner_username = ?",
                Integer.class,
                bankId,
                username
        );
        return count != null && count > 0;
    }

    public boolean isMember(long bankId, String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM question_bank_members WHERE bank_id = ? AND username = ?",
                Integer.class,
                bankId,
                username
        );
        return count != null && count > 0;
    }
}

