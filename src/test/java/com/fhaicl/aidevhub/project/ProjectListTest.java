package com.fhaicl.aidevhub.project;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProjectListTest {
    @Test
    void startsEmpty() {
        assertTrue(new ProjectList().projects().isEmpty());
    }

    @Test
    void addsNamedProjectWithAbsoluteNormalizedPath() {
        ProjectList list = new ProjectList();
        Path path = Path.of("projects", "unused", "..", "example");
        assertTrue(list.add(path));
        assertEquals(new Project("example", path.toAbsolutePath().normalize()), list.projects().getFirst());
    }

    @Test
    void rejectsDuplicateNormalizedPath() {
        ProjectList list = new ProjectList();
        assertTrue(list.add(Path.of("example")));
        assertFalse(list.add(Path.of("example", ".").toAbsolutePath()));
        assertEquals(1, list.projects().size());
    }

    @Test
    void removesOnlySelectedProjectWithoutDeletingDirectory(@TempDir Path directory) throws Exception {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        ProjectList list = new ProjectList();
        list.add(first);
        list.add(second);
        Project selected = list.projects().getFirst();
        assertTrue(list.remove(selected));
        assertFalse(list.remove(selected));
        assertEquals(1, list.projects().size());
        assertEquals(second, list.projects().getFirst().path());
        assertTrue(Files.isDirectory(first));
    }

    @Test
    void supportsRootDirectory() {
        ProjectList list = new ProjectList();
        Path root = Path.of("").toAbsolutePath().getRoot();
        assertTrue(list.add(root));
        assertEquals(root.toString(), list.projects().getFirst().name());
    }

    @Test
    void rejectsNullDirectory() {
        assertThrows(NullPointerException.class, () -> new ProjectList().add(null));
    }
}
