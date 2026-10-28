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
            builder.directory(directory.toAbsolutePath().normalize().toFile());
        }
        // Prevent inherited Git settings from redirecting queries to another repository.
        builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
        builder.environment().put("LC_ALL", "C");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        Path output = Files.createTempFile("aidevhub-git-output-", ".txt");
        try {
            Path error = Files.createTempFile("aidevhub-git-error-", ".txt");
            try {
                builder.redirectOutput(output.toFile());
                builder.redirectError(error.toFile());
                Process process;
                try {
                    process = starter.start(builder);
                } catch (IOException exception) {
                    throw new IOException("无法启动 Git，请确认 Git 已安装、已加入 PATH 且工作目录可访问。", exception);
                }
                try {
                    process.getOutputStream().close();
                    if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new IOException("Git 命令执行超时。");
                    }
                    // Keep all whitespace and NUL separators needed by porcelain status parsing.
                    return new Result(process.exitValue(),
                            Files.readString(output, StandardCharsets.UTF_8),
                            Files.readString(error, StandardCharsets.UTF_8));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Git 命令执行已中断。", exception);
                } finally {
                    terminate(process);
                }
            } finally {
                Files.deleteIfExists(error);
            }
        } finally {
            Files.deleteIfExists(output);
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
