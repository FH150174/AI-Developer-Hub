package com.fhaicl.aidevhub.todo;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class ProjectTodoServiceTest {
    @TempDir Path directory;

    @Test
    void addsCompletesReopensAndDeletesWithPersistence() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var added = service.add(project, List.of(), "中文 task");
        assertFalse(added.getFirst().completed());
        var completed = service.setCompleted(project, added, 0, true);
        assertTrue(new ProjectTodoStore(directory.resolve("todos")).load(project).getFirst().completed());
        assertFalse(added.getFirst().completed());
        var reopened = service.setCompleted(project, completed, 0, false);
        assertEquals(added, reopened);
        service.delete(project, reopened, 0);
        assertEquals(List.of(), store.load(project));
    }

    @Test
    void targetsSelectedIndexForIdenticalTitlesAndKeepsOtherProject() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var first = service.add(project, List.of(), "same");
        var both = service.add(project, first, "same");
        var other = service.add(project("other"), List.of(), "same");
        var completed = service.setCompleted(project, both, 1, true);
        assertFalse(completed.getFirst().completed());
        assertTrue(completed.getLast().completed());
        assertEquals(first, service.delete(project, completed, 1));
        assertEquals(other, store.load(project("other")));
    }

    @Test
    void staleSnapshotCannotOverwriteCurrentData() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var old = service.add(project, List.of(), "task");
        var latest = service.setCompleted(project, old, 0, true);
        assertThrows(IOException.class, () -> service.add(project, old, "other"));
        assertThrows(IOException.class, () -> service.setCompleted(project, old, 0, false));
        assertThrows(IOException.class, () -> service.delete(project, old, 0));
        assertEquals(latest, store.load(project));
    }

    @Test
    void validatesTitleAndIndexBeforeWriting() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var old = service.add(project, List.of(), "task");
        assertThrows(IllegalArgumentException.class, () -> service.add(project, old, "\t "));
        assertThrows(IndexOutOfBoundsException.class, () -> service.delete(project, old, 1));
        assertEquals(old, store.load(project));
    }

    @Test
    void loadsLegacyStatusAsIncompleteAndUpgradesWhenSaved() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var project = project("one");
        store.save(project, List.of(new TodoItem("legacy")));
        Path file = savedFile();
        Properties data = readProperties(file);
        data.remove("0.completed");
        try (var output = Files.newOutputStream(file)) { data.storeToXML(output, "legacy"); }
        var legacy = store.load(project);
        assertFalse(legacy.getFirst().completed());
        new ProjectTodoService(store).setCompleted(project, legacy, 0, true);
        assertEquals("true", readProperties(file).getProperty("0.completed"));
    }

    @Test
    void invalidStatusIsRejectedWithoutChangingFile() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var project = project("one");
        store.save(project, List.of(new TodoItem("task")));
        Path file = savedFile();
        Properties data = readProperties(file);
        data.setProperty("0.completed", "invalid");
        try (var output = Files.newOutputStream(file)) { data.storeToXML(output, "invalid"); }
        byte[] before = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> store.load(project));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void corruptedDataPreventsModification() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var old = service.add(project, List.of(), "task");
        Path file = savedFile();
        Files.writeString(file, "broken xml");
        assertThrows(IOException.class, () -> service.delete(project, old, 0));
        assertEquals("broken xml", Files.readString(file));
        assertEquals(List.of(new TodoItem("task")), old);
    }

    @Test
    void priorityPersistsAndCompletionDoesNotResetIt() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var added = service.add(project, List.of(), "task", TodoItem.Priority.HIGH);
        var completed = service.setCompleted(project, added, 0, true);
        assertEquals(TodoItem.Priority.HIGH, completed.getFirst().priority());
        service.setPriority(project, completed, 0, TodoItem.Priority.LOW);
        var restored = new ProjectTodoStore(directory.resolve("todos")).load(project).getFirst();
        assertEquals(TodoItem.Priority.LOW, restored.priority());
        assertTrue(restored.completed());
    }

    @Test
    void sortedSelectionMapsToCorrectTaskForPriorityCompletionAndDeletion() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var low = service.add(project, List.of(), "same", TodoItem.Priority.LOW);
        var both = service.add(project, low, "same", TodoItem.Priority.HIGH);
        int selected = TodoOrder.PRIORITY.indexes(both).getFirst();
        assertEquals(1, selected);
        var completed = service.setCompleted(project, both, selected, true);
        assertFalse(completed.getFirst().completed());
        assertTrue(completed.getLast().completed());
        var changed = service.setPriority(project, completed, selected, TodoItem.Priority.MEDIUM);
        assertEquals(low, service.delete(project, changed, TodoOrder.PRIORITY.indexes(changed).getFirst()));
    }

    @Test
    void legacyPriorityDefaultsToMediumAndInvalidPriorityFails() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var project = project("one");
        store.save(project, List.of(new TodoItem("legacy")));
        Path file = savedFile();
        Properties data = readProperties(file);
        data.remove("0.priority");
        try (var output = Files.newOutputStream(file)) { data.storeToXML(output, "legacy"); }
        assertEquals(TodoItem.Priority.MEDIUM, store.load(project).getFirst().priority());
        data.setProperty("0.priority", "INVALID");
        try (var output = Files.newOutputStream(file)) { data.storeToXML(output, "invalid"); }
        byte[] before = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> store.load(project));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void priorityOnlyChangeRejectsStaleSnapshot() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        var old = service.add(project, List.of(), "task");
        var current = service.setPriority(project, old, 0, TodoItem.Priority.HIGH);
        assertThrows(IOException.class, () -> service.setPriority(project, old, 0, TodoItem.Priority.LOW));
        assertEquals(current, store.load(project));
        assertThrows(NullPointerException.class, () -> new TodoItem("task", false, null));
    }

    @Test
    void incompleteCountReflectsAddCompleteReopenAndDelete() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        var project = project("one");
        assertEquals(0, service.incompleteCount(project));
        var added = service.add(project, List.of(), "first", TodoItem.Priority.HIGH);
        added = service.add(project, added, "second", TodoItem.Priority.LOW);
        assertEquals(2, service.incompleteCount(project));
        var completed = service.setCompleted(project, added, 0, true);
        assertEquals(1, service.incompleteCount(project));
        var reopened = service.setCompleted(project, completed, 0, false);
        assertEquals(2, service.incompleteCount(project));
        service.delete(project, reopened, 1);
        assertEquals(1, new ProjectTodoService(new ProjectTodoStore(directory.resolve("todos"))).incompleteCount(project));
    }

    @Test
    void countsOnlySelectedProjectIncludingSameTitleTasks() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        store.save(project("one"), List.of(new TodoItem("same"), new TodoItem("same"), new TodoItem("done", true)));
        store.save(project("other"), List.of(new TodoItem("done", true)));
        assertEquals(2, service.incompleteCount(project("one")));
        assertEquals(0, service.incompleteCount(project("other")));
    }

    @Test
    void countReadFailureDoesNotBecomeZeroOrChangeData() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var service = new ProjectTodoService(store);
        store.save(project("one"), List.of(new TodoItem("task")));
        Path file = savedFile();
        Files.writeString(file, "corrupt xml");
        assertThrows(IOException.class, () -> service.incompleteCount(project("one")));
        assertEquals("corrupt xml", Files.readString(file));
    }

    private Project project(String name) { return new Project(name, directory.resolve(name)); }
    private Path savedFile() throws IOException {
        try (var files = Files.list(directory.resolve("todos"))) { return files.findFirst().orElseThrow(); }
    }
    private Properties readProperties(Path file) throws IOException {
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) { data.loadFromXML(input); }
        return data;
    }
}
