package com.fhaicl.aidevhub.environment;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class VersionCommandExecutorTest {
    @Test
    void mergesStreamsAndCleansOutputFile() throws IOException {
        Path[] output = new Path[1];
        var executor = new VersionCommandExecutor(Duration.ofSeconds(1), builder -> {
            assertEquals(List.of("java", "-version"), builder.command());
            assertTrue(builder.redirectErrorStream());
            output[0] = builder.redirectOutput().file().toPath();
            Files.writeString(output[0], "openjdk version \"21\"\n");
            return new StubProcess(false);
        });
        var result = executor.execute(List.of("java", "-version"));
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("21"));
        assertFalse(Files.exists(output[0]));
    }

    @Test
    void startupFailureStillDeletesTemporaryFile() {
        Path[] output = new Path[1];
        var executor = new VersionCommandExecutor(Duration.ofSeconds(1), builder -> {
            output[0] = builder.redirectOutput().file().toPath();
            throw new IOException("missing");
        });
        assertThrows(IOException.class, () -> executor.execute(List.of("missing")));
        assertFalse(Files.exists(output[0]));
    }

    @Test
    void timeoutTerminatesProcess() {
        var process = new StubProcess(true);
        var executor = new VersionCommandExecutor(Duration.ofMillis(1), builder -> process);
        assertTrue(assertThrows(IOException.class, () -> executor.execute(List.of("tool")))
                .getMessage().contains("超时"));
        assertTrue(process.destroyed);
    }

    @Test
    void interruptionTerminatesProcessAndPreservesFlag() {
        var process = new StubProcess(true);
        process.interrupt = true;
        var executor = new VersionCommandExecutor(Duration.ofSeconds(1), builder -> process);
        try {
            assertThrows(IOException.class, () -> executor.execute(List.of("tool")));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(process.destroyed);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void actualJavaStderrVersionIsCaptured() throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var result = new VersionCommandExecutor().execute(List.of(java.toString(), "-version"));
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("version"));
    }

    private static final class StubProcess extends Process {
        boolean alive;
        boolean destroyed;
        boolean interrupt;
        StubProcess(boolean alive) { this.alive = alive; }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (interrupt) { interrupt = false; throw new InterruptedException(); }
            return !alive;
        }
        @Override public int exitValue() { return 0; }
        @Override public boolean isAlive() { return alive; }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public void destroy() { destroyed = true; alive = false; }
        @Override public Process destroyForcibly() { destroy(); return this; }
    }
}
