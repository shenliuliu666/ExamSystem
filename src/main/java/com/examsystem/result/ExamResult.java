package com.examsystem.result;

import java.time.Instant;
import java.util.List;

public class ExamResult {
    private final long id;
    private final long examId;
    private final long attemptId;
    private final String studentUsername;
    private final int totalScore;
    private final int maxScore;
    private final List<ExamResultItem> items;
    private final Instant createdAt;

    public ExamResult(
            long id,
            long examId,
            long attemptId,
            String studentUsername,
            int totalScore,
            int maxScore,
            List<ExamResultItem> items,
            Instant createdAt
    ) {
        this.id = id;
        this.examId = examId;
        this.attemptId = attemptId;
        this.studentUsername = studentUsername;
        this.totalScore = totalScore;
        this.maxScore = maxScore;
        this.items = items;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getExamId() {
        return examId;
    }

    public long getAttemptId() {
        return attemptId;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public List<ExamResultItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

