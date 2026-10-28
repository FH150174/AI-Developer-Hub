package com.fhaicl.aidevhub.todo;

import java.util.Objects;

public record TodoItem(String title, boolean completed, Priority priority) {
    public enum Priority {
        HIGH("高"), MEDIUM("中"), LOW("低");
        private final String label;
        Priority(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public TodoItem(String title, boolean completed) {
        this(title, completed, Priority.MEDIUM);
    }

    public TodoItem(String title) {
        this(title, false);
    }

    public TodoItem {
        Objects.requireNonNull(priority, "TODO priority must not be null");
        Objects.requireNonNull(title, "TODO title must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("TODO title must not be blank");
        }
    }
}
