package com.fhaicl.aidevhub.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class IdePathSettingsTest {
    @TempDir
    Path directory;

    private Path config() { return directory.resolve("settings/ide-paths.xml"); }

    @Test
    void missingFileStartsUnconfiguredWithoutCreatingFile() throws IOException {
        IdePathSettings settings = new IdePathSettings(config());
        settings.load();
        for (var ide : IdePathSettings.Ide.values()) {
            assertNull(settings.path(ide));
            assertThrows(IOException.class, () -> settings.requirePath(ide));
        }
        assertFalse(Files.exists(config()));
    }

    @Test
    void savesAllPathsAndRestoresThemAfterRestart() throws IOException {
        Path installation = Files.createDirectory(directory.resolve("中文 IDE & tools"));
        Map<IdePathSettings.Ide, String> values = new EnumMap<>(IdePathSettings.Ide.class);
        for (var ide : IdePathSettings.Ide.values()) {
            values.put(ide, Files.createFile(installation.resolve(ide.executableNames()[0])).toString());
        }
        IdePathSettings settings = new IdePathSettings(config());
        settings.load();
        settings.save(values);
        IdePathSettings restored = new IdePathSettings(config());
        restored.load();
        for (var ide : IdePathSettings.Ide.values()) {
            assertEquals(Path.of(values.get(ide)), restored.requirePath(ide));
        }
    }

    @ParameterizedTest
    @EnumSource(IdePathSettings.Ide.class)
    void invalidUpdatePreservesDiskAndActiveConfiguration(IdePathSettings.Ide ide) throws IOException {
        Path executable = Files.createFile(directory.resolve(ide.executableNames()[0]));
        IdePathSettings settings = new IdePathSettings(config());
        settings.load();
        settings.save(Map.of(ide, executable.toString()));
        byte[] before = Files.readAllBytes(config());
        for (String invalid : new String[]{directory.toString(), directory.resolve("missing.exe").toString(),
                "relative.exe", "bad\0path"}) {
            assertThrows(IOException.class, () -> settings.save(Map.of(ide, invalid)));
            assertEquals(executable, settings.requirePath(ide));
            assertArrayEquals(before, Files.readAllBytes(config()));
        }
    }

    @Test
    void clearsSavedPathAndAcceptsAlternateIdeaExecutable() throws IOException {
        Path executable = Files.createFile(directory.resolve("idea.exe"));
        IdePathSettings settings = new IdePathSettings(config());
        settings.load();
        settings.save(Map.of(IdePathSettings.Ide.IDEA, executable.toString()));
        assertEquals(executable, settings.requirePath(IdePathSettings.Ide.IDEA));
        settings.save(Map.of(IdePathSettings.Ide.IDEA, "  "));
        IdePathSettings restored = new IdePathSettings(config());
        restored.load();
        assertNull(restored.path(IdePathSettings.Ide.IDEA));
        assertThrows(IOException.class, () -> restored.requirePath(IdePathSettings.Ide.IDEA));
    }

    @Test
    void corruptConfigurationCannotBeOverwritten() throws IOException {
        Files.createDirectories(config().getParent());
        Files.writeString(config(), "broken XML");
        IdePathSettings settings = new IdePathSettings(config());
        assertThrows(IOException.class, settings::load);
        assertThrows(IOException.class, () -> settings.save(Map.of()));
        assertThrows(IOException.class, () -> settings.requirePath(IdePathSettings.Ide.IDEA));
        assertEquals("broken XML", Files.readString(config()));
    }

    @Test
    void rejectsInvalidStoredPath() throws IOException {
        Files.createDirectories(config().getParent());
        Properties data = new Properties();
        data.setProperty("IDEA", "relative/idea.exe");
        try (var output = Files.newOutputStream(config())) {
            data.storeToXML(output, "test");
        }
        IdePathSettings settings = new IdePathSettings(config());
        assertThrows(IOException.class, settings::load);
        assertThrows(IOException.class, () -> settings.save(Map.of()));
    }

    @Test
    void staleExecutablePathRemainsVisibleAfterLoading() throws IOException {
        Path executable = Files.createFile(directory.resolve("Code.exe"));
        IdePathSettings settings = new IdePathSettings(config());
        settings.load();
        settings.save(Map.of(IdePathSettings.Ide.VS_CODE, executable.toString()));
        Files.delete(executable);
        IdePathSettings restored = new IdePathSettings(config());
        restored.load();
        assertEquals(executable, restored.path(IdePathSettings.Ide.VS_CODE));
    }

    @Test
    void failedDiskWriteDoesNotActivateNewPaths() throws IOException {
        // Use a missing parent for loading, then block it before saving.
        Path parent = directory.resolve("blocked");
        IdePathSettings settings = new IdePathSettings(parent.resolve("ide-paths.xml"));
        settings.load();
        Files.writeString(parent, "file");
        Path executable = Files.createFile(directory.resolve("Cursor.exe"));
        assertThrows(IOException.class,
                () -> settings.save(Map.of(IdePathSettings.Ide.CURSOR, executable.toString())));
        assertNull(settings.path(IdePathSettings.Ide.CURSOR));
        assertEquals("file", Files.readString(parent));
    }
}
