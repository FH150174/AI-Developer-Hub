package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;

public final class GitWorkingTreeReader {
    public enum Status { CLEAN, DIRTY, NO_WORKING_TREE, NOT_REPOSITORY, MISSING, NOT_DIRECTORY }

    public Status read(Project project) throws IOException {
        GitRepositoryChecker.Status repository = new GitRepositoryChecker().check(project);
        if (repository != GitRepositoryChecker.Status.REPOSITORY) {
            return Status.valueOf(repository.name());
        }
        GitRepositoryChecker.CommandResult bare =
                GitRepositoryChecker.execute(project, "rev-parse", "--is-bare-repository");
        if (bare.exitCode() != 0) {
            throw new IOException("无法检查工作区类型：" + bare.output());
        }
        if (bare.output().equals("true")) {
            return Status.NO_WORKING_TREE;
        }
        // Disable optional index refresh writes; include staged, unstaged and untracked changes.
        GitRepositoryChecker.CommandResult result = GitRepositoryChecker.execute(project,
                "--no-optional-locks", "status", "--porcelain=v1", "-z",
                "--untracked-files=all", "--ignore-submodules=none");
        if (result.exitCode() != 0) {
            throw new IOException("获取 Working Tree 状态失败：" + result.output());
        }
        return result.output().isEmpty() ? Status.CLEAN : Status.DIRTY;
    }
}
