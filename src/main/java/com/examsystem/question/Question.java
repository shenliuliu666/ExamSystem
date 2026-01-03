package com.examsystem.question;

import java.time.Instant;
import java.util.List;

public class Question {
    private final long id;
    private final Long bankId;
    private final QuestionType type;
    private final String stem;
    private final List<String> options;
    private final List<String> tags;
    private final String correctAnswer;
    private final String analysis;
    private final int score;
    private final String difficulty;
    private final String knowledgePoint;
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Question(
            long id,
            Long bankId,
            QuestionType type,
            String stem,
            List<String> options,
            List<String> tags,
            String correctAnswer,
            String analysis,
            int score,
            String difficulty,
            String knowledgePoint,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.bankId = bankId;
        this.type = type;
        this.stem = stem;
        this.options = options;
        this.tags = tags;
        this.correctAnswer = correctAnswer;
        this.analysis = analysis;
        this.score = score;
        this.difficulty = difficulty;
        this.knowledgePoint = knowledgePoint;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public Long getBankId() {
        return bankId;
    }

    public QuestionType getType() {
        return type;
    }

    public String getStem() {
        return stem;
    }

    public List<String> getOptions() {
        return options;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getAnalysis() {
        return analysis;
    }

    public int getScore() {
        return score;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
