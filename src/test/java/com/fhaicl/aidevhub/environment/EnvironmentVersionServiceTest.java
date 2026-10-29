package com.fhaicl.aidevhub.environment;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentVersionServiceTest {
    @Test
    void recognizesAllFiveVersionBanners() throws IOException {
        var banners = Map.of(
                EnvironmentVersionService.Tool.JAVA, "openjdk version \"21.0.12\" 2026-07-21\nOpenJDK Runtime Environment\n",
                EnvironmentVersionService.Tool.MAVEN, "\u001b[1mApache Maven 3.9.9\u001b[0m\r\nMaven home: private path\r\n",
                EnvironmentVersionService.Tool.GIT, "git version 2.49.0.windows.1\n",
                EnvironmentVersionService.Tool.NODE, "v22.14.0\n",
                EnvironmentVersionService.Tool.PYTHON, "Python 3.13.2\n");
        for (var entry : banners.entrySet()) {
            var service = new EnvironmentVersionService(command ->
                    new VersionCommandExecutor.Result(0, entry.getValue()), "Windows 11");
            String version = service.version(entry.getKey());
            assertFalse(version.isBlank());
            assertFalse(version.contains("\n"));
            assertFalse(version.contains("\u001b"));
            assertFalse(version.contains("private path"));
        }
    }

    @Test
    void usesFixedWindowsMavenCommandAndDirectCommandsForOtherTools() throws IOException {
        var service = new EnvironmentVersionService(command -> {
            assertEquals(List.of("cmd.exe", "/d", "/c", "mvn.cmd", "--version"), command);
            return new VersionCommandExecutor.Result(0, "Apache Maven 3.9.9");
        }, "Windows 11");
        service.version(EnvironmentVersionService.Tool.MAVEN);
        var direct = new EnvironmentVersionService(command -> {
            assertEquals(List.of("java", "-version"), command);
            return new VersionCommandExecutor.Result(0, "java version \"21.0.1\"");
        }, "Windows 11");
        direct.version(EnvironmentVersionService.Tool.JAVA);
        var unix = new EnvironmentVersionService(command -> {
            assertEquals(List.of("mvn", "--version"), command);
            return new VersionCommandExecutor.Result(0, "Apache Maven 3.9.9");
        }, "Linux");
        unix.version(EnvironmentVersionService.Tool.MAVEN);
    }

    @Test
    void rejectsFailureEmptyAndUnexpectedOutputWithoutExposingCommandOutput() {
        for (var result : List.of(new VersionCommandExecutor.Result(1, "private diagnostics"),
                new VersionCommandExecutor.Result(0, ""),
                new VersionCommandExecutor.Result(0, "Python was not found; open Microsoft Store"))) {
            var service = new EnvironmentVersionService(command -> result, "Windows 11");
            IOException failure = assertThrows(IOException.class,
                    () -> service.version(EnvironmentVersionService.Tool.PYTHON));
            assertFalse(failure.getMessage().contains("private diagnostics"));
            assertTrue(failure.getMessage().contains("Python"));
        }
    }

    @Test
    void retainsStartupCauseAndOtherToolsCanStillBeRead() throws IOException {
        IOException cause = new IOException("missing executable");
        var service = new EnvironmentVersionService(command -> {
            if (command.getFirst().equals("node")) throw cause;
            return new VersionCommandExecutor.Result(0, "git version 2.49.0");
        }, "Windows 11");
        assertSame(cause, assertThrows(IOException.class,
                () -> service.version(EnvironmentVersionService.Tool.NODE)).getCause());
        assertEquals("git version 2.49.0", service.version(EnvironmentVersionService.Tool.GIT));
    }
}
