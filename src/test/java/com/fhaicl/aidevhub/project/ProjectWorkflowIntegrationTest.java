package com.fhaicl.aidevhub.project;

import com.fhaicl.aidevhub.notes.DevelopmentLog;
import com.fhaicl.aidevhub.notes.ProjectLogService;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.todo.ProjectTodoService;
import com.fhaicl.aidevhub.todo.ProjectTodoStore;
import com.fhaicl.aidevhub.todo.TodoItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ProjectWorkflowIntegrationTest {
    @TempDir Path directory;

    @Test void projectRenameAndRestartPreserveNotesTodosAndSelectionByPath() throws Exception {
        Path first = Files.createDirectories(directory.resolve("工作区 one/project"));
        Path second = Files.createDirectories(directory.resolve("工作区 two/project"));
        ProjectList projects = new ProjectList();
        assertTrue(projects.add(first));
        assertTrue(projects.add(second));
        assertFalse(projects.add(first.resolve(".")));
        Project selected = projects.projects().getFirst();
        CurrentProject current = new CurrentProject();
        current.select(selected);
        Path data = directory.resolve("app data");
        ProjectStore store = new ProjectStore(data.resolve("projects.xml"));
        ProjectTodoStore todoStore = new ProjectTodoStore(data.resolve("todos"));
        ProjectLogStore logStore = new ProjectLogStore(data.resolve("notes"));
        var todos = new ProjectTodoService(todoStore).add(selected, List.of(), "验收项目", TodoItem.Priority.HIGH);
        var log = new DevelopmentLog("开发记录", "# 中文内容\n完成测试", LocalDate.of(2026, 1, 1));
        new ProjectLogService(logStore).create(selected, List.of(), log);
        Project renamed = projects.rename(selected, "重命名后的项目");
        store.save(projects.projects());
        current.synchronize(projects.projects());
        assertEquals(renamed, current.project());

        ProjectList restarted = new ProjectList();
        restarted.replaceAll(new ProjectStore(data.resolve("projects.xml")).load());
        assertEquals(projects.projects(), restarted.projects());
        Project restored = restarted.projects().getFirst();
        assertEquals(todos, new ProjectTodoStore(data.resolve("todos")).load(restored));
        assertEquals(log, new ProjectLogService(new ProjectLogStore(data.resolve("notes"))).latest(restored).orElseThrow());
        assertEquals(1, new ProjectTodoService(new ProjectTodoStore(data.resolve("todos"))).incompleteCount(restored));
        assertTrue(todoStore.load(restarted.projects().getLast()).isEmpty());
        assertTrue(logStore.load(restarted.projects().getLast()).isEmpty());

        // Removing a list entry clears selection but does not delete a user's directory or associated data.
        assertTrue(restarted.remove(restored));
        store.save(restarted.projects());
        current.synchronize(restarted.projects());
        assertNull(current.project());
        assertTrue(Files.isDirectory(first));
        assertEquals(todos, todoStore.load(restored));
        assertTrue(restarted.add(first));
        assertEquals(todos, todoStore.load(restarted.projects().getLast()));
    }

    @Test void independentServiceInstancesRejectStaleSnapshotsAfterRestart() throws Exception {
        Project project = new Project("project", directory.resolve("中文 workspace"));
        Path data = directory.resolve("data");
        var firstTodo = new ProjectTodoService(new ProjectTodoStore(data.resolve("todos")));
        var secondTodo = new ProjectTodoService(new ProjectTodoStore(data.resolve("todos")));
        var tasks = firstTodo.add(project, List.of(), "first");
        assertThrows(java.io.IOException.class, () -> secondTodo.add(project, List.of(), "stale"));
        assertEquals(tasks, new ProjectTodoStore(data.resolve("todos")).load(project));
        var updated = secondTodo.setCompleted(project, tasks, 0, true);
        assertThrows(java.io.IOException.class, () -> firstTodo.delete(project, tasks, 0));
        assertEquals(updated, new ProjectTodoStore(data.resolve("todos")).load(project));
        var firstLog = new ProjectLogService(new ProjectLogStore(data.resolve("notes")));
        var secondLog = new ProjectLogService(new ProjectLogStore(data.resolve("notes")));
        var log = new DevelopmentLog("first", "内容");
        var logs = firstLog.create(project, List.of(), log);
        assertThrows(java.io.IOException.class, () -> secondLog.create(project, List.of(), new DevelopmentLog("stale", "内容")));
        assertEquals(logs, new ProjectLogStore(data.resolve("notes")).load(project));
    }
}
