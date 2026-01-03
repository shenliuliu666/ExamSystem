package com.examsystem.paper;

import java.time.Instant;
import java.util.List;

public class Paper {
    private final long id;
    private final String name;
    private final List<PaperItem> items;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Paper(long id, String name, List<PaperItem> items, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.items = items;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<PaperItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

