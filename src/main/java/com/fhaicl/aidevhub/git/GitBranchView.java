package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;

public final class GitBranchView extends VBox {
    private final Label projectLabel = new Label("未选择当前项目，请先在 Projects 中设为当前项目。");
    private final Label branchLabel = new Label("当前分支：—");
    private Project project;
    private Task<GitBranchReader.Result> activeTask;

    public GitBranchView() {
        super(8);
        projectLabel.setWrapText(true);
        branchLabel.setWrapText(true);
        getChildren().addAll(projectLabel, branchLabel);
    }

    public void setProject(Project project) {
        this.project = project;
        projectLabel.setText(project == null
                ? "未选择当前项目，请先在 Projects 中设为当前项目。"
                : "当前项目：" + project.name() + "\n路径：" + project.path());
        refresh();
    }

    public void refresh() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
        branchLabel.setText(project == null ? "当前分支：—" : "当前分支：正在读取…");
        if (project == null) {
            return;
        }
        Project selected = project;
        Task<GitBranchReader.Result> task = new Task<>() {
            @Override
            protected GitBranchReader.Result call() throws IOException {
                return new GitBranchReader().read(selected);
            }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            // A previous project's result must never overwrite the current selection.
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            branchLabel.setText(switch (task.getValue().status()) {
                case BRANCH -> "当前分支：" + task.getValue().branchName();
                case DETACHED_HEAD -> "当前分支：无（detached HEAD，分离头指针状态）";
                case NOT_REPOSITORY -> "当前分支：不可用，项目目录不是 Git 仓库。";
                case MISSING -> "当前分支：不可用，项目路径不存在。";
                case NOT_DIRECTORY -> "当前分支：不可用，项目路径不是目录。";
            });
        });
        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            branchLabel.setText("当前分支：读取失败。" + GitException.userMessage(task.getException()));
        });
        Thread thread = new Thread(task, "git-branch-display");
        thread.setDaemon(true);
        thread.start();
    }
}
