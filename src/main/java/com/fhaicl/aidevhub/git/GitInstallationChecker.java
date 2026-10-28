package com.fhaicl.aidevhub.git;

import java.io.IOException;
import java.util.Objects;

public final class GitInstallationChecker {
    @FunctionalInterface
    interface VersionRunner {
        GitCommandExecutor.Result run() throws IOException;
    }

    private final VersionRunner runner;

    public GitInstallationChecker() {
        this(() -> new GitCommandExecutor().execute("--version"));
    }

    GitInstallationChecker(VersionRunner runner) {
        this.runner = Objects.requireNonNull(runner);
    }

    public String version() throws IOException {
        GitCommandExecutor.Result result = runner.run();
        String output = result.output().strip();
        if (result.exitCode() != 0) {
            throw GitException.commandFailed("Git 版本检测失败", result);
        }
        if (!output.matches("git version [0-9]+\\.[0-9]+[^\\r\\n]*")) {
            throw GitException.invalidOutput();
        }
        return output;
    }
}
