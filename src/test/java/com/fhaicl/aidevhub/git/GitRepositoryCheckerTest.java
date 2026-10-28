package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GitRepositoryCheckerTest {
    @TempDir
    Path directory;

    private final GitRepositoryChecker checker = new GitRepositoryChecker();

    @Test
    void detectsOrdinaryDirectoryAndEmptyGitDirectory() throws IOException {
        assertEquals(GitRepositoryChecker.Status.NOT_REPOSITORY, check(directory));
        Files.createDirectory(directory.resolve(".git"));
        assertEquals(GitRepositoryChecker.Status.NOT_REPOSITORY, check(directory));
    }

    @Test
    void detectsRepositoryAndNestedDirectoryWithChineseAndSpaces() throws Exception {
        Path repository = Files.createDirectory(directory.resolve("中文 project"));
        git(repository, "init");
        assertEquals(GitRepositoryChecker.Status.REPOSITORY, check(repository));
        assertEquals(GitRepositoryChecker.Status.REPOSITORY,
                check(Files.createDirectory(repository.resolve("nested"))));
        assertFalse(Files.exists(repository.resolve(".git/index")));
    }

    @Test
    void detectsGitFilePointingToSeparateGitDirectory() throws Exception {
        Path repository = Files.createDirectory(directory.resolve("project"));
        git(repository, "init", "--separate-git-dir=" + directory.resolve("metadata"));
        assertTrue(Files.isRegularFile(repository.resolve(".git")));
        assertEquals(GitRepositoryChecker.Status.REPOSITORY, check(repository));
    }

    @Test
    void detectsBareRepository() throws Exception {
        git(directory, "init", "--bare");
        assertEquals(GitRepositoryChecker.Status.REPOSITORY, check(directory));
    }

    @Test
    void distinguishesMissingPathAndFile() throws IOException {
        Path missing = directory.resolve("missing");
        assertEquals(GitRepositoryChecker.Status.MISSING, check(missing));
        assertFalse(Files.exists(missing));
        Path file = Files.writeString(directory.resolve("file"), "data");
        assertEquals(GitRepositoryChecker.Status.NOT_DIRECTORY, check(file));
        assertEquals("data", Files.readString(file));
    }

    @Test
    void rejectsNullProject() {
        assertThrows(NullPointerException.class, () -> checker.check(null));
    }

    private GitRepositoryChecker.Status check(Path path) throws IOException {
        return checker.check(new Project("project", path));
    }

    private void git(Path path, String... arguments) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(java.util.List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(path.toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Git fixture setup timed out");
            assertEquals(0, process.exitValue(), "Git fixture setup failed");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
