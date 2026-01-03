package com.examsystem.course;

import java.time.Instant;

public class Classroom {
    private final long id;
    private final String name;
    private final String inviteCode;
    private final String ownerUsername;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final int memberCount;

    public Classroom(long id, String name, String inviteCode, String ownerUsername, Instant createdAt, Instant updatedAt) {
        this(id, name, inviteCode, ownerUsername, createdAt, updatedAt, 0);
    }

    public Classroom(long id, String name, String inviteCode, String ownerUsername, Instant createdAt, Instant updatedAt, int memberCount) {
        this.id = id;
        this.name = name;
        this.inviteCode = inviteCode;
        this.ownerUsername = ownerUsername;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.memberCount = memberCount;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getMemberCount() {
        return memberCount;
    }
}
