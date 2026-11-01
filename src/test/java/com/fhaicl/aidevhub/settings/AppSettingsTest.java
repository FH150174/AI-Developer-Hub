package com.fhaicl.aidevhub.settings;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AppSettingsTest {
    @TempDir Path directory;
    private Path file() { return directory.resolve("settings/settings.xml"); }

    @Test void defaultsToPowerShell7WithoutCreatingFile() throws IOException {
        AppSettings settings = new AppSettings(file());
        settings.load();
        assertEquals(AppSettings.Terminal.POWERSHELL_7, settings.values().terminal());
        assertEquals(AppSettings.Theme.LIGHT, settings.values().theme());
        assertEquals(AppSettings.StartPage.Dashboard, settings.values().startPage());
        assertEquals("", settings.values().projectDirectory());
        assertFalse(Files.exists(file()));
    }

    @Test void restoresEverySettingAndClearsDirectory() throws IOException {
        AppSettings settings = new AppSettings(file());
        settings.load();
        for (var terminal : AppSettings.Terminal.values()) {
            var expected = new AppSettings.Values(terminal, directory.toString(), AppSettings.Theme.DARK, AppSettings.StartPage.Projects);
            settings.save(expected);
            AppSettings restored = new AppSettings(file());
            restored.load();
            assertEquals(expected, restored.values());
        }
        settings.save(new AppSettings.Values(AppSettings.Terminal.POWERSHELL_7, "  ", AppSettings.Theme.LIGHT, AppSettings.StartPage.Dashboard));
        AppSettings restored = new AppSettings(file());
        restored.load();
        assertEquals("", restored.values().projectDirectory());
    }

    @Test void invalidDirectoryPreservesActiveAndSavedSettings() throws IOException {
        AppSettings settings = new AppSettings(file());
        settings.load();
        settings.save(settings.values());
        var before = settings.values();
        byte[] saved = Files.readAllBytes(file());
        for (String invalid : new String[]{"relative", directory.resolve("missing").toString(), "bad\0path"}) {
            assertThrows(IOException.class, () -> settings.save(new AppSettings.Values(AppSettings.Terminal.CMD, invalid, AppSettings.Theme.DARK, AppSettings.StartPage.Projects)));
            assertEquals(before, settings.values());
            assertArrayEquals(saved, Files.readAllBytes(file()));
        }
    }

    @Test void corruptFileCannotBeOverwritten() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "invalid XML");
        AppSettings settings = new AppSettings(file());
        assertThrows(IOException.class, settings::load);
        assertThrows(IOException.class, () -> settings.save(settings.values()));
        assertEquals("invalid XML", Files.readString(file()));
    }

    @Test void failedWriteDoesNotActivateSettings() throws IOException {
        AppSettings settings = new AppSettings(file());
        settings.load();
        var before = settings.values();
        Files.writeString(file().getParent(), "blocked");
        assertThrows(IOException.class, () -> settings.save(new AppSettings.Values(AppSettings.Terminal.CMD, "", AppSettings.Theme.DARK, AppSettings.StartPage.Projects)));
        assertEquals(before, settings.values());
    }
}
