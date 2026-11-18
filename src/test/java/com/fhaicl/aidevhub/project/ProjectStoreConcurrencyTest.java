package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ProjectStoreConcurrencyTest {
    private static final int CONFLICT = 2;
    @TempDir Path directory;

    @Test void twoProcessesCannotBothPublishEditsToTheSameSnapshot() throws Exception {
        ProjectStore store = new ProjectStore(directory.resolve("projects.xml"));
        Project a = new Project("A", directory.resolve("a"));
        Project b = new Project("B", directory.resolve("b"));
        Project c = new Project("C", directory.resolve("c"));
        store.save(List.of(), List.of(a));
        List<Project> snapshot = store.load();

        var child = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Writer.class.getName(), directory.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(directory.resolve("ready"))) {
                assertTrue(child.isAlive() && System.nanoTime() < deadline, "Child did not load its snapshot");
                Thread.sleep(10);
            }
            try (var start = child.getOutputStream()) {
                start.write(1);
            }
            boolean parentSaved;
            try {
                store.save(snapshot, List.of(a, b));
                parentSaved = true;
            } catch (ProjectStore.SaveConflictException conflict) {
                parentSaved = false;
            }
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Child did not finish saving");
            assertEquals(parentSaved ? CONFLICT : 0, child.exitValue());
            assertEquals(parentSaved ? List.of(a, b) : List.of(a, c), store.load());
        } finally {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
        }
    }

    public static final class Writer {
        public static void main(String[] args) throws IOException {
            Path directory = Path.of(args[0]);
            ProjectStore store = new ProjectStore(directory.resolve("projects.xml"));
            List<Project> snapshot = store.load();
            Files.createFile(directory.resolve("ready"));
            if (System.in.read() != 1) throw new IOException("Missing start signal");
            try {
                store.save(snapshot, List.of(snapshot.getFirst(), new Project("C", directory.resolve("c"))));
            } catch (ProjectStore.SaveConflictException conflict) {
                System.exit(CONFLICT);
            }
        }
    }
}
