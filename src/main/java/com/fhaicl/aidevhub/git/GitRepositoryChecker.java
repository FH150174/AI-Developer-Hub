package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.project.ProjectPathChecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class GitRepositoryChecker {
    public enum Status { REPOSITORY, NOT_REPOSITORY, MISSING, NOT_DIRECTORY }

    public Status check(Project project) throws IOException {
        Objects.requireNonNull(project, "Project must not be null");
        ProjectPathChecker.Status pathStatus = new ProjectPathChecker().check(project);
        if (pathStatus == ProjectPathChecker.Status.MISSING) {
            return Status.MISSING;
        }
        if (pathStatus == ProjectPathChecker.Status.NOT_DIRECTORY) {
            return Status.NOT_DIRECTORY;
        }
        CommandResult result = execute(project, "rev-parse", "--git-dir");
        if (result.exitCode() == 0) {
            return Status.REPOSITORY;
        }
        if (result.output().startsWith("fatal: not a git repository")) {
            return Status.NOT_REPOSITORY;
        }
        throw new IOException("Git 仓库检测失败：" + result.output());
    }

    record CommandResult(int exitCode, String output) { }

    static CommandResult execute(Project project, String... arguments) throws IOException {
        Path output = Files.createTempFile("aidevhub-git-check-", ".txt");
        try {
            return executeWithOutput(project, output, arguments);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static CommandResult executeWithOutput(Project project, Path output, String... arguments) throws IOException {
        Path error = Files.createTempFile("aidevhub-git-error-", ".txt");
        try {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("git");
            command.addAll(java.util.List.of(arguments));
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(project.path().toAbsolutePath().normalize().toFile());
            // Inherited Git overrides must not make us inspect another repository.
            builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
            builder.environment().put("LC_ALL", "C");
            builder.redirectError(error.toFile());
            builder.redirectOutput(output.toFile());
            Process process;
            try {
                process = builder.start();
            } catch (IOException exception) {
                throw new IOException("无法启动 Git，请确认 Git 已安装并已加入 PATH。", exception);
            }
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    throw new IOException("Git 仓库检测超时。");
                }
                return new CommandResult(process.exitValue(),
                        Files.readString(process.exitValue() == 0 ? output : error, StandardCharsets.UTF_8).strip());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Git 仓库检测已中断。", exception);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } finally {
            Files.deleteIfExists(error);
        }
    }
}
