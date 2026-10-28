package com.fhaicl.aidevhub.todo;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class ProjectTodoStore {
    private final Path directory;

    public ProjectTodoStore(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    public List<TodoItem> load(Project project) throws IOException {
        Properties data = new Properties();
        try (var input = Files.newInputStream(file(project))) {
            data.loadFromXML(input);
        } catch (NoSuchFileException exception) {
            return List.of();
        }
        try {
            int count = Integer.parseInt(data.getProperty("count"));
            if (!key(project).equals(data.getProperty("project.path")) || count < 0
                    || count > data.size() - 2L) {
                throw new IllegalArgumentException("Invalid project TODO data");
            }
            List<TodoItem> items = new ArrayList<>();
            int statusCount = 0;
            int priorityCount = 0;
            for (int i = 0; i < count; i++) {
                String completed = data.getProperty(i + ".completed");
                if (completed != null) {
                    statusCount++;
                    if (!completed.equals("true") && !completed.equals("false")) {
                        throw new IllegalArgumentException("Invalid TODO completion status");
                    }
                }
                String priority = data.getProperty(i + ".priority");
                if (priority != null) priorityCount++;
                items.add(new TodoItem(data.getProperty(i + ".title"), Boolean.parseBoolean(completed),
                        priority == null ? TodoItem.Priority.MEDIUM : TodoItem.Priority.valueOf(priority)));
            }
            if (data.size() != 2L + count + statusCount + priorityCount) {
                throw new IllegalArgumentException("Unexpected TODO fields");
            }
            return List.copyOf(items);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("项目 TODO 数据无效，请检查 TODO 存储文件。", exception);
        }
    }

    public void save(Project project, List<TodoItem> items) throws IOException {
        List<TodoItem> snapshot = List.copyOf(items);
        Properties data = new Properties();
        data.setProperty("project.path", key(project));
        data.setProperty("count", Integer.toString(snapshot.size()));
        for (int i = 0; i < snapshot.size(); i++) {
            data.setProperty(i + ".title", snapshot.get(i).title());
            data.setProperty(i + ".completed", Boolean.toString(snapshot.get(i).completed()));
            data.setProperty(i + ".priority", snapshot.get(i).priority().name());
        }
        Path destination = file(project);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "todos-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.storeToXML(output, "AI Developer Hub project TODOs", "UTF-8");
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String key(Project project) {
        return Objects.requireNonNull(project).path().toAbsolutePath().normalize().toString();
    }

    private Path file(Project project) {
        try {
            // Stable filenames isolate project paths and keep application data outside project directories.
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key(project).getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".xml");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }
}
