package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
        } catch (java.util.InvalidPropertiesFormatException exception) {
            throw new IOException("项目列表文件损坏或格式不受支持（需要 XML，不能使用 JSON）。原文件已保留，请备份后检查或从备份恢复："
                    + file, exception);
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

    public void save(List<Project> expected, List<Project> projects) throws IOException {
        Files.createDirectories(file.getParent());
        // Keep the sidecar: replacing the XML or deleting the lock file must not split writers across locks.
        Path lockFile = file.resolveSibling(file.getFileName() + ".lock");
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new SaveConflictException("另一个窗口正在保存项目列表，请稍后重新加载列表再试。", null);
            if (!load().equals(expected)) {
                throw new SaveConflictException("项目列表已被其他窗口修改，请重新加载列表后再试。", null);
            }
            write(projects);
        } catch (OverlappingFileLockException busy) {
            throw new SaveConflictException("项目列表正在保存，请稍后重新加载列表再试。", busy);
        }
    }

    public static final class SaveConflictException extends IOException {
        private SaveConflictException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void write(List<Project> projects) throws IOException {
        Properties data = new Properties();
        data.setProperty("count", Integer.toString(projects.size()));
        for (int i = 0; i < projects.size(); i++) {
            data.setProperty(i + ".name", projects.get(i).name());
            data.setProperty(i + ".path", projects.get(i).path().toString());
        }
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
