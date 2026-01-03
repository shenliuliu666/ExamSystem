package com.examsystem.user;

import java.time.Instant;

public class UserProfile {
    private final String username;
    private final String fullName;
    private final String studentNo;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    public UserProfile(String username, String fullName, String studentNo, String createdBy, Instant createdAt, Instant updatedAt) {
        this.username = username;
        this.fullName = fullName;
        this.studentNo = studentNo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

