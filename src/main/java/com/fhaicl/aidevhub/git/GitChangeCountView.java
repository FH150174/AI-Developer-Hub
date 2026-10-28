package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;

public final class GitChangeCountView extends VBox {
    private final Label countLabel = new Label("修改文件数量：—");
    private Project project;
    private Task<GitWorkingTreeReader.Result> activeTask;

    public GitChangeCountView() {
        super(8);
        countLabel.setWrapText(true);
        Label scope = new Label("统计整个仓库的已暂存、未暂存和未跟踪文件；同一文件只计一次，不含忽略文件。");
        scope.setWrapText(true);
        getChildren().addAll(countLabel, scope);
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
        countLabel.setText(project == null ? "修改文件数量：—" : "修改文件数量：正在读取…");
        if (project == null) {
            return;
        }
        Project selected = project;
        Task<GitWorkingTreeReader.Result> task = new Task<>() {
            @Override
            protected GitWorkingTreeReader.Result call() throws IOException {
                return new GitWorkingTreeReader().readDetails(selected);
            }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            // A previous project's result must never overwrite the current selection.
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            countLabel.setText(switch (task.getValue().status()) {
                case CLEAN, DIRTY -> "修改文件数量：" + task.getValue().files().size();
                case NO_WORKING_TREE -> "修改文件数量：不可用，裸仓库没有 Working Tree。";
                case NOT_REPOSITORY -> "修改文件数量：不可用，项目目录不是 Git 仓库。";
                case MISSING -> "修改文件数量：不可用，项目路径不存在。";
                case NOT_DIRECTORY -> "修改文件数量：不可用，项目路径不是目录。";
            });
        });
        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            countLabel.setText("修改文件数量：读取失败。" + GitException.userMessage(task.getException()));
        });
        Thread thread = new Thread(task, "git-change-count-display");
        thread.setDaemon(true);
        thread.start();
    }
}
