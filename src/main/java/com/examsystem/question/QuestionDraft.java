package com.examsystem.question;

import java.util.Collections;
import java.util.List;

public class QuestionDraft {
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

    public QuestionDraft(
            QuestionType type,
            String stem,
            List<String> options,
            String correctAnswer,
            String analysis,
            int score,
            String difficulty,
            String knowledgePoint,
            boolean enabled
    ) {
        this(null, type, stem, options, correctAnswer, analysis, score, difficulty, knowledgePoint, Collections.emptyList(), enabled);
    }

    public QuestionDraft(
            Long bankId,
            QuestionType type,
            String stem,
            List<String> options,
            String correctAnswer,
            String analysis,
            int score,
            String difficulty,
            String knowledgePoint,
            List<String> tags,
            boolean enabled
    ) {
        this.bankId = bankId;
        this.type = type;
        this.stem = stem;
        this.options = options == null ? Collections.emptyList() : options;
        this.correctAnswer = correctAnswer;
        this.analysis = analysis;
        this.score = score;
        this.difficulty = difficulty;
        this.knowledgePoint = knowledgePoint;
        this.tags = tags == null ? Collections.emptyList() : tags;
        this.enabled = enabled;
    }

    public QuestionType getType() {
        return type;
    }

    public Long getBankId() {
        return bankId;
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
}
