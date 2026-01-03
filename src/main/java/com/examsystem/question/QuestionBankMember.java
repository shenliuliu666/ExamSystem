package com.examsystem.question;

import java.time.Instant;

public class QuestionBankMember {
    private final long id;
    private final long bankId;
    private final String username;
    private final String role;
    private final Instant joinedAt;

    public QuestionBankMember(long id, long bankId, String username, String role, Instant joinedAt) {
        this.id = id;
        this.bankId = bankId;
        this.username = username;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public long getId() {
        return id;
    }

    public long getBankId() {
        return bankId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}

