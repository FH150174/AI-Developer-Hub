package com.fhaicl.aidevhub.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import com.fhaicl.aidevhub.project.Project;
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
            var launcher = new AgentLauncher("Windows 11", directory.toString(),
                    path -> true, started::set);
            launcher.launch(tool, new Project("project", directory));
            List<String> command = tool == AgentLauncher.Tool.POWERSHELL
                    ? List.of("cmd.exe", "/d", "/c", "start", "\"\"", "powershell.exe", "-NoExit")
                    : List.of("cmd.exe", "/d", "/c", "start", "\"\"", "cmd.exe", "/d", "/k", expected.get(index));
            if (tool == AgentLauncher.Tool.ANTIGRAVITY) {
                command = new java.util.ArrayList<>(command);
                command.add(".");
            }
            if (tool == AgentLauncher.Tool.CODEX || tool == AgentLauncher.Tool.CLAUDE) {
                command = new java.util.ArrayList<>(command);
                command.add(AgentLauncher.CONTEXT_PROMPT);
            }
            assertEquals(command, started.get().command());
            assertEquals("project", started.get().environment().get("AIDEVHUB_PROJECT_NAME"));
            assertEquals(directory.toAbsolutePath().toString(), started.get().environment().get("AIDEVHUB_PROJECT_PATH"));
            assertTrue(started.get().environment().get("AIDEVHUB_PROJECT_CONTEXT").contains(directory.toString()));
            if (tool == AgentLauncher.Tool.WSL) {
                assertTrue(started.get().environment().get("WSLENV").contains("AIDEVHUB_PROJECT_CONTEXT/u"));
            }
            assertEquals(directory.toAbsolutePath().toFile(), started.get().directory());
            index++;
        }
    }

    @Test
    void preservesSpecialCharacterDirectoryOutsideShellCommand() throws IOException {
        Path home = Files.createDirectory(directory.resolve("中文 home & test %PATH%"));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        var launcher = new AgentLauncher("Windows 11", "\"" + directory + "\"",
                path -> path.equals(directory.resolve("codex.cmd")), started::set);
        launcher.launch(AgentLauncher.Tool.CODEX, new Project("project", home));
        assertEquals(home.toFile(), started.get().directory());
        assertFalse(started.get().command().toString().contains(home.toString()));
    }

    @Test
    void missingToolDoesNotStartAnything() {
        var launcher = new AgentLauncher("Windows 11", directory.toString(),
                path -> false, builder -> fail("Must not launch missing tool"));
        IOException failure = assertThrows(IOException.class, () -> launcher.launch(AgentLauncher.Tool.CLAUDE, new Project("project", directory)));
        assertTrue(failure.getMessage().contains("PATH"));
        assertTrue(failure.getMessage().contains("Claude CLI"));
    }

    @Test
    void rejectsUnsupportedPlatformMissingDirectoryAndNullTool() {
        var linux = new AgentLauncher("Linux", "", path -> true, builder -> fail("Must not start"));
        assertThrows(IOException.class, () -> linux.launch(AgentLauncher.Tool.WSL, new Project("project", directory)));
        var missing = new AgentLauncher("Windows 11", "",
                path -> true, builder -> fail("Must not start"));
        assertThrows(IOException.class, () -> missing.launch(AgentLauncher.Tool.CODEX, new Project("project", directory.resolve("missing"))));
        assertThrows(NullPointerException.class, () -> missing.launch(null, new Project("project", directory)));
    }

    @Test
    void retainsStartupFailureCause() {
        IOException cause = new IOException("cannot create process");
        var launcher = new AgentLauncher("Windows 11", directory.toString(),
                path -> true, builder -> { throw cause; });
        assertSame(cause, assertThrows(IOException.class,
                () -> launcher.launch(AgentLauncher.Tool.ANTIGRAVITY, new Project("project", directory))).getCause());
    }

    @Test
    void emptyOrMalformedPathIsReportedWithoutStarting() {
        for (String value : new String[] {null, "", " ; ", "bad\0path"}) {
            var launcher = new AgentLauncher("Windows 11", value,
                    path -> false, builder -> fail("Must not start"));
            assertThrows(IOException.class, () -> launcher.launch(AgentLauncher.Tool.CODEX, new Project("project", directory)));
        }
    }

    @Test
    void rejectsUnselectedProjectAndRegularFileWithoutStarting() throws IOException {
        var launcher = new AgentLauncher("Windows 11", directory.toString(),
                path -> true, builder -> fail("Must not start"));
        assertTrue(assertThrows(IOException.class, () -> launcher.launch(AgentLauncher.Tool.CODEX, null))
                .getMessage().contains("未选择"));
        Path file = Files.createFile(directory.resolve("file.txt"));
        assertThrows(IOException.class, () -> launcher.launch(AgentLauncher.Tool.CODEX, new Project("file", file)));
    }

    @Test
    void switchingProjectsChangesWorkingDirectoryForNextLaunch() throws IOException {
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        var launcher = new AgentLauncher("Windows 11", directory.toString(), path -> true, started::set);
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        launcher.launch(AgentLauncher.Tool.CODEX, new Project("first", first.resolve(".")));
        assertEquals(first.toFile(), started.get().directory());
        launcher.launch(AgentLauncher.Tool.WSL, new Project("second", second));
        assertEquals(second.toFile(), started.get().directory());
    }

    @Test
    void contextIsDataOutsideShellCommandAndSwitchesWithProject() throws IOException {
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        var launcher = new AgentLauncher("Windows 11", directory.toString(), path -> true, started::set);
        String name = "中文 & echo bad %PATH% \" quoted";
        launcher.launch(AgentLauncher.Tool.CODEX, new Project(name, directory));
        assertTrue(started.get().environment().get("AIDEVHUB_PROJECT_CONTEXT").contains(name));
        assertFalse(started.get().command().toString().contains(name));
        Path other = Files.createDirectory(directory.resolve("other"));
        launcher.launch(AgentLauncher.Tool.CLAUDE, new Project("other", other));
        assertEquals("other", started.get().environment().get("AIDEVHUB_PROJECT_NAME"));
        assertFalse(started.get().environment().get("AIDEVHUB_PROJECT_CONTEXT").contains(name));
    }
}
