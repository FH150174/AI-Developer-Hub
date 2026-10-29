package com.fhaicl.aidevhub.environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class VersionCommandExecutor {
    record Result(int exitCode, String output) {}

    @FunctionalInterface
    interface ProcessStarter {
        Process start(ProcessBuilder builder) throws IOException;
    }

    private final Duration timeout;
    private final ProcessStarter starter;

    VersionCommandExecutor() {
        this(Duration.ofSeconds(5), ProcessBuilder::start);
    }

    VersionCommandExecutor(Duration timeout, ProcessStarter starter) {
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isNegative() || timeout.toMillis() < 1) {
            throw new IllegalArgumentException("Timeout must be at least one millisecond");
        }
        this.starter = Objects.requireNonNull(starter);
    }

    Result execute(List<String> command) throws IOException {
        if (command.isEmpty()) throw new IllegalArgumentException("Command must not be empty");
        // A file drains both streams even when a command emits more than a pipe can hold.
        try (OutputFile output = new OutputFile(Files.createTempFile("aidevhub-version-", ".txt"))) {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.path().toFile());
            try (RunningProcess running = new RunningProcess(starter.start(builder))) {
                Process process = running.process();
                process.getOutputStream().close();
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("版本检测超时，请检查工具是否可正常启动。");
                }
                // Version banners are small; cap the amount retained from unexpected output.
                try (var input = Files.newInputStream(output.path())) {
                    return new Result(process.exitValue(), decode(input.readNBytes(16384)));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("版本检测已中断。", exception);
            }
        }
    }

    static String decode(byte[] bytes) {
        // WSL list output is UTF-16LE on Windows; other tools normally emit UTF-8.
        boolean littleEndianBom = bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe;
        boolean wideAscii = bytes.length >= 4 && bytes[1] == 0 && bytes[3] == 0;
        if (littleEndianBom || wideAscii) {
            return new String(bytes, StandardCharsets.UTF_16LE).replace("\ufeff", "");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record OutputFile(Path path) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }

    private record RunningProcess(Process process) implements AutoCloseable {
        @Override public void close() throws IOException {
            if (!process.isAlive()) return;
            boolean interrupted = Thread.interrupted();
            try {
                // Maven runs through CMD on Windows; stop its children as well on timeout.
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                if (!process.waitFor(1, TimeUnit.SECONDS)) throw new IOException("无法终止版本检测进程。");
            } catch (InterruptedException exception) {
                interrupted = true;
                throw new IOException("等待版本检测进程退出时被中断。", exception);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }
}
