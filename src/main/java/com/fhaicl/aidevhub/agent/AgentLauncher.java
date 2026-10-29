package com.fhaicl.aidevhub.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

public final class AgentLauncher {
    public enum Tool {
        CODEX("Codex CLI", "codex"),
        CLAUDE("Claude CLI", "claude"),
        ANTIGRAVITY("Antigravity", "antigravity"),
        POWERSHELL("PowerShell", "powershell"),
        WSL("WSL", "wsl");

        private final String displayName;
        private final String command;
        Tool(String displayName, String command) {
            this.displayName = displayName;
            this.command = command;
        }
        public String displayName() { return displayName; }
    }

    @FunctionalInterface
    interface ProcessStarter {
        void start(ProcessBuilder builder) throws IOException;
    }

    private final String operatingSystem;
    private final String searchPath;
    private final Path directory;
    private final Predicate<Path> isFile;
    private final ProcessStarter starter;

    public AgentLauncher() {
        this(System.getProperty("os.name", ""), System.getenv("PATH"),
                Path.of(System.getProperty("user.home")), Files::isRegularFile, builder -> builder.start());
    }

    AgentLauncher(String operatingSystem, String searchPath, Path directory,
                  Predicate<Path> isFile, ProcessStarter starter) {
        this.operatingSystem = Objects.requireNonNull(operatingSystem);
        this.searchPath = searchPath;
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.isFile = Objects.requireNonNull(isFile);
        this.starter = Objects.requireNonNull(starter);
    }

    public void launch(Tool tool) throws IOException {
        Objects.requireNonNull(tool);
        if (!operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows")) {
            throw new IOException("当前仅支持在 Windows 上启动工具。");
        }
        if (!Files.isDirectory(directory)) throw new IOException("用户目录不存在或无法访问。");
        if (directory.toString().startsWith("\\\\")) {
            throw new IOException("当前启动终端不支持 UNC 用户目录。");
        }
        if (!available(tool)) {
            throw new IOException("未找到 " + tool.displayName() + " 命令（" + tool.command
                    + "）。请确认已安装并加入 PATH，修改环境变量后重启应用。");
        }
        // All shell tokens are fixed constants; paths and user input are never shell commands.
        List<String> command = tool == Tool.POWERSHELL
                ? List.of("cmd.exe", "/d", "/c", "start", "\"\"", "powershell.exe", "-NoExit")
                : List.of("cmd.exe", "/d", "/c", "start", "\"\"", "cmd.exe", "/d", "/k", tool.command);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            starter.start(builder);
        } catch (IOException exception) {
            throw new IOException("无法打开 " + tool.displayName() + " 启动窗口，请检查终端及访问权限。", exception);
        }
    }

    private boolean available(Tool tool) throws IOException {
        if (searchPath == null || searchPath.isBlank()) return false;
        for (String entry : searchPath.split(";")) {
            String value = entry.strip();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            if (value.isBlank()) continue;
            try {
                Path path = Path.of(value);
                for (String extension : List.of(".exe", ".cmd", ".bat")) {
                    if (isFile.test(path.resolve(tool.command + extension))) return true;
                }
            } catch (InvalidPathException exception) {
                throw new IOException("PATH 包含格式无效的目录，请在 Environment 页面检查。", exception);
            }
        }
        return false;
    }
}
