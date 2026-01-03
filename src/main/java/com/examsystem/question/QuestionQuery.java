package com.examsystem.question;

public class QuestionQuery {
    private final QuestionType type;
    private final Boolean enabled;
    private final String keyword;

    public QuestionQuery(QuestionType type, Boolean enabled, String keyword) {
        this(type, enabled, keyword, null);
    }

    private final Long bankId;

    public QuestionQuery(QuestionType type, Boolean enabled, String keyword, Long bankId) {
        this.type = type;
        this.enabled = enabled;
        this.keyword = keyword;
        this.bankId = bankId;
    }

    public QuestionType getType() {
        return type;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getKeyword() {
        return keyword;
    }

    public Long getBankId() {
        return bankId;
    }
}
