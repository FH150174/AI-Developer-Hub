package com.fhaicl.aidevhub.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GitCommandExecutorTest {
    @TempDir
    Path directory;

    @Test
    void executesActualGitWithoutRepository() throws IOException {
        var result = new GitCommandExecutor().execute("--version");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("git version "));
        assertEquals("", result.stderr());
    }

    @Test
    void preservesBothStreamsArgumentsAndCleansTemporaryFiles() throws IOException {
        Path[] files = new Path[2];
        var executor = new GitCommandExecutor(Duration.ofSeconds(1), builder -> {
            assertEquals(List.of("git", "status", "literal & space 中文"), builder.command());
            assertEquals(directory.toAbsolutePath().toFile(), builder.directory());
            assertEquals("C", builder.environment().get("LC_ALL"));
            assertEquals("0", builder.environment().get("GIT_OPTIONAL_LOCKS"));
            assertFalse(builder.environment().containsKey("GIT_DIR"));
            files[0] = builder.redirectOutput().file().toPath();
            files[1] = builder.redirectError().file().toPath();
            Files.writeString(files[0], " M 中文 file\0  \n");
            Files.writeString(files[1], "warning\n");
            return new StubProcess(7, false);
        });
        var result = executor.execute(directory, "status", "literal & space 中文");
        assertEquals(7, result.exitCode());
        assertEquals(" M 中文 file\0  \n", result.stdout());
        assertEquals("warning\n", result.stderr());
        assertFalse(Files.exists(files[0]));
        assertFalse(Files.exists(files[1]));
    }

    @Test
    void reportsStartupFailureAndCleansTemporaryFiles() {
        Path[] output = new Path[1];
        IOException cause = new IOException("missing executable");
        var executor = new GitCommandExecutor(Duration.ofSeconds(1), builder -> {
            output[0] = builder.redirectOutput().file().toPath();
            throw cause;
        });
        assertSame(cause, assertThrows(IOException.class, () -> executor.execute("--version")).getCause());
        assertFalse(Files.exists(output[0]));
    }

    @Test
    void terminatesProcessAfterTimeout() {
        StubProcess process = new StubProcess(0, true);
        var executor = new GitCommandExecutor(Duration.ofMillis(1), builder -> process);
        assertTrue(assertThrows(IOException.class, () -> executor.execute("--version")).getMessage().contains("超时"));
        assertTrue(process.destroyed);
        assertFalse(process.isAlive());
    }

    @Test
    void preservesInterruptFlagAndTerminatesProcess() {
        StubProcess process = new StubProcess(0, true);
        process.interrupt = true;
        var executor = new GitCommandExecutor(Duration.ofSeconds(1), builder -> process);
        try {
            assertTrue(assertThrows(IOException.class, () -> executor.execute("--version")).getCause()
                    instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(process.destroyed);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsEmptyArgumentsAndInvalidTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new GitCommandExecutor().execute(new String[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new GitCommandExecutor(Duration.ZERO, builder -> null));
    }

    @Test
    void invalidDirectoryIsReportedBeforeStartingGit() {
        var executor = new GitCommandExecutor(Duration.ofSeconds(1), builder -> {
            fail("Must not start Git for missing directory");
            return null;
        });
        GitException failure = assertThrows(GitException.class,
                () -> executor.execute(directory.resolve("missing"), "status"));
        assertEquals(GitException.Kind.INVALID_DIRECTORY, failure.kind());
    }

    @Test
    void cleanupFailureDoesNotReplaceTimeout() {
        StubProcess process = new StubProcess(0, true);
        process.refuseTermination = true;
        var executor = new GitCommandExecutor(Duration.ofMillis(1), builder -> process);
        GitException failure = assertThrows(GitException.class, () -> executor.execute("--version"));
        assertEquals(GitException.Kind.TIMEOUT, failure.kind());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("终止"));
    }

    private static final class StubProcess extends Process {
        private final int code;
        private boolean alive;
        private boolean destroyed;
        private boolean interrupt;
        private boolean refuseTermination;

        StubProcess(int code, boolean alive) {
            this.code = code;
            this.alive = alive;
        }

        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return code; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (interrupt) {
                interrupt = false;
                throw new InterruptedException("test interrupt");
            }
            return !alive;
        }
        @Override public int exitValue() { return code; }
        @Override public boolean isAlive() { return alive; }
        @Override public void destroy() { destroyed = true; alive = refuseTermination; }
        @Override public Process destroyForcibly() { destroy(); return this; }
    }
}
