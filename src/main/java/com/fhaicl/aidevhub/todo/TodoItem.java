package com.fhaicl.aidevhub.todo;

import java.util.Objects;

public record TodoItem(String title) {
    public TodoItem {
        Objects.requireNonNull(title, "TODO title must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("TODO title must not be blank");
        }
    }
}
