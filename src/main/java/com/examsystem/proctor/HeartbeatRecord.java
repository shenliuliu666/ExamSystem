package com.examsystem.proctor;

import java.time.Instant;

public class HeartbeatRecord {
    private final long id;
    private final long attemptId;
    private final String username;
    private final Instant ts;
    private final Instant createdAt;

    public HeartbeatRecord(long id, long attemptId, String username, Instant ts, Instant createdAt) {
        this.id = id;
        this.attemptId = attemptId;
        this.username = username;
        this.ts = ts;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getAttemptId() {
        return attemptId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getTs() {
        return ts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

