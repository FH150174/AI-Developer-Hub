package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ProjectIdeaOpener {
    @FunctionalInterface
    interface ProcessStarter {
        void start(ProcessBuilder builder) throws IOException;
    }

    private final ProcessStarter starter;

    public ProjectIdeaOpener() {
        this(builder -> builder.start());
    }

    ProjectIdeaOpener(ProcessStarter starter) {
        this.starter = Objects.requireNonNull(starter);
    }

    public void open(Project project, Path executable) throws IOException {
        Objects.requireNonNull(project, "Project must not be null");
        Objects.requireNonNull(executable, "IDEA executable must not be null");
        Path directory = project.path().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IOException("项目目录不存在或无法访问：" + directory);
        }
        Path idea = executable.toAbsolutePath().normalize();
        String fileName = idea.getFileName() == null ? "" : idea.getFileName().toString();
        if (!Files.isRegularFile(idea)) {
            throw new IOException("未找到 IDEA 启动程序，可能已卸载或路径已变更。请安装 IDEA，或在 Settings 中重新设置路径。");
        }
        if (!(fileName.equalsIgnoreCase("idea64.exe") || fileName.equalsIgnoreCase("idea.exe"))) {
            throw new IOException("请选择 IDEA 安装目录 bin 下的 idea64.exe 或 idea.exe。");
        }
        ProcessBuilder builder = new ProcessBuilder(idea.toString(), directory.toString());
        builder.directory(directory.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            starter.start(builder);
        } catch (IOException exception) {
            throw new IOException("无法启动 IDEA，请检查程序是否可运行及访问权限，并在 Settings 中确认路径。", exception);
        }
    }
}
