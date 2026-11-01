package com.fhaicl.aidevhub.settings;

import com.fhaicl.aidevhub.agent.AgentLauncher;
import com.fhaicl.aidevhub.agent.AgentPathSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class SettingsIntegrationTest {
    @TempDir Path directory;

    @Test void corruptedAgentConfigurationIsIsolatedAndCanBeRestoredFromBackup() throws IOException {
        Path root = Files.createDirectory(directory.resolve("中文 settings with spaces"));
        AppSettings app = new AppSettings(root.resolve("settings.xml"));
        app.load();
        app.save(new AppSettings.Values(AppSettings.Terminal.POWERSHELL_7, directory.toString(), AppSettings.Theme.DARK, AppSettings.StartPage.Projects));
        IdePathSettings ide = new IdePathSettings(root.resolve("ide-paths.xml"));
        ide.load();
        Path executable = Files.createFile(root.resolve("Code.exe"));
        ide.save(Map.of(IdePathSettings.Ide.VS_CODE, executable.toString()));
        Path agentFile = root.resolve("agent-paths.xml");
        AgentPathSettings agent = new AgentPathSettings(agentFile);
        agent.load();
        agent.save(Map.of(), Map.of(AgentLauncher.Tool.CODEX, "codex --help"));
        byte[] backup = Files.readAllBytes(agentFile);
        Files.writeString(agentFile, "{broken JSON");
        AgentPathSettings broken = new AgentPathSettings(agentFile);
        assertThrows(IOException.class, broken::load);
        assertThrows(IOException.class, () -> broken.save(Map.of()));
        AppSettings restoredApp = new AppSettings(root.resolve("settings.xml"));
        restoredApp.load();
        assertEquals(app.values(), restoredApp.values());
        IdePathSettings restoredIde = new IdePathSettings(root.resolve("ide-paths.xml"));
        restoredIde.load();
        assertEquals(executable, restoredIde.requirePath(IdePathSettings.Ide.VS_CODE));
        assertEquals("{broken JSON", Files.readString(agentFile));
        Files.write(agentFile, backup);
        AgentPathSettings repaired = new AgentPathSettings(agentFile);
        repaired.load();
        assertEquals("codex --help", repaired.requireCommand(AgentLauncher.Tool.CODEX));
        repaired.save(Map.of(), Map.of(AgentLauncher.Tool.CODEX, "codex"));
        restoredApp.load();
        restoredIde.load();
        assertEquals(app.values(), restoredApp.values());
        assertEquals(executable, restoredIde.requirePath(IdePathSettings.Ide.VS_CODE));
    }
}
