package com.fhaicl.aidevhub.storage;

import com.fhaicl.aidevhub.agent.AgentLauncher;
import com.fhaicl.aidevhub.agent.AgentPathSettings;
import com.fhaicl.aidevhub.notes.DevelopmentLog;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.project.ProjectStore;
import com.fhaicl.aidevhub.settings.AppSettings;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import com.fhaicl.aidevhub.todo.ProjectTodoStore;
import com.fhaicl.aidevhub.todo.TodoItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AppDataDirectoryTest {
    @TempDir Path directory;
    private Path destination() { return directory.resolve("Roaming/AI-Developer-Hub"); }
    private Path legacy() { return directory.resolve(".aidevhub"); }

    @Test void honorsAppDataAndFallsBackWhenUnset() throws IOException {
        Path roaming = directory.resolve("中文 user/Roaming");
        assertEquals(roaming.resolve("AI-Developer-Hub"), AppDataDirectory.resolve(roaming.toString(), directory));
        for (String unset : new String[]{null, "", "  "}) {
            assertEquals(directory.resolve("AppData/Roaming/AI-Developer-Hub"), AppDataDirectory.resolve(unset, directory));
        }
        assertThrows(IOException.class, () -> AppDataDirectory.resolve("relative", directory));
        assertThrows(IOException.class, () -> AppDataDirectory.resolve("bad\0path", directory));
    }

    @Test void firstLaunchCreatesEmptyRoot() throws IOException {
        assertEquals(destination(), AppDataDirectory.initialize(destination(), legacy()));
        try (var files = Files.list(destination())) { assertEquals(0, files.count()); }
        assertFalse(Files.exists(legacy()));
    }

    @Test void migratesEveryStoreWithoutChangingLegacyDataAndCanSaveAfterRestart() throws IOException {
        Project project = new Project("中文项目", directory.resolve("project"));
        var logs = List.of(new DevelopmentLog("日志", "# 内容\n测试"));
        var todos = List.of(new TodoItem("待办", true, TodoItem.Priority.HIGH));
        new ProjectStore(legacy().resolve("projects.xml")).save(List.of(project));
        new ProjectLogStore(legacy().resolve("notes")).save(project, logs);
        new ProjectTodoStore(legacy().resolve("todos")).save(project, todos);
        AppSettings settings = new AppSettings(legacy().resolve("settings.xml"));
        settings.load();
        settings.save(new AppSettings.Values(AppSettings.Terminal.CMD, directory.toString(), AppSettings.Theme.DARK, AppSettings.StartPage.Projects));
        IdePathSettings ide = new IdePathSettings(legacy().resolve("ide-paths.xml"));
        ide.load();
        Path executable = Files.createFile(directory.resolve("idea64.exe"));
        ide.save(Map.of(IdePathSettings.Ide.IDEA, executable.toString()));
        AgentPathSettings agents = new AgentPathSettings(legacy().resolve("agent-paths.xml"));
        agents.load();
        agents.save(Map.of(), Map.of(AgentLauncher.Tool.CODEX, "codex --help"));
        Files.writeString(legacy().resolve("unrelated.txt"), "leave behind");
        Files.writeString(legacy().resolve("notes/unfinished.tmp"), "leave behind");

        Path root = AppDataDirectory.initialize(destination(), legacy());
        assertEquals(List.of(project), new ProjectStore(root.resolve("projects.xml")).load());
        assertEquals(logs, new ProjectLogStore(root.resolve("notes")).load(project));
        assertEquals(todos, new ProjectTodoStore(root.resolve("todos")).load(project));
        AppSettings restored = new AppSettings(root.resolve("settings.xml"));
        restored.load();
        assertEquals(settings.values(), restored.values());
        IdePathSettings restoredIde = new IdePathSettings(root.resolve("ide-paths.xml"));
        restoredIde.load();
        assertEquals(executable, restoredIde.requirePath(IdePathSettings.Ide.IDEA));
        AgentPathSettings restoredAgents = new AgentPathSettings(root.resolve("agent-paths.xml"));
        restoredAgents.load();
        assertEquals("codex --help", restoredAgents.requireCommand(AgentLauncher.Tool.CODEX));
        try (var files = Files.walk(legacy())) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = root.resolve(legacy().relativize(source));
                if (source.toString().endsWith(".xml")) {
                    assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target));
                } else assertFalse(Files.exists(target));
            }
        }
        new ProjectTodoStore(root.resolve("todos")).save(project, List.of());
        assertEquals(root, AppDataDirectory.initialize(destination(), legacy()));
        assertEquals(List.of(), new ProjectTodoStore(root.resolve("todos")).load(project));
        assertEquals(todos, new ProjectTodoStore(legacy().resolve("todos")).load(project));
    }

    @Test void existingRootIsAuthoritativeEvenWhenEmpty() throws IOException {
        Files.createDirectories(destination());
        Files.createDirectories(legacy());
        Files.writeString(legacy().resolve("settings.xml"), "old");
        AppDataDirectory.initialize(destination(), legacy());
        assertFalse(Files.exists(destination().resolve("settings.xml")));
        Files.writeString(destination().resolve("settings.xml"), "new");
        AppDataDirectory.initialize(destination(), legacy());
        assertEquals("new", Files.readString(destination().resolve("settings.xml")));
    }

    @Test void failedMigrationDoesNotPublishPartialRootAndCanRetry() throws IOException {
        Files.createDirectories(legacy().resolve("notes"));
        Files.writeString(legacy().resolve("projects.xml"), "preserve");
        Files.createDirectory(legacy().resolve("notes/invalid.xml"));
        assertThrows(IOException.class, () -> AppDataDirectory.initialize(destination(), legacy()));
        assertFalse(Files.exists(destination()));
        assertEquals("preserve", Files.readString(legacy().resolve("projects.xml")));
        try (var siblings = Files.list(destination().getParent())) { assertEquals(0, siblings.count()); }
        Files.delete(legacy().resolve("notes/invalid.xml"));
        AppDataDirectory.initialize(destination(), legacy());
        assertEquals("preserve", Files.readString(destination().resolve("projects.xml")));
        assertThrows(IOException.class, () -> new ProjectStore(destination().resolve("projects.xml")).load());
    }

    @Test void rejectsFileInPlaceOfDataRootOrLegacyDirectory() throws IOException {
        Files.createDirectories(destination().getParent());
        Files.writeString(destination(), "blocked");
        assertThrows(IOException.class, () -> AppDataDirectory.initialize(destination(), legacy()));
        assertEquals("blocked", Files.readString(destination()));
        Path another = directory.resolve("other/data");
        Files.writeString(legacy(), "legacy blocked");
        assertThrows(IOException.class, () -> AppDataDirectory.initialize(another, legacy()));
        assertFalse(Files.exists(another));
    }
}
