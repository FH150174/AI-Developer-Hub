package com.fhaicl.aidevhub.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Resolves application storage and copies legacy data before any stores are opened. */
public final class AppDataDirectory {
    private static final List<String> FILES = List.of(
            "settings.xml", "ide-paths.xml", "agent-paths.xml", "projects.xml");
    private static final List<String> DIRECTORIES = List.of("notes", "todos");

    private AppDataDirectory() { }

    public static Path initialize() throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        return initialize(resolve(System.getenv("APPDATA"), home), home.resolve(".aidevhub"));
    }

    static Path resolve(String appData, Path home) throws IOException {
        try {
            Path parent = appData == null || appData.isBlank()
                    ? home.resolve("AppData").resolve("Roaming") : Path.of(appData);
            if (!parent.isAbsolute()) throw new IOException("应用数据目录必须是绝对路径。");
            return parent.resolve("AI-Developer-Hub").normalize();
        } catch (java.nio.file.InvalidPathException exception) {
            throw new IOException("APPDATA 路径格式无效。", exception);
        }
    }

    static Path initialize(Path destination, Path legacy) throws IOException {
        destination = destination.toAbsolutePath().normalize();
        legacy = legacy.toAbsolutePath().normalize();
        if (exists(destination)) {
            requireDirectory(destination);
            return destination;
        }
        Files.createDirectories(destination.getParent());
        Path staging = Files.createTempDirectory(destination.getParent(), "aidevhub-migration-");
        try {
            if (exists(legacy)) {
                requireDirectory(legacy);
                for (String name : FILES) {
                    Path source = legacy.resolve(name);
                    if (exists(source)) copyFile(source, staging.resolve(name));
                }
                for (String name : DIRECTORIES) {
                    Path source = legacy.resolve(name);
                    if (!exists(source)) continue;
                    requireDirectory(source);
                    Path target = Files.createDirectory(staging.resolve(name));
                    try (var files = Files.list(source)) {
                        for (Path file : files.toList()) {
                            // Store-generated project filenames are flat XML files; ignore unrelated files.
                            if (file.getFileName().toString().endsWith(".xml")) {
                                copyFile(file, target.resolve(file.getFileName()));
                            }
                        }
                    }
                }
            }
            // Publish only the complete copy. Never merge legacy data into an existing installation.
            Files.move(staging, destination);
        } catch (IOException | RuntimeException failure) {
            try {
                deleteStaging(staging);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return destination;
    }

    private static boolean exists(Path path) throws IOException {
        try {
            Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (java.nio.file.NoSuchFileException exception) {
            return false;
        }
    }

    private static void requireDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("应用数据目录不存在、无法访问或不是普通目录：" + directory);
        }
    }

    private static void copyFile(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("应用数据文件不是普通文件或无法读取：" + source);
        }
        Files.copy(source, target);
    }

    private static void deleteStaging(Path staging) throws IOException {
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
