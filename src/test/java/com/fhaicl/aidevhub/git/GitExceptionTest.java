package com.fhaicl.aidevhub.git;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class GitExceptionTest {
    @Test
    void preservesExitCodeAndDiagnosticCauseWithoutShowingRawOutput() {
        var failure = GitException.commandFailed("查询失败",
                new GitCommandExecutor.Result(128, "", "secret-token private-path\nraw error"));
        assertEquals(GitException.Kind.COMMAND_FAILED, failure.kind());
        assertTrue(failure.getMessage().contains("128"));
        assertEquals("secret-token private-path\nraw error", failure.getCause().getMessage());
        assertFalse(GitException.userMessage(failure).contains("secret-token"));
    }

    @Test
    void usesStdoutAsDiagnosticWhenStderrIsEmpty() {
        var failure = GitException.commandFailed("查询失败", new GitCommandExecutor.Result(1, "details", ""));
        assertEquals("details", failure.getCause().getMessage());
    }

    @Test
    void suppliesSafeFallbackForIoSecurityUnknownAndNullErrors() {
        assertTrue(GitException.userMessage(new IOException("secret")).contains("读写"));
        assertTrue(GitException.userMessage(new SecurityException("secret")).contains("访问"));
        assertTrue(GitException.userMessage(new RuntimeException("secret")).contains("意外"));
        assertFalse(GitException.userMessage(new RuntimeException("secret")).contains("secret"));
        assertNotNull(GitException.userMessage(null));
    }

    @Test
    void classifiesMalformedOutput() {
        assertEquals(GitException.Kind.INVALID_OUTPUT, GitException.invalidOutput().kind());
    }
}
