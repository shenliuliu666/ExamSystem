package com.examsystem.attempt;

import java.time.Instant;
import java.util.List;

public class ExamAttempt {
    private final long id;
    private final long examId;
    private final long paperId;
    private final String studentUsername;
    private final AttemptStatus status;
    private final List<QuestionSnapshot> questions;
    private final List<AnswerRecord> answers;
    private final Instant startedAt;
    private final Instant submittedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ExamAttempt(
            long id,
            long examId,
            long paperId,
            String studentUsername,
            AttemptStatus status,
            List<QuestionSnapshot> questions,
            List<AnswerRecord> answers,
            Instant startedAt,
            Instant submittedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.examId = examId;
        this.paperId = paperId;
        this.studentUsername = studentUsername;
        this.status = status;
        this.questions = questions;
        this.answers = answers;
        this.startedAt = startedAt;
        this.submittedAt = submittedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public long getExamId() {
        return examId;
    }

    public long getPaperId() {
        return paperId;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public List<QuestionSnapshot> getQuestions() {
        return questions;
    }

    public List<AnswerRecord> getAnswers() {
        return answers;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

