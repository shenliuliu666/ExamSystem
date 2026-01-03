package com.examsystem.course;

import java.time.Instant;

public class ClassMember {
    private final long id;
    private final long classId;
    private final String username;
    private final Instant joinedAt;
    private final String fullName;
    private final String studentNo;

    public ClassMember(long id, long classId, String username, Instant joinedAt) {
        this(id, classId, username, joinedAt, null, null);
    }

    public ClassMember(long id, long classId, String username, Instant joinedAt, String fullName, String studentNo) {
        this.id = id;
        this.classId = classId;
        this.username = username;
        this.joinedAt = joinedAt;
        this.fullName = fullName;
        this.studentNo = studentNo;
    }

    public long getId() {
        return id;
    }

    public long getClassId() {
        return classId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStudentNo() {
        return studentNo;
    }
}
