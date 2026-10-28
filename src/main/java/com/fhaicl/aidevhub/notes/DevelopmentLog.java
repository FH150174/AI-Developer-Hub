package com.fhaicl.aidevhub.notes;

import java.util.Objects;

public record DevelopmentLog(String title, String content) {
    public DevelopmentLog {
        Objects.requireNonNull(title, "Log title must not be null");
        Objects.requireNonNull(content, "Log content must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("Log title must not be blank");
        }
    }
}
