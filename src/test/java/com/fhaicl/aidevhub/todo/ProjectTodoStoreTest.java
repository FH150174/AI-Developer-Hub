package com.fhaicl.aidevhub.todo;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class ProjectTodoStoreTest {
    @TempDir Path directory;

    @Test
    void missingDataIsEmptyWithoutCreatingStorage() throws IOException {
        Path storage = directory.resolve("todos");
        assertEquals(List.of(), new ProjectTodoStore(storage).load(project("one")));
        assertFalse(Files.exists(storage));
    }

    @Test
    void isolatesSameNameProjectsAndRestoresChineseTitlesAndOrder() throws IOException {
        Path storage = directory.resolve("todos");
        var store = new ProjectTodoStore(storage);
        var first = new Project("same", directory.resolve("中文 project"));
        var second = new Project("same", directory.resolve("other"));
        var items = List.of(new TodoItem("中文 & <title>"), new TodoItem("second"), new TodoItem("second"));
        store.save(first, items);
        store.save(second, List.of(new TodoItem("other")));
        assertEquals(items, new ProjectTodoStore(storage).load(first));
        assertEquals("other", store.load(second).getFirst().title());
        assertFalse(Files.exists(first.path()));
        assertThrows(UnsupportedOperationException.class, () -> store.load(first).clear());
    }

    @Test
    void renamingAndNormalizedPathKeepAssociation() throws IOException {
        var store = new ProjectTodoStore(directory.resolve("todos"));
        var project = project("one");
        var items = List.of(new TodoItem("task"));
        store.save(project, items);
        assertEquals(items, store.load(new Project("renamed", project.path().resolve("child/.."))));
    }

    @Test
    void replacesListAndPersistsEmptyListWithoutTemporaryFiles() throws IOException {
        Path storage = directory.resolve("todos");
        var store = new ProjectTodoStore(storage);
        var project = project("one");
        store.save(project, List.of(new TodoItem("first")));
        store.save(project, List.of(new TodoItem("second")));
        assertEquals(List.of(new TodoItem("second")), store.load(project));
        store.save(project, List.of());
        assertEquals(List.of(), store.load(project));
        try (var files = Files.list(storage)) { assertEquals(1, files.count()); }
    }

    @Test
    void rejectsCorruptionAndWrongProjectWithoutChangingFiles() throws IOException {
        Path storage = directory.resolve("todos");
        var store = new ProjectTodoStore(storage);
        var project = project("one");
        store.save(project, List.of(new TodoItem("task")));
        Path file;
        try (var files = Files.list(storage)) { file = files.findFirst().orElseThrow(); }
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) { data.loadFromXML(input); }
        data.setProperty("project.path", project("other").path().toString());
        try (var output = Files.newOutputStream(file)) { data.storeToXML(output, "test"); }
        byte[] before = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> store.load(project));
        assertArrayEquals(before, Files.readAllBytes(file));
        Files.writeString(file, "broken xml");
        assertThrows(IOException.class, () -> store.load(project));
        assertEquals("broken xml", Files.readString(file));
    }

    @Test
    void validatesTitlesAndReportsBlockedStorage() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new TodoItem("\t "));
        assertThrows(NullPointerException.class, () -> new TodoItem(null));
        Path file = Files.writeString(directory.resolve("blocked"), "data");
        assertThrows(IOException.class, () -> new ProjectTodoStore(file).save(project("one"), List.of()));
        assertEquals("data", Files.readString(file));
    }

    private Project project(String name) {
        return new Project(name, directory.resolve(name));
    }
}
