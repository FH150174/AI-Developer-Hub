package com.fhaicl.aidevhub.environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class EnvironmentDiagnosticsService {
    private final EnvironmentVersionService.Runner runner;
    private final boolean windows;

    public EnvironmentDiagnosticsService() {
        this(new VersionCommandExecutor()::execute, System.getProperty("os.name", ""));
    }

    EnvironmentDiagnosticsService(EnvironmentVersionService.Runner runner, String operatingSystem) {
        this.runner = Objects.requireNonNull(runner);
        windows = operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    public String idePath(Path path) {
        if (path == null) return "未配置，请在 Settings 中设置并保存。";
        return path + (Files.isRegularFile(path) ? "（文件存在）" : "（文件不存在或无法访问，请在 Settings 中修正）");
    }

    public String checkPath(String value) {
        if (value == null || value.isBlank()) return "PATH 未设置或为空。";
        String separator = windows ? ";" : ":";
        var seen = new HashSet<String>();
        var directories = new ArrayList<Path>();
        var lines = new ArrayList<String>();
        int index = 0;
        for (String entry : value.split(Pattern.quote(separator), -1)) {
            String text = entry.strip();
            if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
                text = text.substring(1, text.length() - 1);
            }
            String prefix = ++index + ". ";
            if (text.isEmpty()) {
                lines.add(prefix + "空目录项（可能隐式使用当前目录）");
                continue;
            }
            try {
                Path path = Path.of(text);
                String key = path.toAbsolutePath().normalize().toString();
                if (windows) key = key.toLowerCase(Locale.ROOT);
                boolean duplicate = !seen.add(key);
                boolean exists = Files.isDirectory(path);
                lines.add(prefix + text + (exists ? "（目录存在）" : "（目录不存在或无法访问）")
                        + (duplicate ? "（重复）" : "") + (path.isAbsolute() ? "" : "（相对路径，依赖当前目录）"));
                if (exists) directories.add(path);
            } catch (InvalidPathException | SecurityException exception) {
                lines.add(prefix + text + "（格式无效或无权访问）");
            }
        }
        lines.add("\n常用工具在 PATH 中的首个匹配（不执行文件；实际版本见上方）：");
        for (String tool : List.of("java", "mvn", "git", "node", "python")) {
            String executable = windows ? tool + (tool.equals("mvn") ? ".cmd" : ".exe") : tool;
            Path found = null;
            for (Path directory : directories) {
                Path candidate = directory.resolve(executable);
                try {
                    if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
                        found = candidate.toAbsolutePath().normalize();
                        break;
                    }
                } catch (SecurityException exception) {
                    lines.add(executable + "：无权检查 " + candidate);
                }
            }
            lines.add(executable + "：" + (found == null ? "未找到" : found));
        }
        return String.join("\n", lines);
    }

    public String wslStatus() throws IOException {
        if (!windows) return "当前非 Windows 环境，不检测 Windows WSL。";
        String installed = queryWsl(List.of("wsl.exe", "--list", "--quiet"));
        if (installed.isBlank()) return "WSL 可用，暂无已注册发行版。";
        String running = queryWsl(List.of("wsl.exe", "--list", "--running", "--quiet"));
        return "已注册发行版：" + installed.replace("\n", "、")
                + "\n运行中的发行版：" + (running.isBlank() ? "无" : running.replace("\n", "、"));
    }

    private String queryWsl(List<String> command) throws IOException {
        VersionCommandExecutor.Result result;
        try {
            result = runner.run(command);
        } catch (IOException exception) {
            throw new IOException("WSL 检测失败，请确认 WSL 已安装、命令可访问且未超时。", exception);
        }
        if (result.exitCode() != 0) {
            throw new IOException("WSL 查询失败（退出码 " + result.exitCode()
                    + "），请在终端运行 wsl --list --verbose 检查安装及系统配置。");
        }
        return result.output().replace("\r", "").strip();
    }
}
