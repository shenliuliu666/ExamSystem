package com.examsystem.proctor;

import java.time.Instant;

public class ProctorEvent {
    private final long id;
    private final long examId;
    private final long attemptId;
    private final String username;
    private final String type;
    private final String payloadJson;
    private final Instant createdAt;

    public ProctorEvent(
            long id,
            long examId,
            long attemptId,
            String username,
            String type,
            String payloadJson,
            Instant createdAt
    ) {
        this.id = id;
        this.examId = examId;
        this.attemptId = attemptId;
        this.username = username;
        this.type = type;
        this.payloadJson = payloadJson;
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

    public String getUsername() {
        return username;
    }

    public String getType() {
        return type;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

