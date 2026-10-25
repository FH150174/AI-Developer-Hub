package com.fhaicl.aidevhub.project;

import java.util.List;
import java.util.Objects;

public final class CurrentProject {
    private Project project;

    public Project project() {
        return project;
    }

    public void select(Project selected) {
        project = Objects.requireNonNull(selected, "Selected project must not be null");
    }

    public void synchronize(List<Project> projects) {
        if (project != null) {
            var path = project.path();
            project = projects.stream().filter(candidate -> candidate.path().equals(path))
                    .findFirst().orElse(null);
        }
    }
}
