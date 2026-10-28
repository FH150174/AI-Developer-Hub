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
        Path output = Files.createTempFile("aidevhub-git-check-", ".txt");
        try {
            ProcessBuilder builder = new ProcessBuilder("git", "rev-parse", "--git-dir");
            builder.directory(project.path().toAbsolutePath().normalize().toFile());
            // Inherited Git overrides must not make us inspect another repository.
            builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
            builder.environment().put("LC_ALL", "C");
            builder.redirectErrorStream(true);
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
                if (process.exitValue() == 0) {
                    return Status.REPOSITORY;
                }
                String detail = Files.readString(output, StandardCharsets.UTF_8).strip();
                if (detail.startsWith("fatal: not a git repository")) {
                    return Status.NOT_REPOSITORY;
                }
                throw new IOException("Git 仓库检测失败：" + detail);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Git 仓库检测已中断。", exception);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
