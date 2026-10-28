package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.project.ProjectPathChecker;

import java.io.IOException;
import java.util.Objects;

public final class GitRepositoryChecker {
    public enum Status { REPOSITORY, NOT_REPOSITORY, MISSING, NOT_DIRECTORY }

    public Status check(Project project) throws IOException {
        Objects.requireNonNull(project, "Project must not be null");
        ProjectPathChecker.Status pathStatus = new ProjectPathChecker().check(project);
        if (pathStatus == ProjectPathChecker.Status.MISSING) {
            return Status.MISSING;
        }
        if (pathStatus == ProjectPathChecker.Status.NOT_DIRECTORY) {
            return Status.NOT_DIRECTORY;
        }
        GitCommandExecutor.Result result = new GitCommandExecutor().execute(project.path(), "rev-parse", "--git-dir");
        if (result.exitCode() == 0) {
            return Status.REPOSITORY;
        }
        if (result.output().strip().startsWith("fatal: not a git repository")) {
            return Status.NOT_REPOSITORY;
        }
        throw new IOException("Git 仓库检测失败：" + result.output());
    }

}
