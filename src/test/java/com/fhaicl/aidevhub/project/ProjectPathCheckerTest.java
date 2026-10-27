package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProjectPathCheckerTest {
    @TempDir
    Path directory;

    private final ProjectPathChecker checker = new ProjectPathChecker();

    @Test
    void recognizesDirectoryWithChineseAndSpaces() throws IOException {
        Path path = Files.createDirectory(directory.resolve("中文 project"));
        assertEquals(ProjectPathChecker.Status.DIRECTORY, checker.check(new Project("project", path)));
    }

    @Test
    void recognizesMissingPathWithoutCreatingIt() throws IOException {
        Path path = directory.resolve("missing");
        assertEquals(ProjectPathChecker.Status.MISSING, checker.check(new Project("project", path)));
        assertFalse(Files.exists(path));
    }

    @Test
    void distinguishesFileFromDirectoryWithoutChangingIt() throws IOException {
        Path path = Files.writeString(directory.resolve("project.txt"), "project data");
        assertEquals(ProjectPathChecker.Status.NOT_DIRECTORY, checker.check(new Project("project", path)));
        assertEquals("project data", Files.readString(path));
    }

    @Test
    void checksAgainAfterDirectoryIsDeletedAndRestored() throws IOException {
        Path path = Files.createDirectory(directory.resolve("project"));
        Project project = new Project("project", path);
        assertEquals(ProjectPathChecker.Status.DIRECTORY, checker.check(project));

        Files.delete(path);
        assertEquals(ProjectPathChecker.Status.MISSING, checker.check(project));

        Files.createDirectory(path);
        assertEquals(ProjectPathChecker.Status.DIRECTORY, checker.check(project));
    }

    @Test
    void recognizesFilesystemRoot() throws IOException {
        Path root = directory.toAbsolutePath().getRoot();
        assertEquals(ProjectPathChecker.Status.DIRECTORY, checker.check(new Project("root", root)));
    }

    @Test
    void rejectsNullProject() {
        assertThrows(NullPointerException.class, () -> checker.check(null));
    }
}
