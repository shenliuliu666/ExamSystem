package com.examsystem.attempt;

public class AnswerRecord {
    private final long questionId;
    private final String answer;

    public AnswerRecord(long questionId, String answer) {
        this.questionId = questionId;
        this.answer = answer;
    }

    public long getQuestionId() {
        return questionId;
    }

    public String getAnswer() {
        return answer;
    }
}

