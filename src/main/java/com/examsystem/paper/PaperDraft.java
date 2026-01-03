package com.examsystem.paper;

import java.util.Collections;
import java.util.List;

public class PaperDraft {
    private final String name;
    private final List<Long> questionIds;

    public PaperDraft(String name, List<Long> questionIds) {
        this.name = name;
        this.questionIds = questionIds == null ? Collections.emptyList() : questionIds;
    }

    public String getName() {
        return name;
    }

    public List<Long> getQuestionIds() {
        return questionIds;
    }
}

