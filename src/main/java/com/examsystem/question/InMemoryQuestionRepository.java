package com.examsystem.question;

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
public class InMemoryQuestionRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() {};
    private static final String PAPER_ONLY_TAG = "__paper_only__";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InMemoryQuestionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Question create(QuestionDraft draft) {
        Instant now = Instant.now();
        List<String> options = draft.getOptions() == null ? List.of() : draft.getOptions();
        String optionsJson = writeOptionsJson(draft.getOptions());
        List<String> tags = draft.getTags() == null ? List.of() : draft.getTags();
        String tagsJson = writeOptionsJson(draft.getTags());
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO questions(bank_id, type, stem, options_json, tags_json, correct_answer, analysis, score, difficulty, knowledge_point, enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            if (draft.getBankId() == null) {
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, draft.getBankId());
            }
            ps.setString(2, draft.getType().name());
            ps.setString(3, draft.getStem());
            ps.setString(4, optionsJson);
            ps.setString(5, tagsJson);
            ps.setString(6, draft.getCorrectAnswer());
            ps.setString(7, draft.getAnalysis());
            ps.setInt(8, draft.getScore());
            ps.setString(9, draft.getDifficulty());
            ps.setString(10, draft.getKnowledgePoint());
            ps.setBoolean(11, draft.isEnabled());
            ps.setTimestamp(12, java.sql.Timestamp.from(now));
            ps.setTimestamp(13, java.sql.Timestamp.from(now));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return new Question(
                id,
                draft.getBankId(),
                draft.getType(),
                draft.getStem(),
                new ArrayList<>(options),
                new ArrayList<>(tags),
                draft.getCorrectAnswer(),
                draft.getAnalysis(),
                draft.getScore(),
                draft.getDifficulty(),
                draft.getKnowledgePoint(),
                draft.isEnabled(),
                now,
                now
        );
    }

    public Optional<Question> findById(long id) {
        try {
            Question found = jdbcTemplate.queryForObject(
                    "SELECT * FROM questions WHERE id = ?",
                    (rs, rowNum) -> mapQuestion(rs),
                    id
            );
            return Optional.ofNullable(found);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Question> update(long id, QuestionDraft draft) {
        Optional<Question> existing = findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        List<String> options = draft.getOptions() == null ? List.of() : draft.getOptions();
        String optionsJson = writeOptionsJson(draft.getOptions());
        List<String> tags = draft.getTags() == null ? List.of() : draft.getTags();
        String tagsJson = writeOptionsJson(draft.getTags());
        jdbcTemplate.update(
                "UPDATE questions SET bank_id=?, type=?, stem=?, options_json=?, tags_json=?, correct_answer=?, analysis=?, score=?, difficulty=?, knowledge_point=?, enabled=?, updated_at=? WHERE id=?",
                draft.getBankId(),
                draft.getType().name(),
                draft.getStem(),
                optionsJson,
                tagsJson,
                draft.getCorrectAnswer(),
                draft.getAnalysis(),
                draft.getScore(),
                draft.getDifficulty(),
                draft.getKnowledgePoint(),
                draft.isEnabled(),
                java.sql.Timestamp.from(now),
                id
        );

        Question prev = existing.get();
        return Optional.of(new Question(
                prev.getId(),
                draft.getBankId(),
                draft.getType(),
                draft.getStem(),
                new ArrayList<>(options),
                new ArrayList<>(tags),
                draft.getCorrectAnswer(),
                draft.getAnalysis(),
                draft.getScore(),
                draft.getDifficulty(),
                draft.getKnowledgePoint(),
                draft.isEnabled(),
                prev.getCreatedAt(),
                now
        ));
    }

    @Transactional
    public boolean delete(long id) {
        jdbcTemplate.update("DELETE FROM paper_items WHERE question_id = ?", id);
        return jdbcTemplate.update("DELETE FROM questions WHERE id = ?", id) > 0;
    }

    public List<Question> list(QuestionQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM questions WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        sql.append(" AND (tags_json IS NULL OR tags_json NOT LIKE ?)");
        params.add("%\"" + PAPER_ONLY_TAG + "\"%");

        if (query != null && query.getType() != null) {
            sql.append(" AND type = ?");
            params.add(query.getType().name());
        }
        if (query != null && query.getEnabled() != null) {
            sql.append(" AND enabled = ?");
            params.add(query.getEnabled());
        }
        if (query != null && query.getBankId() != null) {
            sql.append(" AND bank_id = ?");
            params.add(query.getBankId());
        }
        if (query != null) {
            String keyword = query.getKeyword();
            if (keyword != null && !keyword.isBlank()) {
                sql.append(" AND (stem LIKE ? OR knowledge_point LIKE ?)");
                String like = "%" + keyword + "%";
                params.add(like);
                params.add(like);
            }
        }

        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapQuestion(rs), params.toArray());
    }

    private Question mapQuestion(java.sql.ResultSet rs) throws java.sql.SQLException {
        long id = rs.getLong("id");
        Long bankId = (Long) rs.getObject("bank_id");
        QuestionType type = QuestionType.valueOf(rs.getString("type"));
        String stem = rs.getString("stem");
        String optionsJson = rs.getString("options_json");
        List<String> options = readOptionsJson(optionsJson);
        String tagsJson = rs.getString("tags_json");
        List<String> tags = readOptionsJson(tagsJson);
        String correctAnswer = rs.getString("correct_answer");
        String analysis = rs.getString("analysis");
        int score = rs.getInt("score");
        String difficulty = rs.getString("difficulty");
        String knowledgePoint = rs.getString("knowledge_point");
        boolean enabled = rs.getBoolean("enabled");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return new Question(
                id,
                bankId,
                type,
                stem,
                options,
                tags,
                correctAnswer,
                analysis,
                score,
                difficulty,
                knowledgePoint,
                enabled,
                createdAt,
                updatedAt
        );
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
}
