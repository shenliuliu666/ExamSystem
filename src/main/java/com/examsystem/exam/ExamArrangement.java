package com.examsystem.exam;

import java.time.Instant;

public class ExamArrangement {
    private final long id;
    private final String name;
    private final long paperId;
    private final Long classId;
    private final Instant startAt;
    private final Instant endAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ExamArrangement(
            long id,
            String name,
            long paperId,
            Long classId,
            Instant startAt,
            Instant endAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.paperId = paperId;
        this.classId = classId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPaperId() {
        return paperId;
    }

    public Long getClassId() {
        return classId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
