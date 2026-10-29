package com.fhaicl.aidevhub.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AgentLauncherTest {
    @TempDir Path directory;

    @Test
    void launchesAllFiveToolsWithFixedCommands() throws IOException {
        var expected = List.of("codex", "claude", "antigravity", "powershell", "wsl");
        int index = 0;
        for (var tool : AgentLauncher.Tool.values()) {
            AtomicReference<ProcessBuilder> started = new AtomicReference<>();
            var launcher = new AgentLauncher("Windows 11", directory.toString(), directory,
                    path -> true, started::set);
            launcher.launch(tool);
            List<String> command = tool == AgentLauncher.Tool.POWERSHELL
                    ? List.of("cmd.exe", "/d", "/c", "start", "\"\"", "powershell.exe", "-NoExit")
                    : List.of("cmd.exe", "/d", "/c", "start", "\"\"", "cmd.exe", "/d", "/k", expected.get(index));
            assertEquals(command, started.get().command());
            assertEquals(directory.toAbsolutePath().toFile(), started.get().directory());
            index++;
        }
    }

    @Test
    void preservesSpecialCharacterDirectoryOutsideShellCommand() throws IOException {
        Path home = Files.createDirectory(directory.resolve("中文 home & test %PATH%"));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        var launcher = new AgentLauncher("Windows 11", "\"" + directory + "\"", home,
                path -> path.equals(directory.resolve("codex.cmd")), started::set);
        launcher.launch(AgentLauncher.Tool.CODEX);
        assertEquals(home.toFile(), started.get().directory());
        assertFalse(started.get().command().toString().contains(home.toString()));
    }

    @Test
    void missingToolDoesNotStartAnything() {
        var launcher = new AgentLauncher("Windows 11", directory.toString(), directory,
                path -> false, builder -> fail("Must not launch missing tool"));
        IOException failure = assertThrows(IOException.class, () -> launcher.launch(AgentLauncher.Tool.CLAUDE));
        assertTrue(failure.getMessage().contains("PATH"));
        assertTrue(failure.getMessage().contains("Claude CLI"));
    }

    @Test
    void rejectsUnsupportedPlatformMissingDirectoryAndNullTool() {
        var linux = new AgentLauncher("Linux", "", directory, path -> true, builder -> fail("Must not start"));
        assertThrows(IOException.class, () -> linux.launch(AgentLauncher.Tool.WSL));
        var missing = new AgentLauncher("Windows 11", "", directory.resolve("missing"),
                path -> true, builder -> fail("Must not start"));
        assertThrows(IOException.class, () -> missing.launch(AgentLauncher.Tool.CODEX));
        assertThrows(NullPointerException.class, () -> missing.launch(null));
    }

    @Test
    void retainsStartupFailureCause() {
        IOException cause = new IOException("cannot create process");
        var launcher = new AgentLauncher("Windows 11", directory.toString(), directory,
                path -> true, builder -> { throw cause; });
        assertSame(cause, assertThrows(IOException.class,
                () -> launcher.launch(AgentLauncher.Tool.ANTIGRAVITY)).getCause());
    }

    @Test
    void emptyOrMalformedPathIsReportedWithoutStarting() {
        for (String value : new String[] {null, "", " ; ", "bad\0path"}) {
            var launcher = new AgentLauncher("Windows 11", value, directory,
                    path -> false, builder -> fail("Must not start"));
            assertThrows(IOException.class, () -> launcher.launch(AgentLauncher.Tool.CODEX));
        }
    }
}
