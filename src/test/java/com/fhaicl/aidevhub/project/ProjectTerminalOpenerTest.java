package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTerminalOpenerTest {
    @TempDir
    Path directory;

    @Test
    void passesSpecialCharacterPathAsWorkingDirectoryRatherThanCommand() throws IOException {
        Path projectPath = Files.createDirectory(directory.resolve("中文 project & test %PATH%"));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        ProjectTerminalOpener opener = new ProjectTerminalOpener("Windows 11", started::set);

        opener.open(new Project("project", projectPath.resolve(".")));

        assertEquals(projectPath.toAbsolutePath().normalize().toFile(), started.get().directory());
        assertEquals(List.of("cmd.exe", "/d", "/c", "start", "\"\"", "cmd.exe", "/d", "/k"),
                started.get().command());
    }

    @Test
    void rejectsMissingDirectoryAndRegularFileWithoutStartingProcess() throws IOException {
        ProjectTerminalOpener opener = new ProjectTerminalOpener("Windows 11", builder -> fail("Must not start"));
        Path file = Files.createFile(directory.resolve("file.txt"));

        assertThrows(IOException.class, () -> opener.open(new Project("missing", directory.resolve("missing"))));
        assertThrows(IOException.class, () -> opener.open(new Project("file", file)));
    }

    @Test
    void reportsUnsupportedPlatformWithoutStartingProcess() {
        ProjectTerminalOpener opener = new ProjectTerminalOpener("Linux", builder -> fail("Must not start"));
        IOException failure = assertThrows(IOException.class,
                () -> opener.open(new Project("project", directory)));
        assertTrue(failure.getMessage().contains("Windows"));
    }

    @Test
    void preservesProcessStartupFailure() {
        IOException failure = new IOException("Terminal unavailable");
        ProjectTerminalOpener opener = new ProjectTerminalOpener("Windows 11", builder -> { throw failure; });

        assertSame(failure, assertThrows(IOException.class,
                () -> opener.open(new Project("project", directory))));
    }

    @Test
    void rejectsNullProject() {
        ProjectTerminalOpener opener = new ProjectTerminalOpener("Windows 11", builder -> fail("Must not start"));
        assertThrows(NullPointerException.class, () -> opener.open(null));
    }
}
