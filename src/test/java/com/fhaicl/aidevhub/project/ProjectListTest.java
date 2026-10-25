package com.fhaicl.aidevhub.project;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ProjectListTest {
    @Test
    void renamesOnlySelectedProjectAndPreservesPathAndOrder() {
        ProjectList list = new ProjectList();
        list.add(Path.of("first"));
        list.add(Path.of("second"));
        Project selected = list.projects().getFirst();
        Project other = list.projects().getLast();

        Project renamed = list.rename(selected, " \u2003New name\t ");

        assertEquals(new Project("New name", selected.path()), renamed);
        assertEquals(java.util.List.of(renamed, other), list.projects());
        assertFalse(list.add(selected.path()));
        assertTrue(list.remove(renamed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void rejectsBlankRenameWithoutChangingList(String name) {
        ProjectList list = new ProjectList();
        list.add(Path.of("example"));
        var before = list.projects();
        assertThrows(IllegalArgumentException.class, () -> list.rename(before.getFirst(), name));
        assertEquals(before, list.projects());
    }

    @Test
    void allowsSameNameAndUnchangedName() {
        ProjectList list = new ProjectList();
        list.add(Path.of("first"));
        list.add(Path.of("second"));
        Project first = list.projects().getFirst();
        assertEquals(first, list.rename(first, first.name()));
        list.rename(first, "second");
        assertEquals(2, list.projects().size());
        assertEquals("second", list.projects().getFirst().name());
    }

    @Test
    void rejectsMissingProjectAndNullNameWithoutChangingList() {
        ProjectList list = new ProjectList();
        list.add(Path.of("example"));
        var before = list.projects();
        assertThrows(NullPointerException.class, () -> list.rename(before.getFirst(), null));
        assertThrows(IllegalArgumentException.class,
                () -> list.rename(new Project("Missing", Path.of("missing")), "New"));
        assertEquals(before, list.projects());
    }

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
