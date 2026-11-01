package com.fhaicl.aidevhub.process;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Redirected output whose Windows handles may take a moment to release after process termination. */
public final class TemporaryProcessOutput implements AutoCloseable {
    private final Path path;

    private TemporaryProcessOutput(Path path) { this.path = path; }
    public Path path() { return path; }

    public static TemporaryProcessOutput create(String prefix) throws IOException {
        return new TemporaryProcessOutput(Files.createTempFile(prefix, ".txt"));
    }

    @Override public void close() throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    Files.deleteIfExists(path);
                    return;
                } catch (FileSystemException failure) {
                    // Bound cleanup latency; permanent permission failures remain visible to the caller.
                    if (attempt == 10) throw failure;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interruption) {
                        interrupted = true;
                        failure.addSuppressed(interruption);
                        throw failure;
                    }
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
