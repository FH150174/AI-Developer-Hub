package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

public final class GitLatestCommitView extends VBox {
    private static final DateTimeFormatter COMMIT_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx");
    private final Label commitLabel = new Label("最近 Commit：—");
    private final Label detail = new Label();
    private final boolean compact;
    private Project project;
    private Task<GitLatestCommitReader.Result> activeTask;

    public GitLatestCommitView() {
        this(false);
    }

    public GitLatestCommitView(boolean compact) {
        super(8);
        this.compact = compact;
        commitLabel.setWrapText(true);
        if (compact) commitLabel.getStyleClass().add("metric-value");
        detail.setWrapText(true);
        detail.getStyleClass().add("metric-detail");
        detail.setVisible(compact);
        detail.setManaged(compact);
        getChildren().addAll(commitLabel, detail);
    }

    public void setProject(Project project) {
        this.project = project;
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
        commitLabel.setTooltip(null);
        commitLabel.setText(compact ? "—" : "最近 Commit：—");
        detail.setText("");
    }

    public void refresh() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
        commitLabel.setTooltip(null);
        commitLabel.setText(project == null ? (compact ? "—" : "最近 Commit：—")
                : (compact ? "正在读取…" : "最近 Commit：正在读取…"));
        detail.setText("");
        if (project == null) {
            return;
        }
        Project selected = project;
        Task<GitLatestCommitReader.Result> task = new Task<>() {
            @Override
            protected GitLatestCommitReader.Result call() throws IOException {
                return new GitLatestCommitReader().read(selected);
            }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            // A previous project's result must never overwrite the current selection.
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            GitLatestCommitReader.Result result = task.getValue();
            if (compact) {
                compactText(result);
            } else {
                commitLabel.setText(switch (result.status()) {
                    case COMMIT -> "最近 Commit：" + (result.subject().isEmpty() ? "（无标题）" : result.subject())
                            + "\nCommit 作者：" + (result.author().isEmpty() ? "（未记录）" : result.author())
                            + "\nCommit 时间：" + result.committedAt().format(COMMIT_TIME)
                            + "\nHash 简写：" + result.shortHash();
                    case NO_COMMITS -> "最近 Commit：暂无提交。";
                    case NOT_REPOSITORY -> "最近 Commit：不可用，项目目录不是 Git 仓库。";
                    case MISSING -> "最近 Commit：不可用，项目路径不存在。";
                    case NOT_DIRECTORY -> "最近 Commit：不可用，项目路径不是目录。";
                });
            }
        });
        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            String message = "最近 Commit：读取失败。" + GitException.userMessage(task.getException());
            commitLabel.setText(compact ? "读取失败" : message);
            if (compact) {
                commitLabel.setTooltip(new Tooltip(message));
                detail.setText("请查看 Git 页面的 Commit 状态。");
            }
        });
        Thread thread = new Thread(task, "git-latest-commit-display");
        thread.setDaemon(true);
        thread.start();
    }

    private void compactText(GitLatestCommitReader.Result result) {
        switch (result.status()) {
            case COMMIT -> {
                String subject = result.subject().isEmpty() ? "（无标题）" : result.subject();
                commitLabel.setText(subject);
                commitLabel.setTooltip(new Tooltip(subject));
                detail.setText("作者：" + (result.author().isEmpty() ? "（未记录）" : result.author())
                        + " · " + result.committedAt().format(COMMIT_TIME)
                        + " · " + result.shortHash());
            }
            case NO_COMMITS -> {
                commitLabel.setText("暂无提交");
                detail.setText("");
            }
            case NOT_REPOSITORY -> {
                commitLabel.setText("不是 Git 仓库");
                detail.setText("");
            }
            case MISSING -> {
                commitLabel.setText("项目路径不存在");
                detail.setText("");
            }
            case NOT_DIRECTORY -> {
                commitLabel.setText("项目路径不是目录");
                detail.setText("");
            }
        }
    }
}
