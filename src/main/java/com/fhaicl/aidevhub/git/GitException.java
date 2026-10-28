package com.fhaicl.aidevhub.git;

import java.io.IOException;

public final class GitException extends IOException {
    public enum Kind { START_FAILED, INVALID_DIRECTORY, TIMEOUT, INTERRUPTED, COMMAND_FAILED, INVALID_OUTPUT }

    private final Kind kind;

    public GitException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    static GitException commandFailed(String operation, GitCommandExecutor.Result result) {
        String detail = result.stderr().isBlank() ? result.stdout() : result.stderr();
        return new GitException(Kind.COMMAND_FAILED,
                operation + "（退出码 " + result.exitCode() + "）。请检查仓库访问权限、配置及完整性。",
                new IOException(detail));
    }

    static GitException invalidOutput() {
        return new GitException(Kind.INVALID_OUTPUT, "Git 返回了无法识别的数据，请检查 Git 版本及仓库状态。", null);
    }

    public static String userMessage(Throwable failure) {
        if (failure instanceof GitException exception) {
            return exception.getMessage();
        }
        if (failure instanceof SecurityException) {
            return "访问被拒绝，请检查 Git 程序、项目目录及临时目录的访问权限。";
        }
        if (failure instanceof IOException) {
            return "Git 查询发生读写错误，请检查项目目录、临时目录的访问权限及磁盘状态。";
        }
        return "Git 查询发生意外错误，请重试；若仍失败，请联系维护者检查异常。";
    }
}
