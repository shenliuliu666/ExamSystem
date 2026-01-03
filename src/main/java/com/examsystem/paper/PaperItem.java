package com.examsystem.paper;

public class PaperItem {
    private final long questionId;
    private final int orderIndex;

    public PaperItem(long questionId, int orderIndex) {
        this.questionId = questionId;
        this.orderIndex = orderIndex;
    }

    public long getQuestionId() {
        return questionId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }
}

