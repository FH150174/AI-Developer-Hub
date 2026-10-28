package com.fhaicl.aidevhub.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class GitCommandExecutor {
    public record Result(int exitCode, String stdout, String stderr) {
        public String output() {
            return exitCode == 0 ? stdout : stderr;
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(ProcessBuilder builder) throws IOException;
    }

    private final Duration timeout;
    private final ProcessStarter starter;

    public GitCommandExecutor() {
        this(Duration.ofSeconds(5), ProcessBuilder::start);
    }

    GitCommandExecutor(Duration timeout, ProcessStarter starter) {
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isNegative() || timeout.isZero() || timeout.toMillis() == 0) {
            throw new IllegalArgumentException("Timeout must be at least one millisecond");
        }
        this.starter = Objects.requireNonNull(starter);
    }

    public Result execute(String... arguments) throws IOException {
        return execute(null, arguments);
    }

    /** Executes directly without a shell. A null directory uses the application's working directory. */
    public Result execute(Path directory, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        if (arguments.length == 0) {
            throw new IllegalArgumentException("Git arguments must not be empty");
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) {
            if (!Files.isDirectory(directory)) {
                throw new GitException(GitException.Kind.INVALID_DIRECTORY,
                        "项目目录不存在或无法访问，请重新检查项目路径。", null);
            }
            builder.directory(directory.toAbsolutePath().normalize().toFile());
        }
        // Prevent inherited Git settings from redirecting queries to another repository.
        builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
        builder.environment().put("LC_ALL", "C");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        try (TemporaryFile output = new TemporaryFile(Files.createTempFile("aidevhub-git-output-", ".txt"));
             TemporaryFile error = new TemporaryFile(Files.createTempFile("aidevhub-git-error-", ".txt"))) {
            builder.redirectOutput(output.path().toFile());
            builder.redirectError(error.path().toFile());
            Process process;
            try {
                process = starter.start(builder);
            } catch (IOException exception) {
                throw new GitException(GitException.Kind.START_FAILED,
                        "无法启动 Git，请确认 Git 已安装、已加入 PATH 且工作目录可访问。", exception);
            }
            // Resource cleanup failures are suppressed onto the original error instead of replacing it.
            try (RunningProcess running = new RunningProcess(process)) {
                process.getOutputStream().close();
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new GitException(GitException.Kind.TIMEOUT,
                            "Git 命令执行超时，请稍后重试或检查仓库所在磁盘状态。", null);
                }
                return new Result(process.exitValue(),
                        Files.readString(output.path(), StandardCharsets.UTF_8),
                        Files.readString(error.path(), StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new GitException(GitException.Kind.INTERRUPTED, "Git 查询已中断，可重新执行查询。", exception);
            }
        }
    }

    private record TemporaryFile(Path path) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }

    private record RunningProcess(Process process) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            terminate(process);
        }
    }

    private static void terminate(Process process) throws IOException {
        if (!process.isAlive()) {
            return;
        }
        boolean interrupted = Thread.interrupted();
        try {
            process.destroyForcibly();
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                throw new IOException("无法终止 Git 进程。");
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            throw new IOException("等待 Git 进程退出时被中断。", exception);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
