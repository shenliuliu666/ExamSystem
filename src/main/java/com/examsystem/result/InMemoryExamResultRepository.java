package com.examsystem.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InMemoryExamResultRepository {
    private final JdbcTemplate jdbcTemplate;

    public InMemoryExamResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ExamResult saveIfAbsent(long examId, long attemptId, ExamResultDraft draft) {
        try {
            return insertResult(examId, attemptId, draft);
        } catch (DuplicateKeyException e) {
            return findByExamAndAttempt(examId, attemptId).orElseThrow(() -> e);
        }
    }

    public Optional<ExamResult> findByExamAndAttempt(long examId, long attemptId) {
        try {
            ResultRow result = jdbcTemplate.queryForObject(
                    "SELECT id, exam_id, attempt_id, student_username, total_score, max_score, created_at "
                            + "FROM exam_results WHERE exam_id = ? AND attempt_id = ?",
                    (rs, rowNum) -> new ResultRow(
                            rs.getLong("id"),
                            rs.getLong("exam_id"),
                            rs.getLong("attempt_id"),
                            rs.getString("student_username"),
                            rs.getInt("total_score"),
                            rs.getInt("max_score"),
                            rs.getTimestamp("created_at").toInstant()
                    ),
                    examId,
                    attemptId
            );
            if (result == null) {
                return Optional.empty();
            }
            List<ExamResultItem> items = listItemsByResultId(result.id);
            return Optional.of(new ExamResult(
                    result.id,
                    result.examId,
                    result.attemptId,
                    result.studentUsername,
                    result.totalScore,
                    result.maxScore,
                    items,
                    result.createdAt
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ExamResult> listByStudent(String username) {
        List<ResultRow> results = jdbcTemplate.query(
                "SELECT id, exam_id, attempt_id, student_username, total_score, max_score, created_at "
                        + "FROM exam_results WHERE student_username = ? ORDER BY id DESC",
                (rs, rowNum) -> new ResultRow(
                        rs.getLong("id"),
                        rs.getLong("exam_id"),
                        rs.getLong("attempt_id"),
                        rs.getString("student_username"),
                        rs.getInt("total_score"),
                        rs.getInt("max_score"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                username
        );
        // Returns results without items (lightweight)
        return results.stream().map(r -> new ExamResult(
                r.id,
                r.examId,
                r.attemptId,
                r.studentUsername,
                r.totalScore,
                r.maxScore,
                List.of(), // No items for list view
                r.createdAt
        )).collect(Collectors.toList());
    }

    public List<ExamResult> listByExamId(long examId) {
        List<ResultRow> results = jdbcTemplate.query(
                "SELECT id, exam_id, attempt_id, student_username, total_score, max_score, created_at "
                        + "FROM exam_results WHERE exam_id = ? ORDER BY id DESC",
                (rs, rowNum) -> new ResultRow(
                        rs.getLong("id"),
                        rs.getLong("exam_id"),
                        rs.getLong("attempt_id"),
                        rs.getString("student_username"),
                        rs.getInt("total_score"),
                        rs.getInt("max_score"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                examId
        );
        if (results.isEmpty()) {
            return List.of();
        }

        List<Long> resultIds = results.stream().map(r -> r.id).collect(Collectors.toList());
        String placeholders = resultIds.stream().map(x -> "?").collect(Collectors.joining(","));
        List<ItemRow> itemRows = jdbcTemplate.query(
                "SELECT result_id, question_id, question_type, answer, correct_answer, max_score, earned_score, correct "
                        + "FROM exam_result_items WHERE result_id IN (" + placeholders + ")",
                (rs, rowNum) -> new ItemRow(
                        rs.getLong("result_id"),
                        rs.getLong("question_id"),
                        rs.getString("question_type"),
                        rs.getString("answer"),
                        rs.getString("correct_answer"),
                        rs.getInt("max_score"),
                        rs.getInt("earned_score"),
                        rs.getBoolean("correct")
                ),
                resultIds.toArray()
        );
        Map<Long, List<ExamResultItem>> itemsByResultId = itemRows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.resultId,
                        Collectors.mapping(
                                r -> new ExamResultItem(
                                        r.questionId,
                                        r.questionType,
                                        r.answer,
                                        r.correctAnswer,
                                        r.maxScore,
                                        r.earnedScore,
                                        r.correct
                                ),
                                Collectors.toList()
                        )
                ));

        return results.stream()
                .map(r -> new ExamResult(
                        r.id,
                        r.examId,
                        r.attemptId,
                        r.studentUsername,
                        r.totalScore,
                        r.maxScore,
                        itemsByResultId.getOrDefault(r.id, List.of()),
                        r.createdAt
                ))
                .collect(Collectors.toList());
    }

    private ExamResult insertResult(long examId, long attemptId, ExamResultDraft draft) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO exam_results(exam_id, attempt_id, student_username, total_score, max_score, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, examId);
            ps.setLong(2, attemptId);
            ps.setString(3, draft.getStudentUsername());
            ps.setInt(4, draft.getTotalScore());
            ps.setInt(5, draft.getMaxScore());
            ps.setTimestamp(6, java.sql.Timestamp.from(now));
            return ps;
        }, keyHolder);
        long resultId = keyHolder.getKey().longValue();

        List<ExamResultItem> safeItems = draft.getItems() == null ? List.of() : draft.getItems();
        if (!safeItems.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO exam_result_items(result_id, question_id, question_type, answer, correct_answer, max_score, earned_score, correct) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    safeItems,
                    safeItems.size(),
                    (ps, item) -> {
                        ps.setLong(1, resultId);
                        ps.setLong(2, item.getQuestionId());
                        ps.setString(3, item.getQuestionType());
                        ps.setString(4, item.getAnswer());
                        ps.setString(5, item.getCorrectAnswer());
                        ps.setInt(6, item.getMaxScore());
                        ps.setInt(7, item.getEarnedScore());
                        ps.setBoolean(8, item.isCorrect());
                    }
            );
        }

        return new ExamResult(
                resultId,
                examId,
                attemptId,
                draft.getStudentUsername(),
                draft.getTotalScore(),
                draft.getMaxScore(),
                safeItems,
                now
        );
    }

    private List<ExamResultItem> listItemsByResultId(long resultId) {
        return jdbcTemplate.query(
                "SELECT question_id, question_type, answer, correct_answer, max_score, earned_score, correct "
                        + "FROM exam_result_items WHERE result_id = ? ORDER BY question_id ASC",
                (rs, rowNum) -> new ExamResultItem(
                        rs.getLong("question_id"),
                        rs.getString("question_type"),
                        rs.getString("answer"),
                        rs.getString("correct_answer"),
                        rs.getInt("max_score"),
                        rs.getInt("earned_score"),
                        rs.getBoolean("correct")
                ),
                resultId
        );
    }

    private static class ResultRow {
        private final long id;
        private final long examId;
        private final long attemptId;
        private final String studentUsername;
        private final int totalScore;
        private final int maxScore;
        private final Instant createdAt;

        private ResultRow(
                long id,
                long examId,
                long attemptId,
                String studentUsername,
                int totalScore,
                int maxScore,
                Instant createdAt
        ) {
            this.id = id;
            this.examId = examId;
            this.attemptId = attemptId;
            this.studentUsername = studentUsername;
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.createdAt = createdAt;
        }
    }

    private static class ItemRow {
        private final long resultId;
        private final long questionId;
        private final String questionType;
        private final String answer;
        private final String correctAnswer;
        private final int maxScore;
        private final int earnedScore;
        private final boolean correct;

        private ItemRow(
                long resultId,
                long questionId,
                String questionType,
                String answer,
                String correctAnswer,
                int maxScore,
                int earnedScore,
                boolean correct
        ) {
            this.resultId = resultId;
            this.questionId = questionId;
            this.questionType = questionType;
            this.answer = answer;
            this.correctAnswer = correctAnswer;
            this.maxScore = maxScore;
            this.earnedScore = earnedScore;
            this.correct = correct;
        }
    }
}
