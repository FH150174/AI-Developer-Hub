package com.fhaicl.aidevhub.notes;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;
import java.time.LocalDate;
import java.time.DateTimeException;
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

public final class ProjectLogStore {
    private final Path directory;

    public ProjectLogStore(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    public List<DevelopmentLog> load(Project project) throws IOException {
        Properties data = new Properties();
        try (var input = Files.newInputStream(file(project))) {
            data.loadFromXML(input);
        } catch (NoSuchFileException exception) {
            return List.of();
        }
        try {
            int count = Integer.parseInt(data.getProperty("count"));
            if (!key(project).equals(data.getProperty("project.path")) || count < 0
                    || count > (data.size() - 2L) / 2L) {
                throw new IllegalArgumentException("Invalid project log data");
            }
            List<DevelopmentLog> logs = new ArrayList<>();
            int dateCount = 0;
            for (int i = 0; i < count; i++) {
                String date = data.getProperty(i + ".date");
                if (date != null) dateCount++;
                logs.add(new DevelopmentLog(data.getProperty(i + ".title"), data.getProperty(i + ".content"),
                        date == null ? null : LocalDate.parse(date)));
            }
            if (data.size() != 2L + 2L * count + dateCount) {
                throw new IllegalArgumentException("Unexpected project log fields");
            }
            return List.copyOf(logs);
        } catch (IllegalArgumentException | NullPointerException | DateTimeException exception) {
            throw new IOException("项目日志数据无效，请检查日志存储文件。", exception);
        }
    }

    public void save(Project project, List<DevelopmentLog> logs) throws IOException {
        List<DevelopmentLog> snapshot = List.copyOf(logs);
        Properties data = new Properties();
        data.setProperty("project.path", key(project));
        data.setProperty("count", Integer.toString(snapshot.size()));
        for (int i = 0; i < snapshot.size(); i++) {
            data.setProperty(i + ".title", snapshot.get(i).title());
            data.setProperty(i + ".content", snapshot.get(i).content());
            if (snapshot.get(i).date() != null) {
                data.setProperty(i + ".date", snapshot.get(i).date().toString());
            }
        }
        Path destination = file(project);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "logs-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.storeToXML(output, "AI Developer Hub project logs", "UTF-8");
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
            // A fixed-length filename supports arbitrary project paths without writing into the project.
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key(project).getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".xml");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }
}
