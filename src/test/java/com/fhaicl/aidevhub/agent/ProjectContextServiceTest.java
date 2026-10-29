package com.fhaicl.aidevhub.agent;

import com.fhaicl.aidevhub.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProjectContextServiceTest {
    @TempDir Path directory;
    private final ProjectContextService service = new ProjectContextService();

    @Test
    void includesProjectAndOnlyExistingKnownDocumentsWithoutChangingFiles() throws IOException {
        Path rules = directory.resolve("AGENTS.md");
        Files.writeString(rules, "existing rules");
        Files.createDirectory(directory.resolve("docs"));
        Files.writeString(directory.resolve("docs/REQUIREMENTS.md"), "requirements");
        Files.writeString(directory.resolve("secret.env"), "private value");
        String context = service.context(new Project("中文 project", directory.resolve(".")));
        assertTrue(context.contains("中文 project"));
        assertTrue(context.contains(directory.toAbsolutePath().toString()));
        assertTrue(context.contains("- AGENTS.md"));
        assertTrue(context.contains("- docs/REQUIREMENTS.md"));
        assertFalse(context.contains("- README.md"));
        assertFalse(context.contains("secret.env"));
        assertFalse(context.contains("private value"));
        assertEquals("existing rules", Files.readString(rules));
        try (var files = Files.list(directory)) { assertEquals(3, files.count()); }
    }

    @Test
    void handlesProjectWithoutDocumentationAndRejectsInvalidDirectory() throws IOException {
        String context = service.context(new Project("empty", directory));
        assertTrue(context.contains("未找到约定文档"));
        assertThrows(IOException.class, () -> service.context(new Project("missing", directory.resolve("missing"))));
        Path file = Files.createFile(directory.resolve("file"));
        assertThrows(IOException.class, () -> service.context(new Project("file", file)));
    }
}
