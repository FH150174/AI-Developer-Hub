package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;

public final class GitBranchReader {
    public enum Status { BRANCH, DETACHED_HEAD, NOT_REPOSITORY, MISSING, NOT_DIRECTORY }

    public record Result(Status status, String branchName) { }

    public Result read(Project project) throws IOException {
        GitRepositoryChecker.Status repository = new GitRepositoryChecker().check(project);
        if (repository != GitRepositoryChecker.Status.REPOSITORY) {
            return new Result(Status.valueOf(repository.name()), "");
        }
        // symbolic-ref also works before the first commit; exit 1 means detached HEAD.
        GitCommandExecutor.Result result =
                new GitCommandExecutor().execute(project.path(), "symbolic-ref", "--quiet", "HEAD");
        if (result.exitCode() == 0 && result.output().startsWith("refs/heads/")) {
            return new Result(Status.BRANCH, result.stdout().strip().substring("refs/heads/".length()));
        }
        if (result.exitCode() == 1) {
            return new Result(Status.DETACHED_HEAD, "");
        }
        throw GitException.commandFailed("获取当前分支失败", result);
    }
}
