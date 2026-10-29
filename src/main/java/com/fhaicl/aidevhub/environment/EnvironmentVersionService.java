package com.fhaicl.aidevhub.environment;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class EnvironmentVersionService {
    public enum Tool {
        JAVA("Java", "java", "-version", "(?:openjdk|java) version \"[^\"]+\"[^\\r\\n]*"),
        MAVEN("Maven", "mvn", "--version", "Apache Maven [0-9]+\\.[0-9]+[^\\r\\n]*"),
        GIT("Git", "git", "--version", "git version [0-9]+\\.[0-9]+[^\\r\\n]*"),
        NODE("Node.js", "node", "--version", "v[0-9]+\\.[0-9]+\\.[0-9]+[^\\r\\n]*"),
        PYTHON("Python", "python", "--version", "Python [0-9]+\\.[0-9]+[^\\r\\n]*");

        private final String displayName;
        private final String executable;
        private final String argument;
        private final Pattern banner;

        Tool(String displayName, String executable, String argument, String banner) {
            this.displayName = displayName;
            this.executable = executable;
            this.argument = argument;
            this.banner = Pattern.compile("(?m)^" + banner + "$");
        }

        public String displayName() { return displayName; }
    }

    @FunctionalInterface
    interface Runner {
        VersionCommandExecutor.Result run(List<String> command) throws IOException;
    }

    private final Runner runner;
    private final boolean windows;

    public EnvironmentVersionService() {
        this(new VersionCommandExecutor()::execute, System.getProperty("os.name", ""));
    }

    EnvironmentVersionService(Runner runner, String operatingSystem) {
        this.runner = Objects.requireNonNull(runner);
        windows = operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    public String version(Tool tool) throws IOException {
        Objects.requireNonNull(tool);
        // Only this fixed command uses a shell; no project path or user input is interpolated.
        List<String> command = windows && tool == Tool.MAVEN
                ? List.of("cmd.exe", "/d", "/c", "mvn.cmd", "--version")
                : List.of(tool.executable, tool.argument);
        VersionCommandExecutor.Result result;
        try {
            result = runner.run(command);
        } catch (IOException exception) {
            throw new IOException("无法检测 " + tool.displayName()
                    + "。请确认已安装、已加入 PATH 且可正常启动；环境修改后请重启应用。", exception);
        }
        if (result.exitCode() != 0) {
            throw new IOException(tool.displayName() + " 版本命令失败（退出码 " + result.exitCode()
                    + "），请在终端检查安装及 PATH 配置。");
        }
        String output = result.output().replaceAll("\\x1B\\[[0-9;]*[A-Za-z]", "").replace("\r", "");
        var match = tool.banner.matcher(output);
        if (!match.find()) throw new IOException(tool.displayName() + " 未返回有效版本信息，请检查安装及 PATH 配置。");
        return match.group().strip();
    }
}
