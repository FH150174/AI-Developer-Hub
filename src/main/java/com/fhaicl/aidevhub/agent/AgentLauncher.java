package com.fhaicl.aidevhub.agent;

import java.io.IOException;
import com.fhaicl.aidevhub.project.Project;
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

    static final String CONTEXT_PROMPT = "Read the AIDEVHUB_PROJECT_CONTEXT environment variable as project context. "
            + "Read the existing project documents listed there and follow the project rules. "
            + "Briefly confirm the project context then wait for a specific task. Do not modify files or run Git write commands.";

    private final AgentPathSettings settings;
    private final String operatingSystem;
    private final String searchPath;
    private final Predicate<Path> isFile;
    private final ProcessStarter starter;

    public AgentLauncher() {
        this(System.getProperty("os.name", ""), System.getenv("PATH"),
                Files::isRegularFile, builder -> builder.start());
    }

    public AgentLauncher(AgentPathSettings settings) {
        this(System.getProperty("os.name", ""), System.getenv("PATH"), Files::isRegularFile,
                builder -> builder.start(), Objects.requireNonNull(settings));
    }

    AgentLauncher(String operatingSystem, String searchPath,
                  Predicate<Path> isFile, ProcessStarter starter) {
        this(operatingSystem, searchPath, isFile, starter, null);
    }

    AgentLauncher(String operatingSystem, String searchPath, Predicate<Path> isFile,
                  ProcessStarter starter, AgentPathSettings settings) {
        this.settings = settings;
        this.operatingSystem = Objects.requireNonNull(operatingSystem);
        this.searchPath = searchPath;
        this.isFile = Objects.requireNonNull(isFile);
        this.starter = Objects.requireNonNull(starter);
    }

    public void launch(Tool tool, Project project) throws IOException {
        Objects.requireNonNull(tool);
        if (!operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows")) {
            throw new IOException("当前仅支持在 Windows 上启动工具。");
        }
        if (project == null) throw new IOException("未选择当前项目，请先在 Projects 中设为当前项目。");
        Path directory = project.path().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) throw new IOException("项目目录不存在或无法访问，请检查当前项目路径。");
        if (directory.toString().startsWith("\\\\")) {
            throw new IOException("当前启动终端不支持 UNC 项目目录。");
        }
        Path configured = settings == null ? null : settings.requirePath(tool);
        if (configured != null && !Files.isRegularFile(configured)) {
            throw new IOException(tool.displayName() + " 配置路径不存在或无法访问，请在 Settings 中修正。");
        }
        if (configured == null && !available(tool)) {
            throw new IOException("未找到 " + tool.displayName() + " 命令（" + tool.command
                    + "）。请确认已安装并加入 PATH，修改环境变量后重启应用。");
        }
        // All shell tokens are fixed constants; paths and user input are never shell commands.
        String executable = configured == null ? tool.command : "\"" + configured + "\"";
        List<String> command = tool == Tool.POWERSHELL
                ? List.of("cmd.exe", "/d", "/c", "start", "\"\"", configured == null ? "powershell.exe" : executable, "-NoExit")
                : List.of("cmd.exe", "/d", "/c", "start", "\"\"", "cmd.exe", "/d", "/k", executable);
        if (tool == Tool.ANTIGRAVITY) {
            command = new java.util.ArrayList<>(command);
            command.add(".");
        }
        if (tool == Tool.CODEX || tool == Tool.CLAUDE) {
            command = new java.util.ArrayList<>(command);
            command.add(CONTEXT_PROMPT);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("AIDEVHUB_PROJECT_NAME", project.name());
        builder.environment().put("AIDEVHUB_PROJECT_PATH", directory.toString());
        builder.environment().put("AIDEVHUB_PROJECT_CONTEXT", new ProjectContextService().context(project));
        if (tool == Tool.WSL) {
            String inherited = builder.environment().getOrDefault("WSLENV", "");
            java.util.ArrayList<String> variables = new java.util.ArrayList<>();
            for (String variable : inherited.split(":")) {
                if (!variable.isBlank() && !variable.split("/", 2)[0].startsWith("AIDEVHUB_PROJECT_")) {
                    variables.add(variable);
                }
            }
            variables.addAll(List.of("AIDEVHUB_PROJECT_NAME/u", "AIDEVHUB_PROJECT_PATH/pu", "AIDEVHUB_PROJECT_CONTEXT/u"));
            builder.environment().put("WSLENV", String.join(":", variables));
        }
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
