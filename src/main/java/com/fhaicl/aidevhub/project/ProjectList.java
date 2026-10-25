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

    public Project rename(Project project, String name) {
        Objects.requireNonNull(project, "Project must not be null");
        String normalizedName = Objects.requireNonNull(name, "Project name must not be null").strip();
        Project renamed = new Project(normalizedName, project.path());
        int index = projects.indexOf(project);
        if (index < 0) {
            throw new IllegalArgumentException("Project is not in the list");
        }
        projects.set(index, renamed);
        return renamed;
    }

    public List<Project> projects() {
        return List.copyOf(projects);
    }

    void replaceAll(List<Project> saved) {
        projects.clear();
        projects.addAll(saved);
    }
}
