package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GitWorkingTreeReaderTest {
    @TempDir
    Path directory;
    private final GitWorkingTreeReader reader = new GitWorkingTreeReader();

    @Test
    void emptyRepositoryIsCleanWithoutCreatingIndex() throws Exception {
        git(directory, "init");
        assertEquals(GitWorkingTreeReader.Status.CLEAN, read(directory));
        assertFalse(Files.exists(directory.resolve(".git/index")));
    }

    @Test
    void detectsUntrackedChineseAndSpacePathAndRefreshes() throws Exception {
        git(directory, "init");
        Path file = Files.writeString(directory.resolve("中文 file.txt"), "content");
        assertEquals(GitWorkingTreeReader.Status.DIRTY, read(directory));
        assertEquals("content", Files.readString(file));
        assertEquals(java.util.List.of(new GitWorkingTreeReader.FileChange("中文 file.txt", "", "??")),
                reader.readDetails(new Project("project", directory)).files());
        Files.delete(file);
        assertEquals(GitWorkingTreeReader.Status.CLEAN, read(directory));
    }

    @Test
    void ignoredFilesDoNotMakeRepositoryDirty() throws Exception {
        git(directory, "init");
        Files.writeString(directory.resolve(".git/info/exclude"), "ignored.txt\n");
        Files.writeString(directory.resolve("ignored.txt"), "ignored");
        assertEquals(GitWorkingTreeReader.Status.CLEAN, read(directory));
    }

    @Test
    void includesStagedModifiedAndDeletedFilesWithoutChangingIndex() throws Exception {
        git(directory, "init");
        Path file = Files.writeString(directory.resolve("tracked.txt"), "");
        // Build only a disposable test index, without add or commit commands.
        git(directory, "hash-object", "-w", "tracked.txt");
        git(directory, "update-index", "--add", "--cacheinfo",
                "100644,e69de29bb2d1d6434b8b29ae775ad8c2e48c5391,tracked.txt");
        Path index = directory.resolve(".git/index");
        byte[] before = Files.readAllBytes(index);
        assertEquals(GitWorkingTreeReader.Status.DIRTY, read(directory));
        Files.writeString(file, "changed");
        assertEquals("Modified", reader.readDetails(new Project("project", directory)).files().getFirst().category());
        assertEquals(GitWorkingTreeReader.Status.DIRTY, read(directory));
        Files.delete(file);
        assertEquals("Deleted", reader.readDetails(new Project("project", directory)).files().getFirst().category());
        assertEquals(GitWorkingTreeReader.Status.DIRTY, read(directory));
        assertArrayEquals(before, Files.readAllBytes(index));
        assertFalse(Files.exists(directory.resolve(".git/index.lock")));
    }

    @Test
    void checksWholeRepositoryWhenProjectIsNestedDirectory() throws Exception {
        git(directory, "init");
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Files.writeString(directory.resolve("outside.txt"), "content");
        assertEquals(GitWorkingTreeReader.Status.DIRTY, read(nested));
    }

    @Test
    void bareRepositoryHasNoWorkingTree() throws Exception {
        git(directory, "init", "--bare");
        assertEquals(GitWorkingTreeReader.Status.NO_WORKING_TREE, read(directory));
    }

    @Test
    void distinguishesInvalidProjectPaths() throws IOException {
        assertEquals(GitWorkingTreeReader.Status.NOT_REPOSITORY, read(directory));
        assertEquals(GitWorkingTreeReader.Status.MISSING, read(directory.resolve("missing")));
        Path file = Files.writeString(directory.resolve("file"), "data");
        assertEquals(GitWorkingTreeReader.Status.NOT_DIRECTORY, read(file));
    }

    @Test
    void supportsSeparateGitDirectory() throws Exception {
        Path repository = Files.createDirectory(directory.resolve("project"));
        git(repository, "init", "--separate-git-dir=" + directory.resolve("metadata"));
        assertEquals(GitWorkingTreeReader.Status.CLEAN, read(repository));
        Files.writeString(repository.resolve("file"), "data");
        assertEquals(GitWorkingTreeReader.Status.DIRTY, read(repository));
    }

    @Test
    void reportsCorruptIndexAsErrorInsteadOfClean() throws Exception {
        git(directory, "init");
        Files.writeString(directory.resolve(".git/index"), "invalid index");
        assertThrows(IOException.class, () -> read(directory));
    }

    @Test
    void rejectsNullProject() {
        assertThrows(NullPointerException.class, () -> reader.read(null));
    }

    @Test
    void parsesStagedAndUnstagedChangesWithoutLosingLeadingSpaces() throws Exception {
        var files = GitWorkingTreeReader.parse(" M modified.txt\0M  staged.txt\0MM both.txt\0 D deleted.txt\0D  staged-deleted.txt\0??  中文 file.txt\0");
        assertEquals(java.util.List.of("Modified", "Modified", "Modified", "Deleted", "Deleted", "Untracked"),
                files.stream().map(GitWorkingTreeReader.FileChange::category).toList());
        assertEquals(" M", files.getFirst().code());
        assertEquals(" 中文 file.txt", files.getLast().path());
    }

    @Test
    void preservesSpecialPathsAndConsumesRenameSourceRecord() throws Exception {
        var files = GitWorkingTreeReader.parse("R  new name.txt\0old name.txt\0?? tab\tline\nfile.txt\0");
        assertEquals(2, files.size());
        assertEquals(new GitWorkingTreeReader.FileChange("new name.txt", "old name.txt", "R "), files.getFirst());
        assertEquals("Other", files.getFirst().category());
        assertEquals("tab\tline\nfile.txt", files.getLast().path());
    }

    @Test
    void doesNotMisclassifyAddedOrConflictedFilesAndPreservesMixedChanges() throws Exception {
        var files = GitWorkingTreeReader.parse("A  added\0UU conflict\0DD both-deleted\0MD mixed\0");
        assertEquals(java.util.List.of("Other", "Other (Conflict)", "Other (Conflict)", "Modified / Deleted"),
                files.stream().map(GitWorkingTreeReader.FileChange::category).toList());
        assertThrows(UnsupportedOperationException.class, () -> files.clear());
    }

    @Test
    void rejectsTruncatedStatusAndRenameRecords() {
        assertThrows(IOException.class, () -> GitWorkingTreeReader.parse(" M file"));
        assertThrows(IOException.class, () -> GitWorkingTreeReader.parse("R  new\0"));
        assertThrows(IOException.class, () -> GitWorkingTreeReader.parse("?? \0"));
    }

    @Test
    void preservesSpacesInActualGitOutput() throws Exception {
        git(directory, "init");
        Files.writeString(directory.resolve(" leading 中文.txt"), "data");
        var result = reader.readDetails(new Project("project", directory));
        assertEquals(" leading 中文.txt", result.files().getFirst().path());
        assertEquals("Untracked", result.files().getFirst().category());
    }

    private GitWorkingTreeReader.Status read(Path path) throws IOException {
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
