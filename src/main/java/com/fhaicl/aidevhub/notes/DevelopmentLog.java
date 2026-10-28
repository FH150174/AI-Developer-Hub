package com.fhaicl.aidevhub.notes;

import java.util.Objects;
import java.time.LocalDate;

public record DevelopmentLog(String title, String content, LocalDate date) {
    // Existing logs have no recorded date; do not invent one when loading them.
    public DevelopmentLog(String title, String content) {
        this(title, content, null);
    }
    public DevelopmentLog {
        Objects.requireNonNull(title, "Log title must not be null");
        Objects.requireNonNull(content, "Log content must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("Log title must not be blank");
        }
    }
}
