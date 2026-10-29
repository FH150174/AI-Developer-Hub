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

    @Test
    void configuredExecutableOverridesPathAndSavedChangesApplyImmediately() throws IOException {
        var settings = new AgentPathSettings(directory.resolve("settings/agent-paths.xml"));
        settings.load();
        Path install = Files.createDirectories(directory.resolve("中文 tools space"));
        Path executable = Files.createFile(install.resolve("codex.cmd"));
        settings.save(java.util.Map.of(AgentLauncher.Tool.CODEX, executable.toString()));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        var launcher = new AgentLauncher("Windows 11", "", path -> false, started::set, settings);
        launcher.launch(AgentLauncher.Tool.CODEX, new Project("project", directory));
        assertTrue(started.get().command().contains("\"" + executable + "\""));
        assertTrue(started.get().environment().containsKey("AIDEVHUB_PROJECT_CONTEXT"));
        settings.save(java.util.Map.of());
        assertThrows(IOException.class, () -> launcher.launch(AgentLauncher.Tool.CODEX, new Project("project", directory)));
    }

    @Test
    void staleConfigurationFailsRatherThanUsingAnotherPathExecutable() throws IOException {
        var settings = new AgentPathSettings(directory.resolve("agent-paths.xml"));
        settings.load();
        Path executable = Files.createFile(directory.resolve("claude.exe"));
        settings.save(java.util.Map.of(AgentLauncher.Tool.CLAUDE, executable.toString()));
        Files.delete(executable);
        var launcher = new AgentLauncher("Windows 11", directory.toString(), path -> true,
                builder -> fail("Must not launch stale configuration"), settings);
        assertTrue(assertThrows(IOException.class,
                () -> launcher.launch(AgentLauncher.Tool.CLAUDE, new Project("project", directory)))
                .getMessage().contains("配置路径"));
    }

    @Test
    void customCommandOverridesStalePathAndDoesNotEnterOuterShellCommand() throws IOException {
        var settings = new AgentPathSettings(directory.resolve("agent-paths.xml"));
        settings.load();
        Path executable = Files.createFile(directory.resolve("codex.exe"));
        String custom = "echo \"%AIDEVHUB_PROJECT_PATH%\" & echo context";
        settings.save(java.util.Map.of(AgentLauncher.Tool.CODEX, executable.toString()),
                java.util.Map.of(AgentLauncher.Tool.CODEX, custom));
        Files.delete(executable);
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        var launcher = new AgentLauncher("Windows 11", "", path -> false, started::set, settings);
        launcher.launch(AgentLauncher.Tool.CODEX, new Project("中文 & name", directory));
        assertEquals(custom, started.get().environment().get("AIDEVHUB_LAUNCH_COMMAND"));
        assertFalse(started.get().command().toString().contains(custom));
        assertTrue(started.get().command().contains("-EncodedCommand"));
        String script = new String(java.util.Base64.getDecoder().decode(started.get().command().getLast()),
                java.nio.charset.StandardCharsets.UTF_16LE);
        assertEquals(AgentLauncher.CUSTOM_COMMAND_SCRIPT, script);
        assertEquals(directory.toFile(), started.get().directory());
        assertTrue(started.get().environment().get("AIDEVHUB_PROJECT_CONTEXT").contains("中文 & name"));
    }

    @Test
    void clearingCustomCommandRestoresDefaultPromptAndPathLookup() throws IOException {
        var settings = new AgentPathSettings(directory.resolve("agent-paths.xml"));
        settings.load();
        settings.save(java.util.Map.of(), java.util.Map.of(AgentLauncher.Tool.CLAUDE, "echo custom"));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();
        var launcher = new AgentLauncher("Windows 11", directory.toString(), path -> true, started::set, settings);
        launcher.launch(AgentLauncher.Tool.CLAUDE, new Project("project", directory));
        assertTrue(started.get().environment().containsKey("AIDEVHUB_LAUNCH_COMMAND"));
        settings.save(java.util.Map.of(), java.util.Map.of());
        launcher.launch(AgentLauncher.Tool.CLAUDE, new Project("project", directory));
        assertTrue(started.get().command().contains(AgentLauncher.CONTEXT_PROMPT));
        assertFalse(started.get().environment().containsKey("AIDEVHUB_LAUNCH_COMMAND"));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void customBootstrapPassesCmdSyntaxAndProjectEnvironmentToRealShell() throws Exception {
        String script = AgentLauncher.CUSTOM_COMMAND_SCRIPT.replace("/k", "/c");
        String encoded = java.util.Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
        Path output = directory.resolve("output.txt");
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-EncodedCommand", encoded);
        builder.directory(directory.toFile());
        builder.environment().put("AIDEVHUB_LAUNCH_COMMAND", "echo FIRST & echo \"%AIDEVHUB_PROJECT_PATH%\"");
        builder.environment().put("AIDEVHUB_PROJECT_PATH", directory.toString());
        builder.redirectErrorStream(true).redirectOutput(output.toFile());
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            String text = Files.readString(output, java.nio.charset.Charset.defaultCharset());
            assertTrue(text.contains("FIRST"), text);
            assertTrue(text.contains(directory.toString()), text);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
