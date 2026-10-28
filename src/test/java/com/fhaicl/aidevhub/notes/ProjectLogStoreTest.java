package com.fhaicl.aidevhub.notes;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLogStoreTest {
    @TempDir Path directory;

    private Project project(String name) {
        return new Project(name, directory.resolve(name));
    }

    @Test
    void missingLogsAreEmptyWithoutCreatingFiles() throws IOException {
        Path storage = directory.resolve("notes");
        assertEquals(List.of(), new ProjectLogStore(storage).load(project("project")));
        assertFalse(Files.exists(storage));
    }

    @Test
    void isolatesProjectsWithSameDisplayNameAndSurvivesReload() throws IOException {
        Path storage = directory.resolve("notes");
        var store = new ProjectLogStore(storage);
        var first = new Project("same", directory.resolve("中文 project"));
        var second = new Project("same", directory.resolve("other"));
        var logs = List.of(new DevelopmentLog("中文 title", "line 1\n中文 & <xml>\n"));
        store.save(first, logs);
        store.save(second, List.of(new DevelopmentLog("other", "data")));
        assertEquals(logs, new ProjectLogStore(storage).load(first));
        assertEquals("other", store.load(second).getFirst().title());
        assertThrows(UnsupportedOperationException.class, () -> store.load(first).clear());
        assertFalse(Files.exists(first.path()));
    }

    @Test
    void renameAndNormalizedPathKeepAssociation() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var first = project("project");
        var logs = List.of(new DevelopmentLog("title", "content"));
        store.save(first, logs);
        assertEquals(logs, store.load(new Project("renamed", first.path().resolve("child/.."))));
    }

    @Test
    void supportsEmptyAndMultipleLogsAndSafeReplacement() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var project = project("project");
        store.save(project, List.of(new DevelopmentLog("one", ""), new DevelopmentLog("two", "body")));
        assertEquals(2, store.load(project).size());
        store.save(project, List.of());
        assertEquals(List.of(), store.load(project));
        try (var files = Files.list(directory.resolve("notes"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void corruptFileAndMismatchedProjectAreReportedWithoutChangingData() throws IOException {
        Path storage = directory.resolve("notes");
        var store = new ProjectLogStore(storage);
        var first = project("one");
        var second = project("two");
        store.save(first, List.of(new DevelopmentLog("one", "content")));
        Path firstFile;
        try (var files = Files.list(storage)) { firstFile = files.findFirst().orElseThrow(); }
        byte[] original = Files.readAllBytes(firstFile);
        store.save(second, List.of());
        Path secondFile;
        try (var files = Files.list(storage)) {
            secondFile = files.filter(path -> !path.equals(firstFile)).findFirst().orElseThrow();
        }
        Files.copy(firstFile, secondFile, StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IOException.class, () -> store.load(second));
        assertArrayEquals(original, Files.readAllBytes(firstFile));
        Files.writeString(firstFile, "broken xml");
        assertThrows(IOException.class, () -> store.load(first));
        assertEquals("broken xml", Files.readString(firstFile));
    }

    @Test
    void validatesLogFieldsAndReportsUnwritableStorage() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentLog(" ", "content"));
        assertThrows(NullPointerException.class, () -> new DevelopmentLog("title", null));
        Path file = Files.writeString(directory.resolve("blocked"), "data");
        var store = new ProjectLogStore(file);
        assertThrows(IOException.class, () -> store.save(project("project"), List.of()));
        assertEquals("data", Files.readString(file));
    }
    @Test
    void persistsDatesIncludingLeapDayAndAllowsEditingDate() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var project = project("dated");
        var date = java.time.LocalDate.of(2024, 2, 29);
        var created = List.of(new DevelopmentLog("title", "body", date));
        store.save(project, created);
        assertEquals(date, new ProjectLogStore(directory.resolve("notes")).load(project).getFirst().date());
        var next = java.time.LocalDate.of(2025, 1, 1);
        var service = new ProjectLogService(store);
        service.edit(project, created, 0, new DevelopmentLog("title", "body", next));
        assertEquals(next, store.load(project).getFirst().date());
    }

    @Test
    void legacyUndatedLogsSurviveMixedSaveWithoutInventedDate() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var project = project("legacy");
        store.save(project, List.of(new DevelopmentLog("legacy", "old content")));
        var legacy = store.load(project);
        assertNull(legacy.getFirst().date());
        var service = new ProjectLogService(store);
        service.create(project, legacy, new DevelopmentLog("new", "body", java.time.LocalDate.of(2026, 10, 28)));
        assertNull(store.load(project).getFirst().date());
        assertEquals("old content", store.load(project).getFirst().content());
        assertEquals(java.time.LocalDate.of(2026, 10, 28), store.load(project).getLast().date());
    }

    @Test
    void malformedStoredDateFailsWithoutChangingFile() throws IOException {
        Path storage = directory.resolve("notes");
        var store = new ProjectLogStore(storage);
        var project = project("dated");
        store.save(project, List.of(new DevelopmentLog("title", "body", java.time.LocalDate.of(2024, 2, 29))));
        Path file;
        try (var files = Files.list(storage)) { file = files.findFirst().orElseThrow(); }
        String malformed = Files.readString(file).replace("2024-02-29", "2025-02-29");
        Files.writeString(file, malformed);
        assertThrows(IOException.class, () -> store.load(project));
        assertEquals(malformed, Files.readString(file));
    }

    @Test
    void detectsDateOnlyExternalChangeAsConflict() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var project = project("dated");
        var original = List.of(new DevelopmentLog("title", "body", java.time.LocalDate.of(2024, 1, 1)));
        var changed = List.of(new DevelopmentLog("title", "body", java.time.LocalDate.of(2024, 1, 2)));
        store.save(project, original);
        store.save(project, changed);
        assertThrows(IOException.class, () -> new ProjectLogService(store).delete(project, original, 0));
        assertEquals(changed, store.load(project));
    }
}
