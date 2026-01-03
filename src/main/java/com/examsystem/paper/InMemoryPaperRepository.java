package com.examsystem.paper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InMemoryPaperRepository {
    private final JdbcTemplate jdbcTemplate;

    public InMemoryPaperRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Paper create(PaperDraft draft, List<PaperItem> items) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO papers(name, created_at, updated_at) VALUES (?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, draft.getName());
            ps.setTimestamp(2, java.sql.Timestamp.from(now));
            ps.setTimestamp(3, java.sql.Timestamp.from(now));
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();

        List<PaperItem> safeItems = items == null ? List.of() : items;
        batchUpsertItems(id, safeItems);
        return new Paper(id, draft.getName(), new ArrayList<>(safeItems), now, now);
    }

    public Optional<Paper> findById(long id) {
        try {
            PaperRow paper = jdbcTemplate.queryForObject(
                    "SELECT id, name, created_at, updated_at FROM papers WHERE id = ?",
                    (rs, rowNum) -> new PaperRow(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    id
            );
            if (paper == null) {
                return Optional.empty();
            }

            List<PaperItem> items = jdbcTemplate.query(
                    "SELECT question_id, order_index FROM paper_items WHERE paper_id = ? ORDER BY order_index ASC",
                    (rs, rowNum) -> new PaperItem(rs.getLong("question_id"), rs.getInt("order_index")),
                    id
            );
            return Optional.of(new Paper(paper.id, paper.name, items, paper.createdAt, paper.updatedAt));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Paper> update(long id, PaperDraft draft, List<PaperItem> items) {
        Optional<Paper> existing = findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE papers SET name = ?, updated_at = ? WHERE id = ?",
                draft.getName(),
                java.sql.Timestamp.from(now),
                id
        );

        jdbcTemplate.update("DELETE FROM paper_items WHERE paper_id = ?", id);
        List<PaperItem> safeItems = items == null ? List.of() : items;
        batchUpsertItems(id, safeItems);

        Paper prev = existing.get();
        return Optional.of(new Paper(prev.getId(), draft.getName(), new ArrayList<>(safeItems), prev.getCreatedAt(), now));
    }

    @Transactional
    public boolean delete(long id) {
        jdbcTemplate.update("DELETE FROM paper_items WHERE paper_id = ?", id);
        return jdbcTemplate.update("DELETE FROM papers WHERE id = ?", id) > 0;
    }

    public boolean isUsedByAnyExamOrAttempt(long paperId) {
        Integer examCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM exams WHERE paper_id = ?",
                Integer.class,
                paperId
        );
        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM exam_attempts WHERE paper_id = ?",
                Integer.class,
                paperId
        );
        return (examCount != null && examCount > 0) || (attemptCount != null && attemptCount > 0);
    }

    public List<Paper> list(PaperQuery query) {
        String keyword = query == null ? null : query.getKeyword();
        StringBuilder sql = new StringBuilder("SELECT id, name, created_at, updated_at FROM papers WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND name LIKE ?");
            params.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY id DESC");

        List<PaperRow> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new PaperRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                params.toArray()
        );

        return rows.stream().map(r -> {
            List<PaperItem> items = jdbcTemplate.query(
                    "SELECT question_id, order_index FROM paper_items WHERE paper_id = ? ORDER BY order_index ASC",
                    (rs, rowNum) -> new PaperItem(rs.getLong("question_id"), rs.getInt("order_index")),
                    r.id
            );
            return new Paper(r.id, r.name, items, r.createdAt, r.updatedAt);
        }).collect(Collectors.toList());
    }

    private void batchUpsertItems(long paperId, List<PaperItem> items) {
        if (items.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO paper_items(paper_id, question_id, order_index) VALUES (?, ?, ?)",
                items,
                items.size(),
                (ps, item) -> {
                    ps.setLong(1, paperId);
                    ps.setLong(2, item.getQuestionId());
                    ps.setInt(3, item.getOrderIndex());
                }
        );
    }

    private static class PaperRow {
        private final long id;
        private final String name;
        private final Instant createdAt;
        private final Instant updatedAt;

        private PaperRow(long id, String name, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
