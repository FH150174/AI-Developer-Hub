package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProjectIdeaOpenerTest {
    @TempDir
    Path directory;

    @Test
    void passesExecutableAndProjectPathsAsSeparateArguments() throws IOException {
        Path installation = Files.createDirectory(directory.resolve("IDEA 安装 & tools"));
        Path executable = Files.createFile(installation.resolve("idea64.exe"));
        Path project = Files.createDirectory(directory.resolve("中文 project & %PATH%"));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();

        new ProjectIdeaOpener(started::set).open(new Project("project", project.resolve(".")), executable);

        assertEquals(List.of(executable.toAbsolutePath().toString(), project.toAbsolutePath().toString()),
                started.get().command());
        assertEquals(project.toAbsolutePath().toFile(), started.get().directory());
    }

    @Test
    void acceptsIdeaExe() throws IOException {
        Path executable = Files.createFile(directory.resolve("idea.exe"));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        new ProjectIdeaOpener(started::set).open(new Project("project", directory), executable);
        assertNotNull(started.get());
    }

    @Test
    void rejectsMissingProjectAndRegularFileWithoutStarting() throws IOException {
        Path executable = Files.createFile(directory.resolve("idea64.exe"));
        ProjectIdeaOpener opener = new ProjectIdeaOpener(builder -> fail("Must not start"));

        assertThrows(IOException.class,
                () -> opener.open(new Project("missing", directory.resolve("missing")), executable));
        assertThrows(IOException.class,
                () -> opener.open(new Project("file", executable), executable));
    }

    @Test
    void rejectsMissingExecutableDirectoryAndWrongExecutableName() throws IOException {
        Path wrongExecutable = Files.createFile(directory.resolve("other.exe"));
        Project project = new Project("project", directory);
        ProjectIdeaOpener opener = new ProjectIdeaOpener(builder -> fail("Must not start"));

        assertThrows(IOException.class, () -> opener.open(project, directory.resolve("idea64.exe")));
        assertThrows(IOException.class, () -> opener.open(project, directory));
        assertThrows(IOException.class, () -> opener.open(project, wrongExecutable));
    }

    @Test
    void preservesStartupFailure() throws IOException {
        Path executable = Files.createFile(directory.resolve("idea64.exe"));
        IOException failure = new IOException("Cannot start IDEA");
        ProjectIdeaOpener opener = new ProjectIdeaOpener(builder -> { throw failure; });

        assertSame(failure, assertThrows(IOException.class,
                () -> opener.open(new Project("project", directory), executable)));
    }

    @Test
    void rejectsNullArguments() {
        ProjectIdeaOpener opener = new ProjectIdeaOpener(builder -> fail("Must not start"));
        assertThrows(NullPointerException.class, () -> opener.open(null, directory));
        assertThrows(NullPointerException.class, () -> opener.open(new Project("project", directory), null));
    }
}
