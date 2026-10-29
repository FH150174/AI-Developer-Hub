package com.fhaicl.aidevhub.notes;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Comparator;
import java.util.Optional;

public final class ProjectLogService {
    private final ProjectLogStore store;

    public ProjectLogService(ProjectLogStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public List<DevelopmentLog> create(Project project, List<DevelopmentLog> expected, DevelopmentLog log) throws IOException {
        var updated = new ArrayList<>(expected);
        updated.add(Objects.requireNonNull(log));
        return save(project, expected, updated);
    }

    public List<DevelopmentLog> edit(Project project, List<DevelopmentLog> expected, int index, DevelopmentLog log) throws IOException {
        var updated = new ArrayList<>(expected);
        updated.set(index, Objects.requireNonNull(log));
        return save(project, expected, updated);
    }

    public List<DevelopmentLog> delete(Project project, List<DevelopmentLog> expected, int index) throws IOException {
        var updated = new ArrayList<>(expected);
        updated.remove(index);
        return save(project, expected, updated);
    }

    public Optional<DevelopmentLog> latest(Project project) throws IOException {
        Comparator<DevelopmentLog> byDate = Comparator.comparing(
                DevelopmentLog::date, Comparator.nullsFirst(Comparator.naturalOrder()));
        // For equal or absent dates, prefer the later entry in the saved list.
        return store.load(project).stream().reduce((previous, next) ->
                byDate.compare(previous, next) > 0 ? previous : next);
    }

    private List<DevelopmentLog> save(Project project, List<DevelopmentLog> expected, List<DevelopmentLog> updated) throws IOException {
        // Reject changes based on an outdated or unreadable list instead of replacing unseen data.
        if (!store.load(project).equals(expected)) {
            throw new IOException("日志已发生变化，请重新进入 Notes 加载后再操作。");
        }
        store.save(project, updated);
        return List.copyOf(updated);
    }
}
