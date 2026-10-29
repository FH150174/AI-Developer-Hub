package com.fhaicl.aidevhub.notes;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLogServiceTest {
    @TempDir Path directory;

    @Test
    void createsEditsAndDeletesWithPersistence() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var service = new ProjectLogService(store);
        var project = new Project("project", directory.resolve("project"));
        var first = new DevelopmentLog("title", "中文\nbody");
        var created = service.create(project, List.of(), first);
        assertEquals(List.of(first), store.load(project));
        var edited = service.edit(project, created, 0, new DevelopmentLog("renamed", "new body"));
        assertEquals("new body", store.load(project).getFirst().content());
        assertEquals(List.of(first), created);
        assertEquals(List.of(), service.delete(project, edited, 0));
        assertEquals(List.of(), new ProjectLogStore(directory.resolve("notes")).load(project));
    }

    @Test
    void editingOrDeletingDuplicateLogsTargetsSelectedIndex() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var service = new ProjectLogService(store);
        var project = new Project("project", directory.resolve("project"));
        var same = new DevelopmentLog("same", "same");
        var one = service.create(project, List.of(), same);
        var two = service.create(project, one, same);
        var edited = service.edit(project, two, 1, new DevelopmentLog("changed", ""));
        assertEquals(same, edited.getFirst());
        assertEquals("changed", edited.getLast().title());
        assertEquals(List.of(same), service.delete(project, edited, 1));
    }

    @Test
    void modificationsStayWithinSelectedProject() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var service = new ProjectLogService(store);
        var first = new Project("same", directory.resolve("first"));
        var second = new Project("same", directory.resolve("second"));
        var original = service.create(first, List.of(), new DevelopmentLog("first", "body"));
        var other = service.create(second, List.of(), new DevelopmentLog("second", "body"));
        service.delete(second, other, 0);
        assertEquals(original, store.load(first));
    }

    @Test
    void rejectsOutdatedSnapshotWithoutOverwritingExternalChanges() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var service = new ProjectLogService(store);
        var project = new Project("project", directory.resolve("project"));
        var old = service.create(project, List.of(), new DevelopmentLog("old", "body"));
        var latest = List.of(new DevelopmentLog("external", "changes"));
        store.save(project, latest);
        assertThrows(IOException.class, () -> service.create(project, old, new DevelopmentLog("new", "")));
        assertThrows(IOException.class, () -> service.edit(project, old, 0, new DevelopmentLog("new", "")));
        assertThrows(IOException.class, () -> service.delete(project, old, 0));
        assertEquals(latest, store.load(project));
    }

    @Test
    void corruptDataPreventsAnyMutation() throws IOException {
        Path storage = directory.resolve("notes");
        var store = new ProjectLogStore(storage);
        var service = new ProjectLogService(store);
        var project = new Project("project", directory.resolve("project"));
        var old = service.create(project, List.of(), new DevelopmentLog("old", "body"));
        Path file;
        try (var files = Files.list(storage)) { file = files.findFirst().orElseThrow(); }
        Files.writeString(file, "broken xml");
        assertThrows(IOException.class, () -> service.delete(project, old, 0));
        assertEquals("broken xml", Files.readString(file));
        assertEquals("old", old.getFirst().title());
    }

    @Test
    void invalidSelectionDoesNotChangeSavedLogs() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var service = new ProjectLogService(store);
        var project = new Project("project", directory.resolve("project"));
        var old = service.create(project, List.of(), new DevelopmentLog("old", "body"));
        assertThrows(IndexOutOfBoundsException.class, () -> service.delete(project, old, 1));
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentLog("\t", "body"));
        assertEquals(old, store.load(project));
    }
    @Test
    void latestUsesDateThenSavedOrderAndKeepsProjectsSeparate() throws IOException {
        var store = new ProjectLogStore(directory.resolve("notes"));
        var service = new ProjectLogService(store);
        var project = new Project("project", directory.resolve("project"));
        var other = new Project("other", directory.resolve("other"));
        assertTrue(service.latest(project).isEmpty());
        var day = java.time.LocalDate.of(2026, 10, 29);
        var latest = new DevelopmentLog("latest", "body", day);
        store.save(project, List.of(new DevelopmentLog("same day", "", day), latest,
                new DevelopmentLog("older", "", day.minusDays(1)), new DevelopmentLog("legacy", "")));
        store.save(other, List.of(new DevelopmentLog("other project", "", day.plusDays(1))));
        assertEquals(latest, service.latest(project).orElseThrow());
        var legacy = new DevelopmentLog("last legacy", "");
        store.save(project, List.of(new DevelopmentLog("first legacy", ""), legacy));
        assertEquals(legacy, service.latest(project).orElseThrow());
    }

    @Test
    void latestReportsCorruptStorage() throws IOException {
        Path storage = directory.resolve("notes");
        var store = new ProjectLogStore(storage);
        var project = new Project("project", directory.resolve("project"));
        store.save(project, List.of(new DevelopmentLog("title", "")));
        try (var files = Files.list(storage)) {
            Files.writeString(files.findFirst().orElseThrow(), "broken xml");
        }
        assertThrows(IOException.class, () -> new ProjectLogService(store).latest(project));
    }
}
