package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ProjectTerminalOpener {
    @FunctionalInterface
    interface ProcessStarter {
        void start(ProcessBuilder builder) throws IOException;
    }

    private final String operatingSystem;
    private final ProcessStarter starter;

    public ProjectTerminalOpener() {
        this(System.getProperty("os.name", ""), builder -> builder.start());
    }

    ProjectTerminalOpener(String operatingSystem, ProcessStarter starter) {
        this.operatingSystem = Objects.requireNonNull(operatingSystem);
        this.starter = Objects.requireNonNull(starter);
    }

    public void open(Project project) throws IOException {
        Objects.requireNonNull(project, "Project must not be null");
        if (!operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows")) {
            throw new IOException("当前仅支持在 Windows 上打开终端。");
        }
        Path directory = project.path().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IOException("项目目录不存在或无法访问：" + directory);
        }
        // CMD cannot retain a UNC working directory and would fall back to another directory.
        if (directory.toString().startsWith("\\\\")) {
            throw new IOException("CMD 不支持 UNC 网络路径作为工作目录，请使用本地项目目录。");
        }
        ProcessBuilder builder = new ProcessBuilder(List.of(
                "cmd.exe", "/d", "/c", "start", "\"\"", "cmd.exe", "/d", "/k"));
        builder.directory(directory.toFile());
        starter.start(builder);
    }
}
