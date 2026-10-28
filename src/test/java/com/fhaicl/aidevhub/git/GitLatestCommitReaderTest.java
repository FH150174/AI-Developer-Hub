package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class GitLatestCommitReaderTest {
    @TempDir
    Path directory;
    private final GitLatestCommitReader reader = new GitLatestCommitReader();

    @Test
    void emptyRepositoryHasNoCommits() throws Exception {
        git(directory, "init");
        assertEquals(GitLatestCommitReader.Status.NO_COMMITS, read(directory).status());
    }

    @Test
    void readsHeadSubjectWithChineseAndIgnoresBodyAndParent() throws Exception {
        git(directory, "init");
        String parent = commit(directory.resolve(".git"), "first", "");
        String head = commit(directory.resolve(".git"), "feat: 中文 title\n\nbody text", parent);
        byte[] before = Files.readAllBytes(directory.resolve(".git/HEAD"));
        var actual = read(directory);
        assertTrue(head.startsWith(actual.shortHash()));
        assertEquals(new GitLatestCommitReader.Result(GitLatestCommitReader.Status.COMMIT, "feat: 中文 title", "Test",
                        java.time.OffsetDateTime.parse("2023-11-14T22:13:20Z"), actual.shortHash()),
                actual);
        assertEquals(head + "\n", Files.readString(directory.resolve(".git/HEAD")));
        assertArrayEquals(before, Files.readAllBytes(directory.resolve(".git/HEAD")));
        assertFalse(Files.exists(directory.resolve(".git/index")));
        assertEquals("feat: 中文 title", read(Files.createDirectory(directory.resolve("中文 nested"))).subject());
    }

    @Test
    void supportsBareRepository() throws Exception {
        git(directory, "init", "--bare");
        commit(directory, "bare subject", "");
        assertEquals("bare subject", read(directory).subject());
    }

    @Test
    void supportsSeparateGitDirectory() throws Exception {
        Path repo = Files.createDirectory(directory.resolve("repo"));
        Path metadata = directory.resolve("metadata");
        git(repo, "init", "--separate-git-dir=" + metadata);
        commit(metadata, "separate subject", "");
        assertEquals("separate subject", read(repo).subject());
    }

    @Test
    void brokenHeadIsFailureRatherThanNoCommits() throws Exception {
        git(directory, "init");
        Files.writeString(directory.resolve(".git/HEAD"), "0123456789012345678901234567890123456789\n");
        assertThrows(GitException.class, () -> read(directory));
    }

    @Test
    void distinguishesInvalidPaths() throws IOException {
        assertEquals(GitLatestCommitReader.Status.NOT_REPOSITORY, read(directory).status());
        assertEquals(GitLatestCommitReader.Status.MISSING, read(directory.resolve("missing")).status());
        assertEquals(GitLatestCommitReader.Status.NOT_DIRECTORY,
                read(Files.writeString(directory.resolve("file"), "data")).status());
        assertThrows(NullPointerException.class, () -> reader.read(null));
    }

    @Test
    void distinguishesAuthorFromCommitterAndPreservesCommitTimeOffset() throws Exception {
        git(directory, "init");
        Path metadata = directory.resolve(".git");
        String tree = object(metadata, "tree", new byte[0]);
        String content = "tree " + tree + "\n"
                + "author 中文 Author <author@example.invalid> 1600000000 -0700\n"
                + "committer Other Person <committer@example.invalid> 1700000000 +0530\n\nsubject\n";
        String hash = object(metadata, "commit", content.getBytes(StandardCharsets.UTF_8));
        Files.writeString(metadata.resolve("HEAD"), hash + "\n");
        var result = read(directory);
        assertEquals("中文 Author", result.author());
        assertEquals(java.time.OffsetDateTime.parse("2023-11-15T03:43:20+05:30"), result.committedAt());
    }

    @Test
    void parsesEmptySubjectAndAuthorAndNegativeTimeZone() throws Exception {
        var result = GitLatestCommitReader.parse("\0\0" + "2023-11-14T15:13:20-07:00\0abcdef1\0\n");
        assertEquals("", result.subject());
        assertEquals("", result.author());
        assertEquals(java.time.ZoneOffset.ofHours(-7), result.committedAt().getOffset());
    }

    @Test
    void rejectsMissingExtraFieldsAndMalformedTime() {
        for (String output : new String[] { "subject\0", "s\0a\0invalid\0abcdef1\0\n",
                "s\0a\0" + "2023-11-14T22:13:20Z\0extra\0" }) {
            assertThrows(GitException.class, () -> GitLatestCommitReader.parse(output));
        }
    }

    @Test
    void absentCommitHasNoAuthorOrTimestamp() throws Exception {
        git(directory, "init");
        var result = read(directory);
        assertEquals("", result.author());
        assertNull(result.committedAt());
        assertEquals("", result.shortHash());
    }

    @Test
    void usesGitAbbreviationConfigurationAndUpdatesWithHead() throws Exception {
        git(directory, "init");
        Path metadata = directory.resolve(".git");
        Files.writeString(metadata.resolve("config"), "\n[core]\n    abbrev = 12\n",
                java.nio.file.StandardOpenOption.APPEND);
        String first = commit(metadata, "first", "");
        assertEquals(first.substring(0, 12), read(directory).shortHash());
        String next = commit(metadata, "next", first);
        assertEquals(next.substring(0, 12), read(directory).shortHash());
    }

    @Test
    void rejectsMissingEmptyAndNonHexadecimalHash() {
        String prefix = "subject\0author\0" + "2023-11-14T22:13:20Z\0";
        for (String suffix : new String[] { "", "\0\n", "invalid\0\n", "abc\0\n" }) {
            assertThrows(GitException.class, () -> GitLatestCommitReader.parse(prefix + suffix));
        }
    }

    // Disposable loose Git objects avoid add/commit commands and user identity configuration.
    private String commit(Path metadata, String message, String parent) throws Exception {
        String tree = object(metadata, "tree", new byte[0]);
        String content = "tree " + tree + "\n" + (parent.isEmpty() ? "" : "parent " + parent + "\n")
                + "author Test <test@example.invalid> 1700000000 +0000\n"
                + "committer Test <test@example.invalid> 1700000000 +0000\n\n" + message + "\n";
        String hash = object(metadata, "commit", content.getBytes(StandardCharsets.UTF_8));
        Files.writeString(metadata.resolve("HEAD"), hash + "\n");
        return hash;
    }

    private String object(Path metadata, String type, byte[] content) throws Exception {
        byte[] header = (type + " " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[header.length + content.length];
        System.arraycopy(header, 0, bytes, 0, header.length);
        System.arraycopy(content, 0, bytes, header.length, content.length);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        Path target = metadata.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash.substring(2));
        Files.createDirectories(target.getParent());
        try (var output = new DeflaterOutputStream(Files.newOutputStream(target))) {
            output.write(bytes);
        }
        return hash;
    }

    private GitLatestCommitReader.Result read(Path path) throws IOException {
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
