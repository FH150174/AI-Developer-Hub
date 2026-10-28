package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GitBranchReaderTest {
    @TempDir
    Path directory;

    private final GitBranchReader reader = new GitBranchReader();

    @Test
    void readsUnbornBranchWithSlashAndChineseNameFromNestedDirectory() throws Exception {
        git(directory, "init", "--initial-branch=feature/中文");
        Path nested = Files.createDirectory(directory.resolve("中文 project"));
        assertEquals(new GitBranchReader.Result(GitBranchReader.Status.BRANCH, "feature/中文"), read(nested));
        assertFalse(Files.exists(directory.resolve(".git/index")));
    }

    @Test
    void readsAgainAfterHeadChangesWithoutModifyingHead() throws Exception {
        git(directory, "init", "--initial-branch=main");
        assertEquals("main", read(directory).branchName());
        Path head = directory.resolve(".git/HEAD");
        String content = "ref: refs/heads/feature/next\n";
        Files.writeString(head, content);
        assertEquals("feature/next", read(directory).branchName());
        assertEquals(content, Files.readString(head));
    }

    @Test
    void reportsDetachedHeadWithoutTreatingHeadAsBranchName() throws Exception {
        git(directory, "init");
        // A direct HEAD fixture needs no commit or changes to the project's history.
        Path head = directory.resolve(".git/HEAD");
        String content = "0123456789012345678901234567890123456789\n";
        Files.writeString(head, content);
        assertEquals(new GitBranchReader.Result(GitBranchReader.Status.DETACHED_HEAD, ""), read(directory));
        assertEquals(content, Files.readString(head));
    }

    @Test
    void readsBranchUsingGitFile() throws Exception {
        git(directory, "init", "--initial-branch=main",
                "--separate-git-dir=" + directory.resolve("metadata"));
        assertEquals("main", read(directory).branchName());
    }

    @Test
    void readsBareRepositoryBranch() throws Exception {
        git(directory, "init", "--bare", "--initial-branch=main");
        assertEquals("main", read(directory).branchName());
    }

    @Test
    void distinguishesNonRepositoryMissingPathAndFile() throws IOException {
        assertEquals(GitBranchReader.Status.NOT_REPOSITORY, read(directory).status());
        assertEquals(GitBranchReader.Status.MISSING, read(directory.resolve("missing")).status());
        Path file = Files.writeString(directory.resolve("file"), "data");
        assertEquals(GitBranchReader.Status.NOT_DIRECTORY, read(file).status());
    }

    @Test
    void reportsBrokenRepositoryAsFailure() throws Exception {
        git(directory, "init");
        Files.writeString(directory.resolve(".git/config"), "[broken");
        assertThrows(IOException.class, () -> read(directory));
    }

    @Test
    void rejectsNullProject() {
        assertThrows(NullPointerException.class, () -> reader.read(null));
    }

    private GitBranchReader.Result read(Path path) throws IOException {
        return reader.read(new Project("project", path));
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
