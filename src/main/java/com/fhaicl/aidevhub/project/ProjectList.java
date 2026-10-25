package com.fhaicl.aidevhub.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProjectList {
    private final List<Project> projects = new ArrayList<>();

    public boolean add(Path directory) {
        Path path = Objects.requireNonNull(directory, "Project directory must not be null")
                .toAbsolutePath().normalize();
        if (projects.stream().anyMatch(project -> project.path().equals(path))) {
            return false;
        }
        Path fileName = path.getFileName();
        projects.add(new Project(fileName == null ? path.toString() : fileName.toString(), path));
        return true;
    }

    public boolean remove(Project project) {
        return projects.remove(project);
    }

    public List<Project> projects() {
        return List.copyOf(projects);
    }
}
