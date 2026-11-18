package com.fhaicl.aidevhub.notes;

import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.storage.ProjectHistoryFiles;

import java.io.IOException;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ProjectLogStore {
    private final ProjectHistoryFiles files;

    public ProjectLogStore(Path directory) {
        this.files = new ProjectHistoryFiles(directory);
    }

    public List<DevelopmentLog> load(Project project) throws IOException {
        Properties data = new Properties();
        var entry = files.resolve(project.path());
        try (var input = Files.newInputStream(entry.file())) {
            data.loadFromXML(input);
        } catch (java.util.InvalidPropertiesFormatException exception) {
            throw new IOException("项目日志文件损坏或格式不受支持（需要 XML，不能使用 JSON）。原文件已保留，请备份后检查或从备份恢复："
                    + entry.file(), exception);
        } catch (NoSuchFileException exception) {
            return List.of();
        }
        try {
            int count = Integer.parseInt(data.getProperty("count"));
            if (!entry.key().equals(data.getProperty("project.path")) || count < 0
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
        var entry = files.resolve(project.path());
        Properties data = new Properties();
        data.setProperty("project.path", entry.key());
        data.setProperty("count", Integer.toString(snapshot.size()));
        for (int i = 0; i < snapshot.size(); i++) {
            data.setProperty(i + ".title", snapshot.get(i).title());
            data.setProperty(i + ".content", snapshot.get(i).content());
            if (snapshot.get(i).date() != null) {
                data.setProperty(i + ".date", snapshot.get(i).date().toString());
            }
        }
        Path destination = entry.file();
        Path directory = Files.createDirectories(destination.getParent());
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

}
