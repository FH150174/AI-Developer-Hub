package com.fhaicl.aidevhub.git;

import java.io.IOException;
import java.util.Objects;

public final class GitInstallationChecker {
    @FunctionalInterface
    interface VersionRunner {
        GitRepositoryChecker.CommandResult run() throws IOException;
    }

    private final VersionRunner runner;

    public GitInstallationChecker() {
        this(GitRepositoryChecker::executeVersion);
    }

    GitInstallationChecker(VersionRunner runner) {
        this.runner = Objects.requireNonNull(runner);
    }

    public String version() throws IOException {
        GitRepositoryChecker.CommandResult result = runner.run();
        String output = result.output().strip();
        if (result.exitCode() != 0) {
            throw new IOException("Git 版本检测失败（退出码 " + result.exitCode() + "）：" + output);
        }
        if (!output.matches("git version [0-9]+\\.[0-9]+[^\\r\\n]*")) {
            throw new IOException("Git 返回了无法识别的版本信息：" + output);
        }
        return output;
    }
}
