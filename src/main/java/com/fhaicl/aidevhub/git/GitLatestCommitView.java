package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

public final class GitLatestCommitView extends VBox {
    private final Label commitLabel = new Label("最近 Commit：—");
    private Project project;
    private Task<GitLatestCommitReader.Result> activeTask;

    public GitLatestCommitView() {
        super(8);
        commitLabel.setWrapText(true);
        getChildren().add(commitLabel);
    }

    public void setProject(Project project) {
        this.project = project;
        refresh();
    }

    public void refresh() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
        commitLabel.setText(project == null ? "最近 Commit：—" : "最近 Commit：正在读取…");
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
            commitLabel.setText(switch (task.getValue().status()) {
                case COMMIT -> "最近 Commit：" + (task.getValue().subject().isEmpty()
                        ? "（无标题）" : task.getValue().subject())
                        + "\nCommit 作者：" + (task.getValue().author().isEmpty() ? "（未记录）" : task.getValue().author())
                        + "\nCommit 时间：" + task.getValue().committedAt()
                                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx"));
                case NO_COMMITS -> "最近 Commit：暂无提交。";
                case NOT_REPOSITORY -> "最近 Commit：不可用，项目目录不是 Git 仓库。";
                case MISSING -> "最近 Commit：不可用，项目路径不存在。";
                case NOT_DIRECTORY -> "最近 Commit：不可用，项目路径不是目录。";
            });
        });
        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            commitLabel.setText("最近 Commit：读取失败。" + GitException.userMessage(task.getException()));
        });
        Thread thread = new Thread(task, "git-latest-commit-display");
        thread.setDaemon(true);
        thread.start();
    }
}
