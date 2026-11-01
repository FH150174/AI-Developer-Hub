package com.fhaicl.aidevhub.storage;

import com.fhaicl.aidevhub.agent.AgentPathSettings;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.project.ProjectStore;
import com.fhaicl.aidevhub.settings.AppSettings;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import com.fhaicl.aidevhub.todo.ProjectTodoStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class CorruptDataTest {
    enum Data { APP, IDE, AGENT, PROJECTS, NOTES, TODOS }
    @TempDir Path directory;
    @FunctionalInterface interface IoAction { void run() throws IOException; }
    record Fixture(Path file, IoAction load, IoAction save) { }

    @ParameterizedTest
    @EnumSource(Data.class)
    void rejectsMalformedXmlAndJsonWithoutChangingTheFile(Data data) throws IOException {
        Path root = Files.createDirectories(directory.resolve("中文 data with spaces"));
        Fixture fixture = fixture(data, root);
        for (String content : new String[]{"", "<properties><entry key=\"theme\">DARK", "{\"theme\":\"DARK\"", "{\"projects\":[]}"}) {
            Files.writeString(fixture.file(), content);
            byte[] original = Files.readAllBytes(fixture.file());
            IOException failure = assertThrows(IOException.class, fixture.load()::run);
            assertTrue(failure.getMessage().contains("损坏"));
            assertTrue(failure.getMessage().contains("XML"));
            assertTrue(failure.getMessage().contains("JSON"));
            assertTrue(failure.getMessage().contains(fixture.file().toString()));
            assertNotNull(failure.getCause());
            if (data == Data.APP || data == Data.IDE || data == Data.AGENT) {
                assertThrows(IOException.class, fixture.save()::run);
            }
            assertArrayEquals(original, Files.readAllBytes(fixture.file()));
        }
    }

    private Fixture fixture(Data data, Path root) throws IOException {
        Project project = new Project("中文 project", directory.resolve("项目 with spaces"));
        Fixture fixture = switch (data) {
            case APP -> {
                Path file = root.resolve("settings.xml");
                AppSettings settings = new AppSettings(file);
                settings.load();
                yield new Fixture(file, settings::load, () -> settings.save(settings.values()));
            }
            case IDE -> {
                Path file = root.resolve("ide-paths.xml");
                IdePathSettings settings = new IdePathSettings(file);
                settings.load();
                yield new Fixture(file, settings::load, () -> settings.save(Map.of()));
            }
            case AGENT -> {
                Path file = root.resolve("agent-paths.xml");
                AgentPathSettings settings = new AgentPathSettings(file);
                settings.load();
                yield new Fixture(file, settings::load, () -> settings.save(Map.of()));
            }
            case PROJECTS -> {
                Path file = root.resolve("projects.xml");
                ProjectStore store = new ProjectStore(file);
                yield new Fixture(file, store::load, () -> store.save(List.of(project)));
            }
            case NOTES -> {
                Path notes = root.resolve("notes");
                ProjectLogStore store = new ProjectLogStore(notes);
                store.save(project, List.of());
                yield new Fixture(onlyFile(notes), () -> store.load(project), () -> store.save(project, List.of()));
            }
            case TODOS -> {
                Path todos = root.resolve("todos");
                ProjectTodoStore store = new ProjectTodoStore(todos);
                store.save(project, List.of());
                yield new Fixture(onlyFile(todos), () -> store.load(project), () -> store.save(project, List.of()));
            }
        };
        fixture.save().run();
        return fixture;
    }

    private Path onlyFile(Path directory) throws IOException {
        try (var files = Files.list(directory)) { return files.findFirst().orElseThrow(); }
    }
}
