package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public final class GitLatestCommitReader {
    public enum Status { COMMIT, NO_COMMITS, NOT_REPOSITORY, MISSING, NOT_DIRECTORY }

    public record Result(Status status, String subject, String author, OffsetDateTime committedAt, String shortHash) { }

    public Result read(Project project) throws IOException {
        GitRepositoryChecker.Status repository = new GitRepositoryChecker().check(project);
        if (repository != GitRepositoryChecker.Status.REPOSITORY) {
            return new Result(Status.valueOf(repository.name()), "", "", null, "");
        }
        GitCommandExecutor.Result result = new GitCommandExecutor().execute(project.path(),
                "--no-pager", "-c", "i18n.logOutputEncoding=UTF-8", "log", "-1",
                "--no-show-signature", "--format=%s%x00%an%x00%cI%x00%h%x00");
        if (result.exitCode() != 0) {
            if (result.stderr().strip().matches("fatal: your current branch '.+' does not have any commits yet")) {
                return new Result(Status.NO_COMMITS, "", "", null, "");
            }
            throw GitException.commandFailed("读取最近 Commit 失败", result);
        }
        return parse(result.stdout());
    }

    static Result parse(String output) throws GitException {
        String[] fields = output.split("\0", -1);
        if (fields.length != 5 || !fields[4].isBlank() || !fields[3].matches("[0-9a-f]{4,64}")) {
            throw GitException.invalidOutput();
        }
        try {
            return new Result(Status.COMMIT, fields[0], fields[1], OffsetDateTime.parse(fields[2]), fields[3]);
        } catch (DateTimeParseException exception) {
            throw new GitException(GitException.Kind.INVALID_OUTPUT,
                    "Git 返回了无法识别的提交时间，请检查仓库记录。", exception);
        }
    }
}
