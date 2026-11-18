package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreTest {
    @TempDir Path directory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"-1", "2147483647", "2147483648", "NaN", ""})
    void rejectsInvalidAndOversizedCountsWithoutChangingData(String count) throws IOException {
        Path file = directory.resolve("projects.xml");
        Properties properties = new Properties();
        properties.setProperty("count", count);
        write(file, properties);
        byte[] original = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> new ProjectStore(file).load());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void missingFileLoadsEmptyWithoutCreatingFile() throws IOException {
        Path file = directory.resolve("projects.xml");
        assertTrue(new ProjectStore(file).load().isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void restoresNamesPathsAndOrderAndPersistsChanges() throws IOException {
        Path file = directory.resolve("nested/projects.xml");
        ProjectStore store = new ProjectStore(file);
        Project first = new Project("中文 & <项目>", directory.resolve("项目 空格"));
        Project second = new Project("Second", directory.resolve("second"));
        store.save(List.of(), List.of(first, second));
        assertEquals(List.of(first, second), new ProjectStore(file).load());
        Project renamed = new Project("Renamed", first.path());
        store.save(List.of(first, second), List.of(renamed));
        assertEquals(List.of(renamed), store.load());
        store.save(List.of(renamed), List.of());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void staleWindowCannotOverwriteAnotherWindowAndCanRetryAfterReload() throws IOException {
        Path file = directory.resolve("projects.xml");
        ProjectStore first = new ProjectStore(file);
        ProjectStore second = new ProjectStore(file);
        Project a = new Project("A", directory.resolve("a"));
        Project b = new Project("B", directory.resolve("b"));
        Project c = new Project("C", directory.resolve("c"));
        first.save(List.of(), List.of(a));
        var firstSnapshot = first.load();
        var secondSnapshot = second.load();
        first.save(firstSnapshot, List.of(a, b));
        byte[] saved = Files.readAllBytes(file);

        var conflict = assertThrows(ProjectStore.SaveConflictException.class,
                () -> second.save(secondSnapshot, List.of(a, c)));
        assertTrue(conflict.getMessage().contains("重新加载"));
        assertArrayEquals(saved, Files.readAllBytes(file));
        var refreshed = second.load();
        assertEquals(List.of(a, b), refreshed);
        second.save(refreshed, List.of(a, b, c));
        assertEquals(List.of(a, b, c), first.load());
    }

    @Test
    void busyWriterDoesNotBlockOrChangeDataAndLockReleaseAllowsRetry() throws IOException {
        Path file = directory.resolve("projects.xml");
        ProjectStore store = new ProjectStore(file);
        Project a = new Project("A", directory.resolve("a"));
        store.save(List.of(), List.of(a));
        byte[] original = Files.readAllBytes(file);
        try (var channel = FileChannel.open(directory.resolve("projects.xml.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertThrows(ProjectStore.SaveConflictException.class, () -> store.save(List.of(a), List.of()));
            assertArrayEquals(original, Files.readAllBytes(file));
        }
        store.save(List.of(a), List.of());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void corruptXmlReportsFailureWithoutChangingFile() throws IOException {
        Path file = directory.resolve("projects.xml");
        Files.writeString(file, "broken XML");
        assertThrows(IOException.class, () -> new ProjectStore(file).load());
        assertEquals("broken XML", Files.readString(file));
    }

    @Test
    void invalidFieldsAndDuplicatePathsAreRejected() throws IOException {
        Path file = directory.resolve("projects.xml");
        Properties data = new Properties();
        data.setProperty("count", "1");
        data.setProperty("0.name", "Example");
        data.setProperty("0.path", "relative");
        write(file, data);
        assertThrows(IOException.class, () -> new ProjectStore(file).load());
        data.setProperty("0.path", directory.toString());
        data.setProperty("0.name", " ");
        write(file, data);
        assertThrows(IOException.class, () -> new ProjectStore(file).load());
        data.setProperty("0.name", "Example");
        data.setProperty("count", "2");
        data.setProperty("1.name", "Duplicate");
        data.setProperty("1.path", directory.toString());
        write(file, data);
        assertThrows(IOException.class, () -> new ProjectStore(file).load());
        data.remove("1.path");
        write(file, data);
        assertThrows(IOException.class, () -> new ProjectStore(file).load());
    }

    @Test
    void writeFailureLeavesExistingTargetUntouched() throws IOException {
        Path file = Files.createDirectory(directory.resolve("projects.xml"));
        Path marker = Files.writeString(file.resolve("marker"), "keep");
        assertThrows(IOException.class, () -> new ProjectStore(file).save(List.of(), List.of()));
        assertEquals("keep", Files.readString(marker));
        try (var files = Files.list(directory)) {
            assertEquals(java.util.Set.of(file, directory.resolve("projects.xml.lock")),
                    files.collect(java.util.stream.Collectors.toSet()));
        }
    }

    private void write(Path file, Properties data) throws IOException {
        try (var output = Files.newOutputStream(file)) {
            data.storeToXML(output, "test");
        }
    }
}
