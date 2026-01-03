package com.examsystem.attempt;

import java.util.List;

public class QuestionSnapshot {
    private final long id;
    private final String type;
    private final String stem;
    private final List<String> options;
    private final int score;

    public QuestionSnapshot(long id, String type, String stem, List<String> options, int score) {
        this.id = id;
        this.type = type;
        this.stem = stem;
        this.options = options;
        this.score = score;
    }

    public long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getStem() {
        return stem;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getScore() {
        return score;
    }
}

