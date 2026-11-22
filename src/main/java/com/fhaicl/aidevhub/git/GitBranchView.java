package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.io.IOException;

public final class GitBranchView extends VBox {
    private final Label projectLabel = new Label("未选择当前项目，请先在 Projects 中设为当前项目。");
    private final Label branchLabel = new Label("当前分支：—");
    private final boolean compact;
    private Project project;
    private Task<GitBranchReader.Result> activeTask;

    public GitBranchView() {
        this(true, false);
    }

    public GitBranchView(boolean showProject) {
        this(showProject, false);
    }

    public GitBranchView(boolean showProject, boolean compact) {
        super(8);
        this.compact = compact;
        projectLabel.setVisible(showProject);
        projectLabel.setManaged(showProject);
        projectLabel.setWrapText(true);
        branchLabel.setWrapText(true);
        if (compact) branchLabel.getStyleClass().add("metric-value");
        getChildren().addAll(projectLabel, branchLabel);
    }

    public void setProject(Project project) {
        this.project = project;
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
        projectLabel.setText(project == null
                ? "未选择当前项目，请先在 Projects 中设为当前项目。"
                : "当前项目：" + project.name() + "\n路径：" + project.path());
        branchLabel.setTooltip(null);
        branchLabel.setText(compact ? "—" : "当前分支：—");
    }

    public void refresh() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
        branchLabel.setTooltip(null);
        branchLabel.setText(project == null ? (compact ? "—" : "当前分支：—")
                : (compact ? "正在读取…" : "当前分支：正在读取…"));
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
                case BRANCH -> compact ? task.getValue().branchName() : "当前分支：" + task.getValue().branchName();
                case DETACHED_HEAD -> compact ? "detached HEAD" : "当前分支：无（detached HEAD，分离头指针状态）";
                case NOT_REPOSITORY -> compact ? "不是 Git 仓库" : "当前分支：不可用，项目目录不是 Git 仓库。";
                case MISSING -> compact ? "项目路径不存在" : "当前分支：不可用，项目路径不存在。";
                case NOT_DIRECTORY -> compact ? "项目路径不是目录" : "当前分支：不可用，项目路径不是目录。";
            });
        });
        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }
            activeTask = null;
            String detail = "当前分支：读取失败。" + GitException.userMessage(task.getException());
            branchLabel.setText(compact ? "读取失败" : detail);
            if (compact) branchLabel.setTooltip(new Tooltip(detail));
        });
        Thread thread = new Thread(task, "git-branch-display");
        thread.setDaemon(true);
        thread.start();
    }
}
