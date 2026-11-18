package com.fhaicl.aidevhub.project;

import com.fhaicl.aidevhub.notes.DevelopmentLog;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.todo.ProjectTodoStore;
import com.fhaicl.aidevhub.todo.TodoItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
@EnabledOnOs(OS.WINDOWS)
class ProjectHistoryPathTest {
    @TempDir Path directory;

    @Test void readdingWithDifferentCaseRecoversAndUpdatesLegacyFilesAfterRestart() throws Exception {
        Files.createDirectories(directory.resolve("Work/Demo"));
        Path old = directory.resolve("wOrK/dEmo");
        Path alias = directory.resolve("WORK/DEMO");
        assumeTrue(Files.isDirectory(old) && Files.isDirectory(alias), "Requires a case-insensitive directory");
        Path notes = directory.resolve("notes"), todos = directory.resolve("todos");
        Path logFile = legacy(notes, old, "原日志", true);
        Path todoFile = legacy(todos, old, "原待办", false);
        byte[] logBefore = Files.readAllBytes(logFile), todoBefore = Files.readAllBytes(todoFile);
        var projects = new ProjectList();
        projects.add(old);
        var previous = projects.projects().getFirst();
        projects.remove(previous);
        projects.add(alias);
        var index = new ProjectStore(directory.resolve("projects.xml"));
        index.save(List.of(), projects.projects());
        Project restored = index.load().getFirst();

        var logs = new ProjectLogStore(notes);
        var tasks = new ProjectTodoStore(todos);
        assertAll(() -> assertEquals(List.of(new DevelopmentLog("原日志", "正文")), logs.load(restored)),
                () -> assertEquals(List.of(new TodoItem("原待办")), tasks.load(restored)));
        assertArrayEquals(logBefore, Files.readAllBytes(logFile));
        assertArrayEquals(todoBefore, Files.readAllBytes(todoFile));
        logs.save(restored, List.of(new DevelopmentLog("改后", "正文")));
        tasks.save(restored, List.of(new TodoItem("改后", true)));
        assertEquals("改后", new ProjectLogStore(notes).load(previous).getFirst().title());
        assertTrue(new ProjectTodoStore(todos).load(previous).getFirst().completed());
        try (var n = Files.list(notes); var t = Files.list(todos)) {
            assertEquals(List.of(logFile), n.toList());
            assertEquals(List.of(todoFile), t.toList());
        }
    }

    @Test void ambiguousHistoriesAreReportedEvenForExactSpellingAndNeitherFileIsChanged() throws Exception {
        Path actual = Files.createDirectory(directory.resolve("Demo"));
        Project project = new Project("p", directory.resolve("DEmo"));
        assumeTrue(Files.isDirectory(project.path()), "Requires a case-insensitive directory");
        for (boolean log : List.of(true, false)) {
            Path storage = directory.resolve(log ? "notes" : "todos");
            Path first = legacy(storage, project.path(), "first", log);
            Path second = legacy(storage, directory.resolve("deMO"), "second", log);
            byte[] one = Files.readAllBytes(first), two = Files.readAllBytes(second);
            var notes = new ProjectLogStore(storage);
            var todos = new ProjectTodoStore(storage);
            for (Project selected : List.of(project, new Project("p", actual))) {
                var read = assertThrows(IOException.class, () -> {
                    if (log) notes.load(selected); else todos.load(selected);
                });
                assertTrue(read.getMessage().contains("冲突"), read::getMessage);
                assertTrue(read.getMessage().contains(first.toString()) && read.getMessage().contains(second.toString()));
                assertThrows(IOException.class, () -> {
                    if (log) notes.save(selected, List.of()); else todos.save(selected, List.of());
                });
            }
            assertArrayEquals(one, Files.readAllBytes(first));
            assertArrayEquals(two, Files.readAllBytes(second));
        }
    }

    @Test void newDataUsesDiskSpellingWithoutCombiningDifferentProjects() throws Exception {
        Path actual = Files.createDirectories(directory.resolve("One/Demo"));
        Path alias = actual.resolveSibling("DEMO");
        boolean sameDirectory = Files.isDirectory(alias);
        if (!sameDirectory) Files.createDirectory(alias);
        var a = new Project("Demo", actual);
        var b = new Project("Demo", alias);
        var elsewhere = new Project("Demo", Files.createDirectories(directory.resolve("Two/Demo")));
        var notes = new ProjectLogStore(directory.resolve("notes"));
        var todos = new ProjectTodoStore(directory.resolve("todos"));
        var logs = List.of(new DevelopmentLog("first", "content"));
        var tasks = List.of(new TodoItem("first"));

        notes.save(b, logs);
        todos.save(b, tasks);
        assertEquals(sameDirectory ? logs : List.of(), notes.load(a));
        assertEquals(sameDirectory ? tasks : List.of(), todos.load(a));
        assertTrue(notes.load(elsewhere).isEmpty());
        assertTrue(todos.load(elsewhere).isEmpty());
        if (sameDirectory) {
            // An offline read by the actual spelling also proves new files didn't hash the typed alias.
            Files.delete(actual);
            assertEquals(logs, notes.load(a));
            assertEquals(tasks, todos.load(a));
        }
    }

    @Test void offlineLegacyHistoryRemainsReadableButAnUnverifiableAliasCannotForkIt() throws Exception {
        Path old = directory.resolve("offline");
        Path notes = directory.resolve("notes");
        Path file = legacy(notes, old, "历史", true);
        byte[] saved = Files.readAllBytes(file);
        var store = new ProjectLogStore(notes);
        var alias = new Project("p", directory.resolve("OFFLINE"));
        assertEquals("历史", store.load(new Project("p", old)).getFirst().title());
        assertThrows(IOException.class, () -> store.save(alias, List.of()));
        assertArrayEquals(saved, Files.readAllBytes(file));
        try (var all = Files.list(notes)) { assertEquals(List.of(file), all.toList()); }
    }

    // Construct the v1.0 fixture independently of the current stores and path resolver.
    private Path legacy(Path storage, Path project, String title, boolean log) throws Exception {
        String key = project.toAbsolutePath().normalize().toString();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        Path file = Files.createDirectories(storage).resolve(HexFormat.of().formatHex(hash) + ".xml");
        Properties data = new Properties();
        data.setProperty("project.path", key);
        data.setProperty("count", "1");
        data.setProperty("0.title", title);
        if (log) data.setProperty("0.content", "正文");
        try (var output = Files.newOutputStream(file)) { data.storeToXML(output, "v1.0 fixture"); }
        return file;
    }
}
