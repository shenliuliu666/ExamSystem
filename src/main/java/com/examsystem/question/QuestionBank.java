package com.examsystem.question;

import java.time.Instant;

public class QuestionBank {
    private final long id;
    private final String name;
    private final String ownerUsername;
    private final String visibility;
    private final Instant createdAt;
    private final Instant updatedAt;

    public QuestionBank(long id, String name, String ownerUsername, String visibility, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.ownerUsername = ownerUsername;
        this.visibility = visibility;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

