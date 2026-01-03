package com.examsystem.exam;

import java.time.Instant;

public class ExamDraft {
    private final String name;
    private final long paperId;
    private final Long classId;
    private final Instant startAt;
    private final Instant endAt;
    private final ExamSettings settings;

    public ExamDraft(String name, long paperId, Long classId, Instant startAt, Instant endAt, ExamSettings settings) {
        this.name = name;
        this.paperId = paperId;
        this.classId = classId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.settings = settings;
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

    public ExamSettings getSettings() {
        return settings;
    }
}
