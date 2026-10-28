package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;

public final class GitChangeCountView extends VBox {
    private final Label countLabel = new Label("修改文件数量：—");
    private final TableView<GitWorkingTreeReader.FileChange> files = new TableView<>();
    private final Label emptyState = new Label("未选择当前项目");
    private Project project;
    private Task<GitWorkingTreeReader.Result> activeTask;

    public GitChangeCountView() {
        super(8);
        countLabel.setWrapText(true);
        Label scope = new Label("统计整个仓库的已暂存、未暂存和未跟踪文件；同一文件只计一次，不含忽略文件。");
        scope.setWrapText(true);
        Label heading = new Label("Git 状态列表");
        Label legend = new Label("状态码：第一位为暂存区，第二位为工作区；? 未跟踪，M 修改，D 删除，A 新增，R 重命名，C 复制，U 冲突。");
        legend.setWrapText(true);
        files.setPlaceholder(emptyState);
        files.setEditable(false);
        files.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<GitWorkingTreeReader.FileChange, String> category = new TableColumn<>("类型");
        category.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().category()));
        TableColumn<GitWorkingTreeReader.FileChange, String> code = new TableColumn<>("暂存区 / 工作区");
        code.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().code()));
        TableColumn<GitWorkingTreeReader.FileChange, String> path = new TableColumn<>("仓库相对路径");
        path.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().path()));
        TableColumn<GitWorkingTreeReader.FileChange, String> original = new TableColumn<>("原路径");
        original.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().originalPath()));
        files.getColumns().add(category);
        files.getColumns().add(code);
        files.getColumns().add(path);
        files.getColumns().add(original);
        files.setPrefHeight(280);
        files.setMinHeight(140);
        VBox.setVgrow(files, Priority.ALWAYS);
        getChildren().addAll(countLabel, scope, heading, legend, files);
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
        files.getItems().clear();
        emptyState.setText(project == null ? "未选择当前项目" : "正在读取 Git 状态…");
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
            GitWorkingTreeReader.Result result = task.getValue();
            files.getItems().setAll(result.files());
            emptyState.setText(switch (result.status()) {
                case CLEAN -> "工作区干净，没有变更文件";
                case DIRTY -> "暂无变更文件";
                case NO_WORKING_TREE -> "裸仓库没有 Working Tree";
                case NOT_REPOSITORY -> "项目目录不是 Git 仓库";
                case MISSING -> "项目路径不存在";
                case NOT_DIRECTORY -> "项目路径不是目录";
            });
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
            files.getItems().clear();
            emptyState.setText("Git 状态读取失败，请检查上方错误提示");
            countLabel.setText("修改文件数量：读取失败。" + GitException.userMessage(task.getException()));
        });
        Thread thread = new Thread(task, "git-change-count-display");
        thread.setDaemon(true);
        thread.start();
    }
}
