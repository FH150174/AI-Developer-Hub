package com.fhaicl.aidevhub.todo;

import com.fhaicl.aidevhub.project.Project;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProjectTodoService {
    private final ProjectTodoStore store;

    public ProjectTodoService(ProjectTodoStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public long incompleteCount(Project project) throws IOException {
        return store.load(project).stream().filter(item -> !item.completed()).count();
    }

    public List<TodoItem> add(Project project, List<TodoItem> expected, String title) throws IOException {
        return add(project, expected, title, TodoItem.Priority.MEDIUM);
    }

    public List<TodoItem> add(Project project, List<TodoItem> expected, String title, TodoItem.Priority priority) throws IOException {
        var updated = new ArrayList<>(expected);
        updated.add(new TodoItem(title, false, priority));
        return save(project, expected, updated);
    }

    public List<TodoItem> setCompleted(Project project, List<TodoItem> expected, int index, boolean completed) throws IOException {
        var updated = new ArrayList<>(expected);
        updated.set(index, new TodoItem(updated.get(index).title(), completed, updated.get(index).priority()));
        return save(project, expected, updated);
    }

    public List<TodoItem> setPriority(Project project, List<TodoItem> expected, int index, TodoItem.Priority priority) throws IOException {
        var updated = new ArrayList<>(expected);
        TodoItem old = updated.get(index);
        updated.set(index, new TodoItem(old.title(), old.completed(), priority));
        return save(project, expected, updated);
    }

    public List<TodoItem> delete(Project project, List<TodoItem> expected, int index) throws IOException {
        var updated = new ArrayList<>(expected);
        updated.remove(index);
        return save(project, expected, updated);
    }

    private List<TodoItem> save(Project project, List<TodoItem> expected, List<TodoItem> updated) throws IOException {
        if (!store.load(project).equals(expected)) {
            throw new IOException("TODO 已发生变化，请重新进入 TODO 加载后再操作。");
        }
        store.save(project, updated);
        return List.copyOf(updated);
    }
}
