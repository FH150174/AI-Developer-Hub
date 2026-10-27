package com.fhaicl.aidevhub.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class IdePathSettings {
    public enum Ide {
        IDEA("IDEA", "idea64.exe", "idea.exe"),
        VS_CODE("VS Code", "Code.exe"),
        CURSOR("Cursor", "Cursor.exe");

        private final String displayName;
        private final String[] executableNames;

        Ide(String displayName, String... executableNames) {
            this.displayName = displayName;
            this.executableNames = executableNames;
        }

        public String displayName() { return displayName; }
        public String[] executableNames() { return executableNames.clone(); }

        private boolean accepts(Path path) {
            if (path.getFileName() == null) return false;
            for (String name : executableNames) {
                if (name.equalsIgnoreCase(path.getFileName().toString())) return true;
            }
            return false;
        }
    }

    private final Path file;
    private Map<Ide, Path> paths = new EnumMap<>(Ide.class);
    private boolean loaded;

    public IdePathSettings(Path file) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    public void load() throws IOException {
        loaded = false;
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) {
            data.loadFromXML(input);
        } catch (NoSuchFileException exception) {
            paths = new EnumMap<>(Ide.class);
            loaded = true;
            return;
        }
        Map<Ide, Path> restored = new EnumMap<>(Ide.class);
        try {
            for (String key : data.stringPropertyNames()) {
                Ide ide = Ide.valueOf(key);
                Path path = Path.of(data.getProperty(key));
                if (!path.isAbsolute() || !ide.accepts(path)) {
                    throw new IllegalArgumentException("Invalid executable path for " + ide.displayName());
                }
                // Keep stale paths visible so users can repair moved or uninstalled IDEs.
                restored.put(ide, path.normalize());
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("IDE 路径配置无效：" + file, exception);
        }
        paths = restored;
        loaded = true;
    }

    public Path path(Ide ide) {
        return paths.get(Objects.requireNonNull(ide));
    }

    public Path requirePath(Ide ide) throws IOException {
        if (!loaded) throw new IOException("IDE 路径配置加载失败，请检查配置文件后重启。");
        Path path = path(ide);
        if (path == null) throw new IOException("请先在 Settings 中设置并保存 " + ide.displayName() + " 路径。");
        return path;
    }

    public void save(Map<Ide, String> values) throws IOException {
        if (!loaded) throw new IOException("配置尚未成功加载，禁止覆盖原配置。");
        Map<Ide, Path> updated = new EnumMap<>(Ide.class);
        Properties data = new Properties();
        for (Ide ide : Ide.values()) {
            String value = values.getOrDefault(ide, "").strip();
            if (value.isEmpty()) continue;
            Path path;
            try {
                path = Path.of(value).normalize();
            } catch (IllegalArgumentException exception) {
                throw new IOException(ide.displayName() + " 路径格式无效。", exception);
            }
            if (!path.isAbsolute() || !ide.accepts(path) || !Files.isRegularFile(path)) {
                throw new IOException("请选择存在的 " + ide.displayName() + " 启动程序："
                        + String.join(" / ", ide.executableNames()));
            }
            updated.put(ide, path);
            data.setProperty(ide.name(), path.toString());
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "ide-paths-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.storeToXML(output, "AI Developer Hub IDE paths", "UTF-8");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        paths = updated;
    }
}
