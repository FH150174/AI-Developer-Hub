package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreTest {
    @TempDir Path directory;

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
        store.save(List.of(first, second));
        assertEquals(List.of(first, second), new ProjectStore(file).load());
        Project renamed = new Project("Renamed", first.path());
        store.save(List.of(renamed));
        assertEquals(List.of(renamed), store.load());
        store.save(List.of());
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
        assertThrows(IOException.class, () -> new ProjectStore(file).save(List.of()));
        assertEquals("keep", Files.readString(marker));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    private void write(Path file, Properties data) throws IOException {
        try (var output = Files.newOutputStream(file)) {
            data.storeToXML(output, "test");
        }
    }
}
