package com.fhaicl.aidevhub.settings;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public final class AppSettings {
    public enum Terminal { POWERSHELL_7, WINDOWS_POWERSHELL, CMD }
    public enum Theme { LIGHT, DARK }
    public enum StartPage { Dashboard, Projects }
    public record Values(Terminal terminal, String projectDirectory, Theme theme, StartPage startPage) {
        public Values {
            java.util.Objects.requireNonNull(terminal);
            java.util.Objects.requireNonNull(projectDirectory);
            java.util.Objects.requireNonNull(theme);
            java.util.Objects.requireNonNull(startPage);
        }
    }
    private final Path file;
    private Values values = new Values(Terminal.POWERSHELL_7, "", Theme.LIGHT, StartPage.Dashboard);
    private boolean loaded;

    public AppSettings(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public Values values() { return values; }

    public void load() throws IOException {
        loaded = false;
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) {
            data.loadFromXML(input);
        } catch (NoSuchFileException exception) {
            loaded = true;
            return;
        }
        try {
            Values restored = new Values(Terminal.valueOf(data.getProperty("terminal", "POWERSHELL_7")),
                    data.getProperty("projectDirectory", ""), Theme.valueOf(data.getProperty("theme", "LIGHT")),
                    StartPage.valueOf(data.getProperty("startPage", "Dashboard")));
            if (!restored.projectDirectory().isEmpty() && !Path.of(restored.projectDirectory()).isAbsolute()) {
                throw new IllegalArgumentException("项目目录必须为绝对路径");
            }
            values = restored;
        } catch (IllegalArgumentException exception) {
            throw new IOException("应用设置无效：" + file, exception);
        }
        loaded = true;
    }

    public void save(Values updated) throws IOException {
        if (!loaded) throw new IOException("设置未成功加载，禁止覆盖原文件。");
        String directory = updated.projectDirectory().strip();
        try {
            if (!directory.isEmpty() && (!Path.of(directory).isAbsolute() || !Files.isDirectory(Path.of(directory)))) {
                throw new IOException("请选择存在的绝对项目目录。");
            }
        } catch (InvalidPathException exception) {
            throw new IOException("项目目录格式无效。", exception);
        }
        Values normalized = new Values(updated.terminal(), directory, updated.theme(), updated.startPage());
        Properties data = new Properties();
        data.setProperty("terminal", normalized.terminal().name());
        data.setProperty("projectDirectory", directory);
        data.setProperty("theme", normalized.theme().name());
        data.setProperty("startPage", normalized.startPage().name());
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "settings-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.storeToXML(output, "AI Developer Hub settings", "UTF-8");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        values = normalized;
    }
}
