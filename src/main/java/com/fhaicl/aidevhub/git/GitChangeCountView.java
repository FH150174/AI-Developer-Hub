package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Objects;

public final class GitChangeCountView extends VBox {
    @FunctionalInterface
    interface WorkingTreeQuery {
        GitWorkingTreeReader.Result read(Project project) throws IOException;
    }

    private final Label countLabel = new Label("修改文件数量：—");
    private final TableView<GitWorkingTreeReader.FileChange> files = new TableView<>();
    private final Label emptyState = new Label("未选择当前项目");
    private final boolean compact;
    private final WorkingTreeQuery query;
    private Project project;
    private Task<GitWorkingTreeReader.Result> activeTask;

    public GitChangeCountView() {
        this(true, false);
    }

    public GitChangeCountView(boolean showDetails) {
        this(showDetails, false);
    }

    public GitChangeCountView(boolean showDetails, boolean compact) {
        this(showDetails, compact, new GitWorkingTreeReader()::readDetails);
    }

    GitChangeCountView(boolean showDetails, WorkingTreeQuery query) {
        this(showDetails, false, query);
    }

    GitChangeCountView(boolean showDetails, boolean compact, WorkingTreeQuery query) {
        super(8);
        this.compact = compact;
        this.query = Objects.requireNonNull(query);
        countLabel.setWrapText(true);
        if (compact) countLabel.getStyleClass().add("metric-value");
        Label scope = new Label("统计整个仓库的已暂存、未暂存和未跟踪文件；同一文件只计一次，不含忽略文件。");
        scope.setWrapText(true);
        scope.setMinWidth(0);
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
        if (compact) {
            TitledPane explanation = new TitledPane("统计说明", scope);
            explanation.setExpanded(false);
            explanation.setAnimated(false);
            explanation.setFocusTraversable(true);
            explanation.getStyleClass().add("stats-explanation");
            getChildren().addAll(countLabel, explanation);
        } else {
            getChildren().addAll(countLabel, scope);
        }
        if (showDetails) getChildren().addAll(heading, legend, files);
    }

    public void setProject(Project project) {
        this.project = project;
        cancelActiveTask();
        files.getItems().clear();
        emptyState.setText(project == null ? "未选择当前项目" : "项目已变更，进入页面后刷新 Git 状态。");
        countLabel.setTooltip(null);
        countLabel.setText(compact ? "—" : "修改文件数量：—");
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
    }

    public void refresh() {
        cancelActiveTask();
        files.getItems().clear();
        emptyState.setText(project == null ? "未选择当前项目" : "正在读取 Git 状态…");
        countLabel.setTooltip(null);
        countLabel.setText(project == null ? (compact ? "—" : "修改文件数量：—")
                : (compact ? "正在读取…" : "修改文件数量：正在读取…"));
        if (project == null) {
            return;
        }
        Project selected = project;
        Task<GitWorkingTreeReader.Result> task = new Task<>() {
            @Override
            protected GitWorkingTreeReader.Result call() throws IOException {
                return query.read(selected);
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
            countLabel.setText(compact ? compactSummary(task.getValue()) : switch (task.getValue().status()) {
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
            String detail = "修改文件数量：读取失败。" + GitException.userMessage(task.getException());
            countLabel.setText(compact ? "读取失败" : detail);
            if (compact) countLabel.setTooltip(new Tooltip(detail));
        });
        Thread thread = new Thread(task, "git-change-count-display");
        thread.setDaemon(true);
        thread.start();
    }

    private static String compactSummary(GitWorkingTreeReader.Result result) {
        return switch (result.status()) {
            case CLEAN -> "无变更";
            case DIRTY -> result.files().size() + " 个文件变更";
            case NO_WORKING_TREE -> "不可用（裸仓库）";
            case NOT_REPOSITORY -> "不是 Git 仓库";
            case MISSING -> "项目路径不存在";
            case NOT_DIRECTORY -> "项目路径不是目录";
        };
    }
}
