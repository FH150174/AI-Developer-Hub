package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

public final class ProjectStore {
    private final Path file;

    public ProjectStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public List<Project> load() throws IOException {
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) {
            data.loadFromXML(input);
        } catch (NoSuchFileException exception) {
            return List.of();
        }
        try {
            int count = Integer.parseInt(data.getProperty("count"));
            if (count < 0 || data.size() != 1L + 2L * count) {
                throw new IllegalArgumentException("Invalid project count");
            }
            List<Project> projects = new ArrayList<>();
            var paths = new HashSet<Path>();
            for (int i = 0; i < count; i++) {
                Path path = Path.of(data.getProperty(i + ".path"));
                if (!path.isAbsolute() || !paths.add(path.normalize())) {
                    throw new IllegalArgumentException("Invalid or duplicate project path");
                }
                projects.add(new Project(data.getProperty(i + ".name"), path.normalize()));
            }
            return List.copyOf(projects);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Invalid project data in " + file, exception);
        }
    }

    public void save(List<Project> projects) throws IOException {
        Properties data = new Properties();
        data.setProperty("count", Integer.toString(projects.size()));
        for (int i = 0; i < projects.size(); i++) {
            data.setProperty(i + ".name", projects.get(i).name());
            data.setProperty(i + ".path", projects.get(i).path().toString());
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "projects-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.storeToXML(output, "AI Developer Hub projects", "UTF-8");
            }
            // Fail safely if the filesystem cannot atomically replace the saved list.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
