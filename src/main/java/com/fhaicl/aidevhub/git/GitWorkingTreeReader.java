package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GitWorkingTreeReader {
    public enum Status { CLEAN, DIRTY, NO_WORKING_TREE, NOT_REPOSITORY, MISSING, NOT_DIRECTORY }

    public record FileChange(String path, String originalPath, String code) {
        public String category() {
            if (code.equals("??")) {
                return "Untracked";
            }
            if (code.equals("DD") || code.equals("AA") || code.indexOf('U') >= 0) {
                return "Other (Conflict)";
            }
            boolean modified = code.indexOf('M') >= 0;
            boolean deleted = code.indexOf('D') >= 0;
            if (modified && deleted) {
                return "Modified / Deleted";
            }
            if (deleted) {
                return "Deleted";
            }
            return modified ? "Modified" : "Other";
        }
    }

    public record Result(Status status, List<FileChange> files) {
        public Result {
            files = List.copyOf(files);
        }
    }

    public Status read(Project project) throws IOException {
        return readDetails(project).status();
    }

    public Result readDetails(Project project) throws IOException {
        GitRepositoryChecker.Status repository = new GitRepositoryChecker().check(project);
        if (repository != GitRepositoryChecker.Status.REPOSITORY) {
            return new Result(Status.valueOf(repository.name()), List.of());
        }
        GitRepositoryChecker.CommandResult bare =
                GitRepositoryChecker.execute(project, "rev-parse", "--is-bare-repository");
        if (bare.exitCode() != 0) {
            throw new IOException("无法检查工作区类型：" + bare.output());
        }
        if (bare.output().equals("true")) {
            return new Result(Status.NO_WORKING_TREE, List.of());
        }
        // Disable optional index refresh writes; include staged, unstaged and untracked changes.
        GitRepositoryChecker.CommandResult result = GitRepositoryChecker.executeRaw(project,
                "--no-optional-locks", "status", "--porcelain=v1", "-z",
                "--untracked-files=all", "--ignore-submodules=none");
        if (result.exitCode() != 0) {
            throw new IOException("获取 Working Tree 状态失败：" + result.output());
        }
        List<FileChange> files = parse(result.output());
        return new Result(files.isEmpty() ? Status.CLEAN : Status.DIRTY, files);
    }

    static List<FileChange> parse(String output) throws IOException {
        List<FileChange> files = new ArrayList<>();
        int offset = 0;
        while (offset < output.length()) {
            int end = output.indexOf('\0', offset);
            if (end < offset + 4 || output.charAt(offset + 2) != ' ') {
                throw new IOException("Git 文件状态输出格式无效。");
            }
            String code = output.substring(offset, offset + 2);
            String path = output.substring(offset + 3, end);
            offset = end + 1;
            String originalPath = "";
            // In porcelain -z, rename/copy records contain destination then source.
            if (code.indexOf('R') >= 0 || code.indexOf('C') >= 0) {
                end = output.indexOf('\0', offset);
                if (end <= offset) {
                    throw new IOException("Git 重命名文件状态输出不完整。");
                }
                originalPath = output.substring(offset, end);
                offset = end + 1;
            }
            files.add(new FileChange(path, originalPath, code));
        }
        return List.copyOf(files);
    }
}
