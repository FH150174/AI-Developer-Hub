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
        var failure = assertThrows(CommandExecutionException.class, () -> executor.execute(List.of("missing")));
        assertEquals(CommandExecutionException.Kind.START_FAILED, failure.kind());
        assertTrue(failure.getMessage().contains("PATH"));
        assertFalse(Files.exists(output[0]));
    }

    @Test
    void timeoutTerminatesProcess() {
        var process = new StubProcess(true);
        var executor = new VersionCommandExecutor(Duration.ofMillis(1), builder -> process);
        var failure = assertThrows(CommandExecutionException.class, () -> executor.execute(List.of("tool")));
        assertEquals(CommandExecutionException.Kind.TIMEOUT, failure.kind());
        assertTrue(failure.getMessage().contains("超时"));
        assertTrue(process.destroyed);
        assertTrue(process.childDestroyed.get());
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
    @org.junit.jupiter.api.Tag("integration")
    void actualJavaStderrVersionIsCaptured() throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var result = new VersionCommandExecutor().execute(List.of(java.toString(), "-version"));
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("version"));
    }

    @Test
    @org.junit.jupiter.api.Tag("integration")
    void realLongRunningCommandIsBoundedAndReleasesOutput() throws Exception {
        Process[] started = new Process[1];
        Path[] output = new Path[1];
        var executor = new VersionCommandExecutor(Duration.ofMillis(700), builder -> {
            output[0] = builder.redirectOutput().file().toPath();
            started[0] = builder.start();
            return started[0];
        });
        try {
            assertTimeout(Duration.ofSeconds(5), () -> {
                var failure = assertThrows(CommandExecutionException.class,
                        () -> executor.execute(com.fhaicl.aidevhub.support.SlowCommand.command()));
                assertEquals(CommandExecutionException.Kind.TIMEOUT, failure.kind());
            });
            assertFalse(started[0].isAlive());
            assertFalse(Files.exists(output[0]));
        } finally {
            if (started[0] != null && started[0].isAlive()) started[0].destroyForcibly();
        }
    }

    private static final class StubProcess extends Process {
        boolean alive;
        boolean destroyed;
        boolean interrupt;
        private final java.util.concurrent.atomic.AtomicBoolean childDestroyed = new java.util.concurrent.atomic.AtomicBoolean();
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
        @Override public Stream<ProcessHandle> descendants() {
            return Stream.of(com.fhaicl.aidevhub.support.SlowCommand.child(childDestroyed));
        }
        @Override public void destroy() { destroyed = true; alive = false; }
        @Override public Process destroyForcibly() { destroy(); return this; }
    }
}
