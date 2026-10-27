package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public final class ProjectPathChecker {
    public enum Status {
        DIRECTORY,
        MISSING,
        NOT_DIRECTORY
    }

    public Status check(Project project) throws IOException {
        Objects.requireNonNull(project, "Project must not be null");
        try {
            BasicFileAttributes attributes = Files.readAttributes(project.path(), BasicFileAttributes.class);
            return attributes.isDirectory() ? Status.DIRECTORY : Status.NOT_DIRECTORY;
        } catch (NoSuchFileException exception) {
            return Status.MISSING;
        } catch (NotDirectoryException exception) {
            return Status.NOT_DIRECTORY;
        }
    }
}
