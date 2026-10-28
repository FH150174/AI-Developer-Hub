package com.fhaicl.aidevhub.git;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GitInstallationCheckerTest {
    @Test
    void detectsActualGitWithoutProject() throws IOException {
        assertTrue(new GitInstallationChecker().version().startsWith("git version "));
    }

    @Test
    void acceptsWindowsVersionAndTrailingNewline() throws IOException {
        assertEquals("git version 2.51.0.windows.1", checker(0, "git version 2.51.0.windows.1\r\n").version());
    }

    @Test
    void acceptsStandardVersion() throws IOException {
        assertEquals("git version 2.43.0", checker(0, "git version 2.43.0\n").version());
    }

    @Test
    void reportsLaunchFailureWithOriginalCause() {
        IOException failure = new IOException("Cannot run program git");
        GitInstallationChecker checker = new GitInstallationChecker(() -> { throw failure; });
        assertSame(failure, assertThrows(IOException.class, checker::version));
    }

    @Test
    void rejectsFailedCommandEvenWithValidLookingVersion() {
        IOException failure = assertThrows(IOException.class,
                () -> checker(1, "git version 2.43.0").version());
        assertTrue(failure.getMessage().contains("退出码 1"));
    }

    @Test
    void rejectsEmptyMalformedAndMultilineVersionOutput() {
        for (String output : new String[] { "", "unrelated program", "git version unknown",
                "git version 2.43.0\nextra output" }) {
            assertThrows(IOException.class, () -> checker(0, output).version());
        }
    }

    private GitInstallationChecker checker(int exitCode, String output) {
        return new GitInstallationChecker(() -> new GitRepositoryChecker.CommandResult(exitCode, output));
    }
}
