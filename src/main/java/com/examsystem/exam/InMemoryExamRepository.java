package com.examsystem.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
public class InMemoryExamRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InMemoryExamRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ExamArrangement create(ExamDraft draft) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO exams(name, paper_id, class_id, start_at, end_at, status, settings_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, draft.getName());
            ps.setLong(2, draft.getPaperId());
            if (draft.getClassId() == null) {
                ps.setObject(3, null);
            } else {
                ps.setLong(3, draft.getClassId());
            }
            ps.setTimestamp(4, java.sql.Timestamp.from(draft.getStartAt()));
            ps.setTimestamp(5, java.sql.Timestamp.from(draft.getEndAt()));
            ps.setString(6, "PUBLISHED");
            ps.setString(7, writeSettingsJson(draft.getSettings()));
            ps.setTimestamp(8, java.sql.Timestamp.from(now));
            ps.setTimestamp(9, java.sql.Timestamp.from(now));
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        ExamArrangement exam = new ExamArrangement(
                id,
                draft.getName(),
                draft.getPaperId(),
                draft.getClassId(),
                draft.getStartAt(),
                draft.getEndAt(),
                now,
                now
        );
        return exam;
    }

    public void update(long id, ExamDraft draft) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE exams SET name = ?, paper_id = ?, class_id = ?, start_at = ?, end_at = ?, settings_json = ?, updated_at = ? WHERE id = ?",
                draft.getName(),
                draft.getPaperId(),
                draft.getClassId(),
                java.sql.Timestamp.from(draft.getStartAt()),
                java.sql.Timestamp.from(draft.getEndAt()),
                writeSettingsJson(draft.getSettings()),
                java.sql.Timestamp.from(now),
                id
        );
    }

    @Transactional
    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM attempt_heartbeats WHERE attempt_id IN (SELECT id FROM exam_attempts WHERE exam_id = ?)", id);
        jdbcTemplate.update("DELETE FROM proctor_events WHERE exam_id = ?", id);
        jdbcTemplate.update("DELETE FROM exam_result_items WHERE result_id IN (SELECT id FROM exam_results WHERE exam_id = ?)", id);
        jdbcTemplate.update("DELETE FROM exam_results WHERE exam_id = ?", id);
        jdbcTemplate.update("DELETE FROM exam_attempt_answers WHERE attempt_id IN (SELECT id FROM exam_attempts WHERE exam_id = ?)", id);
        jdbcTemplate.update("DELETE FROM exam_attempt_questions WHERE attempt_id IN (SELECT id FROM exam_attempts WHERE exam_id = ?)", id);
        jdbcTemplate.update("DELETE FROM exam_attempts WHERE exam_id = ?", id);
        jdbcTemplate.update("DELETE FROM exams WHERE id = ?", id);
    }

    public Optional<ExamArrangement> findById(long id) {
        try {
            ExamArrangement exam = jdbcTemplate.queryForObject(
                    "SELECT id, name, paper_id, class_id, start_at, end_at, created_at, updated_at FROM exams WHERE id = ?",
                    (rs, rowNum) -> new ExamArrangement(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getLong("paper_id"),
                            (Long) rs.getObject("class_id"),
                            rs.getTimestamp("start_at").toInstant(),
                            rs.getTimestamp("end_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    id
            );
            return Optional.ofNullable(exam);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ExamArrangement> listAll() {
        return jdbcTemplate.query(
                "SELECT id, name, paper_id, class_id, start_at, end_at, created_at, updated_at FROM exams ORDER BY id DESC",
                (rs, rowNum) -> new ExamArrangement(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("paper_id"),
                        (Long) rs.getObject("class_id"),
                        rs.getTimestamp("start_at").toInstant(),
                        rs.getTimestamp("end_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                )
        );
    }

    public List<ExamArrangement> listByClassIds(List<Long> classIds) {
        List<Long> safeIds = classIds == null ? List.of() : classIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (safeIds.isEmpty()) {
            return List.of();
        }
        String placeholders = safeIds.stream().map(x -> "?").collect(Collectors.joining(","));
        return jdbcTemplate.query(
                "SELECT id, name, paper_id, class_id, start_at, end_at, created_at, updated_at FROM exams WHERE class_id IN (" + placeholders + ") ORDER BY id DESC",
                (rs, rowNum) -> new ExamArrangement(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("paper_id"),
                        (Long) rs.getObject("class_id"),
                        rs.getTimestamp("start_at").toInstant(),
                        rs.getTimestamp("end_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                safeIds.toArray()
        );
    }

    public Optional<ExamSettings> findSettingsById(long id) {
        try {
            String json = jdbcTemplate.queryForObject(
                    "SELECT settings_json FROM exams WHERE id = ?",
                    String.class,
                    id
            );
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(readSettingsJson(json));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private String writeSettingsJson(ExamSettings settings) {
        try {
            ExamSettings value = settings == null ? ExamSettings.defaultSettings() : settings;
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize exam settings", e);
        }
    }

    private ExamSettings readSettingsJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return ExamSettings.defaultSettings();
            }
            return objectMapper.readValue(json, ExamSettings.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize exam settings", e);
        }
    }
}
