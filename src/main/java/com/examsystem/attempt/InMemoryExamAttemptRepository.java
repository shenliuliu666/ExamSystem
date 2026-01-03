package com.examsystem.attempt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InMemoryExamAttemptRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InMemoryExamAttemptRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExamAttempt create(long examId, long paperId, String studentUsername, List<QuestionSnapshot> questions) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO exam_attempts(exam_id, paper_id, student_username, status, started_at, submitted_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, examId);
            ps.setLong(2, paperId);
            ps.setString(3, studentUsername);
            ps.setString(4, AttemptStatus.IN_PROGRESS.name());
            ps.setTimestamp(5, java.sql.Timestamp.from(now));
            ps.setTimestamp(6, null);
            ps.setTimestamp(7, java.sql.Timestamp.from(now));
            ps.setTimestamp(8, java.sql.Timestamp.from(now));
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();

        List<QuestionSnapshot> safeQuestions = questions == null ? List.of() : questions;
        if (!safeQuestions.isEmpty()) {
            List<QuestionSnapshotWithOrder> rows = new ArrayList<>();
            for (int i = 0; i < safeQuestions.size(); i++) {
                rows.add(new QuestionSnapshotWithOrder(safeQuestions.get(i), i + 1));
            }
            jdbcTemplate.batchUpdate(
                    "INSERT INTO exam_attempt_questions(attempt_id, question_id, question_type, stem, options_json, score, order_index) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    rows,
                    rows.size(),
                    (ps, row) -> {
                        QuestionSnapshot q = row.snapshot;
                        ps.setLong(1, id);
                        ps.setLong(2, q.getId());
                        ps.setString(3, q.getType());
                        ps.setString(4, q.getStem());
                        ps.setString(5, writeOptionsJson(q.getOptions()));
                        ps.setInt(6, q.getScore());
                        ps.setInt(7, row.orderIndex);
                    }
            );
        }

        ExamAttempt attempt = new ExamAttempt(
                id,
                examId,
                paperId,
                studentUsername,
                AttemptStatus.IN_PROGRESS,
                new ArrayList<>(safeQuestions),
                List.of(),
                now,
                null,
                now,
                now
        );
        return attempt;
    }

    public Optional<ExamAttempt> findById(long id) {
        try {
            AttemptRow attempt = jdbcTemplate.queryForObject(
                    "SELECT id, exam_id, paper_id, student_username, status, started_at, submitted_at, created_at, updated_at FROM exam_attempts WHERE id = ?",
                    (rs, rowNum) -> new AttemptRow(
                            rs.getLong("id"),
                            rs.getLong("exam_id"),
                            rs.getLong("paper_id"),
                            rs.getString("student_username"),
                            AttemptStatus.valueOf(rs.getString("status")),
                            rs.getTimestamp("started_at").toInstant(),
                            rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    id
            );
            if (attempt == null) {
                return Optional.empty();
            }

            List<QuestionSnapshot> questions = jdbcTemplate.query(
                    "SELECT question_id, question_type, stem, options_json, score FROM exam_attempt_questions WHERE attempt_id = ? ORDER BY order_index ASC",
                    (rs, rowNum) -> new QuestionSnapshot(
                            rs.getLong("question_id"),
                            rs.getString("question_type"),
                            rs.getString("stem"),
                            readOptionsJson(rs.getString("options_json")),
                            rs.getInt("score")
                    ),
                    id
            );

            List<AnswerRecord> answers = jdbcTemplate.query(
                    "SELECT question_id, answer FROM exam_attempt_answers WHERE attempt_id = ? ORDER BY question_id ASC",
                    (rs, rowNum) -> new AnswerRecord(rs.getLong("question_id"), rs.getString("answer")),
                    id
            );

            return Optional.of(new ExamAttempt(
                    attempt.id,
                    attempt.examId,
                    attempt.paperId,
                    attempt.studentUsername,
                    attempt.status,
                    questions,
                    answers,
                    attempt.startedAt,
                    attempt.submittedAt,
                    attempt.createdAt,
                    attempt.updatedAt
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ExamAttempt> findActiveAttempt(long examId, String studentUsername) {
        try {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM exam_attempts WHERE exam_id = ? AND student_username = ? AND status = ? ORDER BY id DESC LIMIT 1",
                    Long.class,
                    examId,
                    studentUsername,
                    AttemptStatus.IN_PROGRESS.name()
            );
            if (id == null) {
                return Optional.empty();
            }
            return findById(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<ExamAttempt> submit(long attemptId, List<AnswerRecord> answers) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE exam_attempts SET status = ?, submitted_at = ?, updated_at = ? WHERE id = ?",
                AttemptStatus.SUBMITTED.name(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                attemptId
        );
        if (updated == 0) {
            return Optional.empty();
        }

        jdbcTemplate.update("DELETE FROM exam_attempt_answers WHERE attempt_id = ?", attemptId);
        List<AnswerRecord> safeAnswers = answers == null ? List.of() : answers;
        if (!safeAnswers.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO exam_attempt_answers(attempt_id, question_id, answer) VALUES (?, ?, ?)",
                    safeAnswers,
                    safeAnswers.size(),
                    (ps, a) -> {
                        ps.setLong(1, attemptId);
                        ps.setLong(2, a.getQuestionId());
                        ps.setString(3, a.getAnswer());
                    }
            );
        }
        return findById(attemptId);
    }

    @Transactional
    public Optional<ExamAttempt> submitIfInProgress(long attemptId, List<AnswerRecord> answers) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE exam_attempts SET status = ?, submitted_at = ?, updated_at = ? WHERE id = ? AND status = ?",
                AttemptStatus.SUBMITTED.name(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                attemptId,
                AttemptStatus.IN_PROGRESS.name()
        );
        if (updated == 0) {
            return Optional.empty();
        }
        jdbcTemplate.update("DELETE FROM exam_attempt_answers WHERE attempt_id = ?", attemptId);
        List<AnswerRecord> safeAnswers = answers == null ? List.of() : answers;
        if (!safeAnswers.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO exam_attempt_answers(attempt_id, question_id, answer) VALUES (?, ?, ?)",
                    safeAnswers,
                    safeAnswers.size(),
                    (ps, a) -> {
                        ps.setLong(1, attemptId);
                        ps.setLong(2, a.getQuestionId());
                        ps.setString(3, a.getAnswer());
                    }
            );
        }
        return findById(attemptId).filter(a -> a.getStatus() == AttemptStatus.SUBMITTED);
    }

    @Transactional
    public Optional<ExamAttempt> autoSubmitIfInProgress(long attemptId) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE exam_attempts SET status = ?, submitted_at = ?, updated_at = ? WHERE id = ? AND status = ?",
                AttemptStatus.AUTO_SUBMITTED.name(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                attemptId,
                AttemptStatus.IN_PROGRESS.name()
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(attemptId).filter(a -> a.getStatus() == AttemptStatus.AUTO_SUBMITTED);
    }

    @Transactional
    public Optional<ExamAttempt> reopenIfSubmitted(long attemptId) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE exam_attempts SET status = ?, submitted_at = NULL, updated_at = ? WHERE id = ? AND status IN (?, ?)",
                AttemptStatus.IN_PROGRESS.name(),
                java.sql.Timestamp.from(now),
                attemptId,
                AttemptStatus.SUBMITTED.name(),
                AttemptStatus.AUTO_SUBMITTED.name()
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(attemptId).filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS);
    }

    public List<ExamAttempt> listByExamId(long examId) {
        List<AttemptRow> rows = jdbcTemplate.query(
                "SELECT id, exam_id, paper_id, student_username, status, started_at, submitted_at, created_at, updated_at "
                        + "FROM exam_attempts WHERE exam_id = ? ORDER BY id DESC",
                (rs, rowNum) -> new AttemptRow(
                        rs.getLong("id"),
                        rs.getLong("exam_id"),
                        rs.getLong("paper_id"),
                        rs.getString("student_username"),
                        AttemptStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                examId
        );
        return rows.stream().map(r -> new ExamAttempt(
                r.id,
                r.examId,
                r.paperId,
                r.studentUsername,
                r.status,
                List.of(),
                List.of(),
                r.startedAt,
                r.submittedAt,
                r.createdAt,
                r.updatedAt
        )).collect(java.util.stream.Collectors.toList());
    }

    public List<ExamAttempt> listByStudent(String username) {
        List<AttemptRow> rows = jdbcTemplate.query(
                "SELECT id, exam_id, paper_id, student_username, status, started_at, submitted_at, created_at, updated_at "
                        + "FROM exam_attempts WHERE student_username = ? ORDER BY id DESC",
                (rs, rowNum) -> new AttemptRow(
                        rs.getLong("id"),
                        rs.getLong("exam_id"),
                        rs.getLong("paper_id"),
                        rs.getString("student_username"),
                        AttemptStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                username
        );
        return rows.stream().map(r -> new ExamAttempt(
                r.id,
                r.examId,
                r.paperId,
                r.studentUsername,
                r.status,
                List.of(),
                List.of(),
                r.startedAt,
                r.submittedAt,
                r.createdAt,
                r.updatedAt
        )).collect(java.util.stream.Collectors.toList());
    }

    public List<ExamAttempt> listInProgressAttempts() {
        List<AttemptRow> rows = jdbcTemplate.query(
                "SELECT id, exam_id, paper_id, student_username, status, started_at, submitted_at, created_at, updated_at "
                        + "FROM exam_attempts WHERE status = ? ORDER BY id DESC",
                (rs, rowNum) -> new AttemptRow(
                        rs.getLong("id"),
                        rs.getLong("exam_id"),
                        rs.getLong("paper_id"),
                        rs.getString("student_username"),
                        AttemptStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                AttemptStatus.IN_PROGRESS.name()
        );
        return rows.stream().map(r -> new ExamAttempt(
                r.id,
                r.examId,
                r.paperId,
                r.studentUsername,
                r.status,
                List.of(),
                List.of(),
                r.startedAt,
                r.submittedAt,
                r.createdAt,
                r.updatedAt
        )).collect(java.util.stream.Collectors.toList());
    }

    private String writeOptionsJson(List<String> options) {
        try {
            List<String> safe = options == null ? List.of() : options;
            return objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize options", e);
        }
    }

    private List<String> readOptionsJson(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, STRING_LIST);
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize options", e);
        }
    }

    private static class AttemptRow {
        private final long id;
        private final long examId;
        private final long paperId;
        private final String studentUsername;
        private final AttemptStatus status;
        private final Instant startedAt;
        private final Instant submittedAt;
        private final Instant createdAt;
        private final Instant updatedAt;

        private AttemptRow(
                long id,
                long examId,
                long paperId,
                String studentUsername,
                AttemptStatus status,
                Instant startedAt,
                Instant submittedAt,
                Instant createdAt,
                Instant updatedAt
        ) {
            this.id = id;
            this.examId = examId;
            this.paperId = paperId;
            this.studentUsername = studentUsername;
            this.status = status;
            this.startedAt = startedAt;
            this.submittedAt = submittedAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    private static class QuestionSnapshotWithOrder {
        private final QuestionSnapshot snapshot;
        private final int orderIndex;

        private QuestionSnapshotWithOrder(QuestionSnapshot snapshot, int orderIndex) {
            this.snapshot = snapshot;
            this.orderIndex = orderIndex;
        }
    }
}
