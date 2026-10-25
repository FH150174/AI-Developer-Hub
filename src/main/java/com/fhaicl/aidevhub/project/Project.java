package com.fhaicl.aidevhub.project;

import java.nio.file.Path;
import java.util.Objects;

public record Project(String name, Path path) {

    public Project {
        Objects.requireNonNull(name, "Project name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Project name must not be blank");
        }
        Objects.requireNonNull(path, "Project path must not be null");
    }
}
