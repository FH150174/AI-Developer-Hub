package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProjectDirectoryOpenerTest {
    @TempDir
    Path directory;

    @Test
    void opensSelectedDirectoryWithSpacesAndChineseCharacters() throws IOException {
        Path projectPath = Files.createDirectory(directory.resolve("中文 project"));
        AtomicReference<Path> opened = new AtomicReference<>();
        ProjectDirectoryOpener opener = new ProjectDirectoryOpener(opened::set);

        opener.open(new Project("project", projectPath.resolve(".")));

        assertEquals(projectPath.toAbsolutePath().normalize(), opened.get());
    }

    @Test
    void rejectsMissingDirectoryAndRegularFileWithoutOpening() throws IOException {
        ProjectDirectoryOpener opener = new ProjectDirectoryOpener(path -> fail("Must not open invalid path"));
        Path file = Files.createFile(directory.resolve("file.txt"));

        assertThrows(IOException.class, () -> opener.open(new Project("missing", directory.resolve("missing"))));
        assertThrows(IOException.class, () -> opener.open(new Project("file", file)));
    }

    @Test
    void preservesOpeningFailure() {
        IOException failure = new IOException("File manager unavailable");
        ProjectDirectoryOpener opener = new ProjectDirectoryOpener(path -> { throw failure; });

        assertSame(failure, assertThrows(IOException.class,
                () -> opener.open(new Project("project", directory))));
    }

    @Test
    void rejectsNullProject() {
        ProjectDirectoryOpener opener = new ProjectDirectoryOpener(path -> fail("Must not open"));
        assertThrows(NullPointerException.class, () -> opener.open(null));
    }
}
