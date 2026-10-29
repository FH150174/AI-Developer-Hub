package com.fhaicl.aidevhub.environment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentDiagnosticsServiceTest {
    @TempDir Path directory;

    private EnvironmentDiagnosticsService service() {
        return new EnvironmentDiagnosticsService(command -> { throw new IOException("not expected"); }, "Windows 11");
    }

    @Test
    void reportsConfiguredMissingAndUnconfiguredIdeFiles() throws IOException {
        Path executable = Files.createFile(directory.resolve("idea64.exe"));
        assertTrue(service().idePath(executable).contains("文件存在"));
        Files.delete(executable);
        assertTrue(service().idePath(executable).contains("文件不存在"));
        assertTrue(service().idePath(null).contains("未配置"));
        assertTrue(service().idePath(directory).contains("文件不存在"));
    }

    @Test
    void checksPathEntriesAndFindsFirstToolWithoutExecutingIt() throws IOException {
        Path first = Files.createDirectory(directory.resolve("first space 中文"));
        Path second = Files.createDirectory(directory.resolve("second"));
        Files.writeString(first.resolve("java.exe"), "must not execute");
        Files.writeString(second.resolve("java.exe"), "must not execute");
        String report = service().checkPath("\"" + first + "\";" + second + ";" + first
                + ";;" + directory.resolve("missing") + ";bad\0path");
        assertTrue(report.contains("重复"));
        assertTrue(report.contains("空目录项"));
        assertTrue(report.contains("目录不存在"));
        assertTrue(report.contains("格式无效"));
        assertTrue(report.contains("java.exe：" + first.resolve("java.exe")));
        assertTrue(report.contains("python.exe：未找到"));
        assertTrue(service().checkPath(null).contains("为空"));
        assertTrue(service().checkPath("").contains("为空"));
    }

    @Test
    void wslListsInstalledAndRunningDistributionsUsingReadOnlyCommands() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        var service = new EnvironmentDiagnosticsService(command -> {
            if (calls.getAndIncrement() == 0) {
                assertEquals(List.of("wsl.exe", "--list", "--quiet"), command);
                return new VersionCommandExecutor.Result(0, "Ubuntu\r\nDebian\r\n");
            }
            assertEquals(List.of("wsl.exe", "--list", "--running", "--quiet"), command);
            return new VersionCommandExecutor.Result(0, "Ubuntu\r\n");
        }, "Windows 11");
        String status = service.wslStatus();
        assertTrue(status.contains("已注册发行版：Ubuntu、Debian"));
        assertTrue(status.contains("运行中的发行版：Ubuntu"));
        assertEquals(2, calls.get());
    }

    @Test
    void handlesNoDistributionsAndNoRunningDistributions() throws IOException {
        var empty = new EnvironmentDiagnosticsService(command -> new VersionCommandExecutor.Result(0, "\r\n"), "Windows 11");
        assertTrue(empty.wslStatus().contains("暂无已注册"));
        var stopped = new EnvironmentDiagnosticsService(command ->
                new VersionCommandExecutor.Result(0, command.contains("--running") ? "" : "Ubuntu"), "Windows 11");
        assertTrue(stopped.wslStatus().contains("运行中的发行版：无"));
    }

    @Test
    void reportsWslFailuresWithoutTreatingThemAsAnEmptyInstallation() {
        var failed = new EnvironmentDiagnosticsService(command -> new VersionCommandExecutor.Result(1, "private details"), "Windows 11");
        IOException failure = assertThrows(IOException.class, failed::wslStatus);
        assertTrue(failure.getMessage().contains("退出码 1"));
        assertFalse(failure.getMessage().contains("private details"));
        IOException cause = new IOException("timeout");
        var missing = new EnvironmentDiagnosticsService(command -> { throw cause; }, "Windows 11");
        assertSame(cause, assertThrows(IOException.class, missing::wslStatus).getCause());
    }

    @Test
    void skipsWslOnNonWindows() throws IOException {
        var service = new EnvironmentDiagnosticsService(command -> { fail("Must not start WSL"); return null; }, "Linux");
        assertTrue(service.wslStatus().contains("非 Windows"));
    }

    @Test
    void decodesWslUtf16AndPreservesUtf8VersionOutput() {
        String names = "Ubuntu\r\n中文发行版\r\n";
        assertEquals(names, VersionCommandExecutor.decode(names.getBytes(StandardCharsets.UTF_16LE)));
        assertEquals(names, VersionCommandExecutor.decode(("\ufeff" + names).getBytes(StandardCharsets.UTF_16LE)));
        assertEquals("git version 2.49", VersionCommandExecutor.decode("git version 2.49".getBytes(StandardCharsets.UTF_8)));
    }
}
