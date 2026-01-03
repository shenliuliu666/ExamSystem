package com.examsystem.result;

import java.util.List;

public class ExamResultDraft {
    private final String studentUsername;
    private final int totalScore;
    private final int maxScore;
    private final List<ExamResultItem> items;

    public ExamResultDraft(String studentUsername, int totalScore, int maxScore, List<ExamResultItem> items) {
        this.studentUsername = studentUsername;
        this.totalScore = totalScore;
        this.maxScore = maxScore;
        this.items = items;
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
}

