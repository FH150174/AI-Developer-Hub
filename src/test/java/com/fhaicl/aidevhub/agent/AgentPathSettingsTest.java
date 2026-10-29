package com.fhaicl.aidevhub.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentPathSettingsTest {
    @TempDir Path directory;

    @Test
    void defaultsToPathAndPersistsAllToolsThenClears() throws IOException {
        Path file = directory.resolve("config/agent-paths.xml");
        var settings = new AgentPathSettings(file);
        settings.load();
        assertNull(settings.requirePath(AgentLauncher.Tool.CODEX));
        var values = new java.util.EnumMap<AgentLauncher.Tool, String>(AgentLauncher.Tool.class);
        for (var tool : AgentLauncher.Tool.values()) {
            Path executable = Files.createFile(directory.resolve(AgentPathSettings.executableNames(tool)[0]));
            values.put(tool, executable.toString());
        }
        settings.save(values);
        var restored = new AgentPathSettings(file);
        restored.load();
        for (var tool : AgentLauncher.Tool.values()) assertEquals(Path.of(values.get(tool)), restored.requirePath(tool));
        restored.save(Map.of());
        restored.load();
        for (var tool : AgentLauncher.Tool.values()) assertNull(restored.requirePath(tool));
    }

    @Test
    void invalidSavePreservesExistingConfig() throws IOException {
        Path file = directory.resolve("agent-paths.xml");
        Path executable = Files.createFile(directory.resolve("codex.cmd"));
        var settings = new AgentPathSettings(file);
        settings.load();
        settings.save(Map.of(AgentLauncher.Tool.CODEX, executable.toString()));
        String before = Files.readString(file);
        for (String bad : new String[] {"codex.cmd", directory.resolve("missing/claude.exe").toString(), executable.toString()}) {
            assertThrows(IOException.class, () -> settings.save(Map.of(AgentLauncher.Tool.CLAUDE, bad)));
            assertEquals(before, Files.readString(file));
            assertEquals(executable, settings.requirePath(AgentLauncher.Tool.CODEX));
        }
    }

    @Test
    void corruptConfigBlocksSaveAndLaunchLookupWithoutOverwriting() throws IOException {
        Path file = directory.resolve("agent-paths.xml");
        Files.writeString(file, "broken XML");
        var settings = new AgentPathSettings(file);
        assertThrows(IOException.class, settings::load);
        assertThrows(IOException.class, () -> settings.save(Map.of()));
        assertThrows(IOException.class, () -> settings.requirePath(AgentLauncher.Tool.CODEX));
        assertEquals("broken XML", Files.readString(file));
    }

    @Test
    void stalePathIsRestoredForRepairAndSpecialCharactersAreRejected() throws IOException {
        Path file = directory.resolve("agent-paths.xml");
        Path executable = Files.createFile(directory.resolve("codex.exe"));
        var settings = new AgentPathSettings(file);
        settings.load();
        settings.save(Map.of(AgentLauncher.Tool.CODEX, executable.toString()));
        Files.delete(executable);
        settings.load();
        assertEquals(executable, settings.requirePath(AgentLauncher.Tool.CODEX));
        Path unsafe = Files.createDirectories(directory.resolve("tools %PATH% & test")).resolve("codex.cmd");
        Files.createFile(unsafe);
        assertThrows(IOException.class, () -> settings.save(Map.of(AgentLauncher.Tool.CODEX, unsafe.toString())));
    }
}
