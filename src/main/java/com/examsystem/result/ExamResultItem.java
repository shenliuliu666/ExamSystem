package com.examsystem.result;

public class ExamResultItem {
    private final long questionId;
    private final String questionType;
    private final String answer;
    private final String correctAnswer;
    private final int maxScore;
    private final int earnedScore;
    private final boolean correct;

    public ExamResultItem(
            long questionId,
            String questionType,
            String answer,
            String correctAnswer,
            int maxScore,
            int earnedScore,
            boolean correct
    ) {
        this.questionId = questionId;
        this.questionType = questionType;
        this.answer = answer;
        this.correctAnswer = correctAnswer;
        this.maxScore = maxScore;
        this.earnedScore = earnedScore;
        this.correct = correct;
    }

    public long getQuestionId() {
        return questionId;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getAnswer() {
        return answer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public int getEarnedScore() {
        return earnedScore;
    }

    public boolean isCorrect() {
        return correct;
    }
}

