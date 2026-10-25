package com.fhaicl.aidevhub.project;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectTest {

    @Test
    void preservesProvidedNameAndPath() {
        Path path = Path.of("projects", "example");
        Project project = new Project(" Example ", path);

        assertEquals(" Example ", project.name());
        assertEquals(path, project.path());
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class,
                () -> new Project(null, Path.of("example")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void rejectsBlankName(String name) {
        assertThrows(IllegalArgumentException.class,
                () -> new Project(name, Path.of("example")));
    }

    @Test
    void rejectsNullPath() {
        assertThrows(NullPointerException.class,
                () -> new Project("Example", null));
    }
}
