package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;

public final class GitLatestCommitReader {
    public enum Status { COMMIT, NO_COMMITS, NOT_REPOSITORY, MISSING, NOT_DIRECTORY }

    public record Result(Status status, String subject) { }

    public Result read(Project project) throws IOException {
        GitRepositoryChecker.Status repository = new GitRepositoryChecker().check(project);
        if (repository != GitRepositoryChecker.Status.REPOSITORY) {
            return new Result(Status.valueOf(repository.name()), "");
        }
        GitCommandExecutor.Result result = new GitCommandExecutor().execute(project.path(),
                "--no-pager", "-c", "i18n.logOutputEncoding=UTF-8", "log", "-1",
                "--no-show-signature", "--format=%s%x00");
        if (result.exitCode() != 0) {
            if (result.stderr().strip().matches("fatal: your current branch '.+' does not have any commits yet")) {
                return new Result(Status.NO_COMMITS, "");
            }
            throw GitException.commandFailed("读取最近 Commit 失败", result);
        }
        int end = result.stdout().indexOf('\0');
        if (end < 0 || !result.stdout().substring(end + 1).isBlank()) {
            throw GitException.invalidOutput();
        }
        return new Result(Status.COMMIT, result.stdout().substring(0, end));
    }
}
