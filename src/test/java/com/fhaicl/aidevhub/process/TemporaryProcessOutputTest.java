package com.fhaicl.aidevhub.process;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
@org.junit.jupiter.api.Tag("integration")
class TemporaryProcessOutputTest {
    @Test void retriesTransientFileLockAndPreservesExistingInterruptFlag() throws Exception {
        TemporaryProcessOutput output = TemporaryProcessOutput.create("aidevhub-cleanup-test-");
        try (var locked = new FileOutputStream(output.path().toFile());
             var executor = Executors.newSingleThreadScheduledExecutor()) {
            var release = executor.schedule(() -> { locked.close(); return null; }, 100, TimeUnit.MILLISECONDS);
            try {
                Thread.currentThread().interrupt();
                output.close();
                assertFalse(Files.exists(output.path()));
                assertTrue(Thread.interrupted());
                release.get(1, TimeUnit.SECONDS);
            } finally {
                Thread.interrupted();
            }
        } finally {
            output.close();
        }
    }

    @Test void reportsPersistentLockWithinBoundedTimeAndCanBeRetried() throws IOException {
        TemporaryProcessOutput output = TemporaryProcessOutput.create("aidevhub-cleanup-test-");
        try {
            try (var locked = new FileOutputStream(output.path().toFile())) {
                assertTimeout(Duration.ofSeconds(2), () -> assertThrows(IOException.class, output::close));
                assertTrue(Files.exists(output.path()));
            }
        } finally {
            output.close();
        }
        assertFalse(Files.exists(output.path()));
    }
}
