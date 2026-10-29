package com.fhaicl.aidevhub.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class AgentPathSettings {
    private final Path file;
    private Map<AgentLauncher.Tool, Path> paths = new EnumMap<>(AgentLauncher.Tool.class);
    private Map<AgentLauncher.Tool, String> commands = new EnumMap<>(AgentLauncher.Tool.class);
    private boolean loaded;

    public AgentPathSettings(Path file) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    public synchronized void load() throws IOException {
        loaded = false;
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) {
            data.loadFromXML(input);
        } catch (NoSuchFileException exception) {
            paths = new EnumMap<>(AgentLauncher.Tool.class);
            commands = new EnumMap<>(AgentLauncher.Tool.class);
            loaded = true;
            return;
        }
        Map<AgentLauncher.Tool, Path> restored = new EnumMap<>(AgentLauncher.Tool.class);
        Map<AgentLauncher.Tool, String> restoredCommands = new EnumMap<>(AgentLauncher.Tool.class);
        try {
            for (String key : data.stringPropertyNames()) {
                if (key.endsWith(".command")) {
                    AgentLauncher.Tool tool = AgentLauncher.Tool.valueOf(key.substring(0, key.length() - ".command".length()));
                    String command = validateCommand(data.getProperty(key));
                    if (!command.isEmpty()) restoredCommands.put(tool, command);
                    continue;
                }
                AgentLauncher.Tool tool = AgentLauncher.Tool.valueOf(key);
                Path path = Path.of(data.getProperty(key));
                if (!path.isAbsolute() || !accepts(tool, path)) {
                    throw new IllegalArgumentException("Invalid executable path for " + tool.displayName());
                }
                // Keep stale paths visible so users can repair moved or uninstalled Agents.
                restored.put(tool, path.normalize());
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Agent 路径配置无效：" + file, exception);
        }
        paths = restored;
        commands = restoredCommands;
        loaded = true;
    }

    public synchronized Path path(AgentLauncher.Tool tool) {
        return paths.get(Objects.requireNonNull(tool));
    }

    public synchronized Path requirePath(AgentLauncher.Tool tool) throws IOException {
        if (!loaded) throw new IOException("Agent 路径配置加载失败，请检查配置文件后重启。");
        Path path = path(tool);
        return path;
    }

    public synchronized String command(AgentLauncher.Tool tool) {
        return commands.getOrDefault(Objects.requireNonNull(tool), "");
    }

    public synchronized String requireCommand(AgentLauncher.Tool tool) throws IOException {
        if (!loaded) throw new IOException("Agent 配置加载失败，请检查配置文件后重启。");
        return command(tool);
    }

    public synchronized void save(Map<AgentLauncher.Tool, String> values) throws IOException {
        save(values, commands);
    }

    public synchronized void save(Map<AgentLauncher.Tool, String> values,
                                  Map<AgentLauncher.Tool, String> commandValues) throws IOException {
        if (!loaded) throw new IOException("配置尚未成功加载，禁止覆盖原配置。");
        Map<AgentLauncher.Tool, Path> updated = new EnumMap<>(AgentLauncher.Tool.class);
        Properties data = new Properties();
        for (AgentLauncher.Tool tool : AgentLauncher.Tool.values()) {
            String value = values.getOrDefault(tool, "").strip();
            if (value.isEmpty()) continue;
            Path path;
            try {
                path = Path.of(value).normalize();
            } catch (IllegalArgumentException exception) {
                throw new IOException(tool.displayName() + " 路径格式无效。", exception);
            }
            if (!path.isAbsolute() || !accepts(tool, path) || !Files.isRegularFile(path)) {
                throw new IOException("请选择存在的 " + tool.displayName() + " 启动程序："
                        + String.join(" / ", executableNames(tool)));
            }
            updated.put(tool, path);
            data.setProperty(tool.name(), path.toString());
        }
        Map<AgentLauncher.Tool, String> updatedCommands = new EnumMap<>(AgentLauncher.Tool.class);
        for (AgentLauncher.Tool tool : AgentLauncher.Tool.values()) {
            String command;
            try {
                command = validateCommand(commandValues.getOrDefault(tool, ""));
            } catch (IllegalArgumentException exception) {
                throw new IOException(tool.displayName() + " 命令无效：请输入不超过 4000 字符的单行 CMD 命令。", exception);
            }
            if (!command.isEmpty()) {
                updatedCommands.put(tool, command);
                data.setProperty(tool.name() + ".command", command);
            }
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "agent-paths-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.storeToXML(output, "AI Developer Hub Agent paths", "UTF-8");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        paths = updated;
        commands = updatedCommands;
    }

    public static String[] executableNames(AgentLauncher.Tool tool) {
        String command = switch (tool) {
            case CODEX -> "codex";
            case CLAUDE -> "claude";
            case ANTIGRAVITY -> "antigravity";
            case POWERSHELL -> "powershell";
            case WSL -> "wsl";
        };
        return tool == AgentLauncher.Tool.POWERSHELL || tool == AgentLauncher.Tool.WSL
                ? new String[] {command + ".exe"}
                : new String[] {command + ".exe", command + ".cmd", command + ".bat"};
    }

    private static boolean accepts(AgentLauncher.Tool tool, Path path) {
        if (path.getFileName() == null) return false;
        // These paths cross CMD twice when opening a separate interactive window.
        if (path.toString().chars().anyMatch(character -> "\"%!^&|<>\r\n".indexOf(character) >= 0)) return false;
        for (String name : executableNames(tool)) {
            if (path.getFileName().toString().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static String validateCommand(String value) {
        Objects.requireNonNull(value);
        if (value.length() > 4000 || value.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("Command must be a single line of at most 4000 characters");
        }
        return value.strip();
    }
}
