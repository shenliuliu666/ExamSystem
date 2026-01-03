package com.examsystem.exam;

import java.time.Instant;

public class ExamSettings {
    private final boolean autoGradeObjective;
    private final boolean requireManualReview;
    private final boolean allowPartialScore;
    private final String scoreVisibleMode;
    private final String paperReviewMode;
    private final Instant reviewAvailableAt;
    private final Integer durationMinutes;
    private final Integer attemptLimit;
    private final boolean shuffleQuestions;
    private final boolean shuffleOptions;
    private final boolean allowResume;
    private final boolean enableHeartbeat;
    private final boolean recordTabSwitch;
    private final Integer forceSubmitOnLeaveSeconds;
    // New fields
    private final boolean autoSubmitOnTimeout;
    private final boolean allowReviewPaper;
    private final String showAnswersStrategy; // NONE, AFTER_SUBMISSION, AFTER_DEADLINE
    private final boolean showScore;

    public ExamSettings() {
        this(true, false, true, "IMMEDIATE", "SHOW_CORRECT", null, null, 1, false, false, true, true, true, null, true, true, "AFTER_SUBMISSION", true);
    }

    public ExamSettings(
            boolean autoGradeObjective,
            boolean requireManualReview,
            boolean allowPartialScore,
            String scoreVisibleMode,
            String paperReviewMode,
            Instant reviewAvailableAt,
            Integer durationMinutes,
            Integer attemptLimit,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean allowResume,
            boolean enableHeartbeat,
            boolean recordTabSwitch,
            Integer forceSubmitOnLeaveSeconds,
            boolean autoSubmitOnTimeout,
            boolean allowReviewPaper,
            String showAnswersStrategy,
            boolean showScore
    ) {
        this.autoGradeObjective = autoGradeObjective;
        this.requireManualReview = requireManualReview;
        this.allowPartialScore = allowPartialScore;
        this.scoreVisibleMode = scoreVisibleMode;
        this.paperReviewMode = paperReviewMode;
        this.reviewAvailableAt = reviewAvailableAt;
        this.durationMinutes = durationMinutes;
        this.attemptLimit = attemptLimit;
        this.shuffleQuestions = shuffleQuestions;
        this.shuffleOptions = shuffleOptions;
        this.allowResume = allowResume;
        this.enableHeartbeat = enableHeartbeat;
        this.recordTabSwitch = recordTabSwitch;
        this.forceSubmitOnLeaveSeconds = forceSubmitOnLeaveSeconds;
        this.autoSubmitOnTimeout = autoSubmitOnTimeout;
        this.allowReviewPaper = allowReviewPaper;
        this.showAnswersStrategy = showAnswersStrategy;
        this.showScore = showScore;
    }

    public static ExamSettings defaultSettings() {
        return new ExamSettings(
                true,
                false,
                true,
                "IMMEDIATE",
                "SHOW_CORRECT",
                null,
                null,
                1,
                false,
                false,
                true,
                true,
                true,
                null,
                true,
                true,
                "AFTER_SUBMISSION",
                true
        );
    }

    public boolean isAutoGradeObjective() {
        return autoGradeObjective;
    }

    public boolean isRequireManualReview() {
        return requireManualReview;
    }

    public boolean isAllowPartialScore() {
        return allowPartialScore;
    }

    public String getScoreVisibleMode() {
        return scoreVisibleMode;
    }

    public String getPaperReviewMode() {
        return paperReviewMode;
    }

    public Instant getReviewAvailableAt() {
        return reviewAvailableAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Integer getAttemptLimit() {
        return attemptLimit;
    }

    public boolean isShuffleQuestions() {
        return shuffleQuestions;
    }

    public boolean isShuffleOptions() {
        return shuffleOptions;
    }

    public boolean isAllowResume() {
        return allowResume;
    }

    public boolean isEnableHeartbeat() {
        return enableHeartbeat;
    }

    public boolean isRecordTabSwitch() {
        return recordTabSwitch;
    }

    public Integer getForceSubmitOnLeaveSeconds() {
        return forceSubmitOnLeaveSeconds;
    }

    public boolean isAutoSubmitOnTimeout() {
        return autoSubmitOnTimeout;
    }

    public boolean isAllowReviewPaper() {
        return allowReviewPaper;
    }

    public String getShowAnswersStrategy() {
        return showAnswersStrategy;
    }

    public boolean isShowScore() {
        return showScore;
    }
}

