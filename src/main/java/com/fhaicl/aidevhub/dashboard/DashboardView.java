package com.fhaicl.aidevhub.dashboard;

import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.project.ProjectDirectoryOpener;
import com.fhaicl.aidevhub.project.ProjectTerminalOpener;
import com.fhaicl.aidevhub.project.ProjectIdeaOpener;
import com.fhaicl.aidevhub.project.ProjectEditorOpener;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.notes.DevelopmentLog;
import com.fhaicl.aidevhub.git.GitBranchView;
import com.fhaicl.aidevhub.git.GitLatestCommitView;
import com.fhaicl.aidevhub.git.GitChangeCountView;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.layout.FlowPane;
import com.fhaicl.aidevhub.notes.ProjectLogService;
import com.fhaicl.aidevhub.todo.ProjectTodoService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.Objects;

public final class DashboardView extends VBox {
    private final ProjectTodoService todos;
    private final Label projectLabel = new Label();
    private final Label incomplete = new Label();
    private final Label projectCount = new Label();
    private final Label latestLog = new Label();
    private final GitBranchView branch = new GitBranchView(false);
    private final GitLatestCommitView commit = new GitLatestCommitView();
    private final GitChangeCountView changes = new GitChangeCountView(false);
    private final FlowPane launchers = new FlowPane(8, 8);
    private final ProjectLogService logs;
    private Task<DevelopmentLog> logTask;
    private Project project;
    private Task<Long> activeTask;

    public DashboardView(ProjectTodoService todos, ProjectLogStore logs, IdePathSettings settings) {
        super(12);
        this.todos = Objects.requireNonNull(todos);
        this.logs = new ProjectLogService(Objects.requireNonNull(logs));
        Objects.requireNonNull(settings);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Dashboard");
        title.setFont(Font.font(24));
        projectLabel.setWrapText(true);
        incomplete.setWrapText(true);
        latestLog.setWrapText(true);
        Button refresh = new Button("刷新 Dashboard");
        refresh.setOnAction(event -> refresh());
        addLauncher("打开目录", () -> new ProjectDirectoryOpener().open(project));
        addLauncher("打开终端", () -> new ProjectTerminalOpener().open(project));
        addLauncher("打开 IDEA", () -> new ProjectIdeaOpener().open(project,
                settings.requirePath(IdePathSettings.Ide.IDEA)));
        addLauncher("打开 VS Code", () -> new ProjectEditorOpener(ProjectEditorOpener.Editor.VS_CODE)
                .open(project, settings.requirePath(IdePathSettings.Ide.VS_CODE)));
        addLauncher("打开 Cursor", () -> new ProjectEditorOpener(ProjectEditorOpener.Editor.CURSOR)
                .open(project, settings.requirePath(IdePathSettings.Ide.CURSOR)));
        getChildren().addAll(title, refresh, projectCount, projectLabel, launchers,
                branch, changes, commit, incomplete, latestLog);
        setProject(null);
    }

    public void setProject(Project project) {
        this.project = project;
        projectLabel.setText(project == null ? "未选择当前项目，请先在 Projects 中设为当前项目。"
                : "当前项目：" + project.name() + "\n路径：" + project.path());
        launchers.setDisable(project == null);
        branch.setProject(project);
        commit.setProject(project);
        changes.setProject(project);
        refreshLocalData();
    }

    public void setProjectCount(int count) {
        projectCount.setText(count < 0 ? "项目数量：读取失败，请检查 Projects 页面。" : "项目数量：" + count);
    }

    public void refresh() {
        branch.refresh();
        commit.refresh();
        changes.refresh();
        refreshLocalData();
    }

    private void refreshLocalData() {
        refreshLog();
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
        incomplete.setText(project == null ? "未完成 TODO：—" : "未完成 TODO：正在读取…");
        if (project == null) return;
        Project selected = project;
        Task<Long> task = new Task<>() {
            @Override
            protected Long call() throws IOException {
                return todos.incompleteCount(selected);
            }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            if (activeTask != task) return;
            activeTask = null;
            incomplete.setText("未完成 TODO：" + task.getValue() + " 项");
        });
        task.setOnFailed(event -> {
            if (activeTask != task) return;
            activeTask = null;
            incomplete.setText("未完成 TODO：读取失败。请检查 TODO 文件、访问权限及磁盘状态，重新进入 Dashboard 可重试。");
            System.getLogger(DashboardView.class.getName()).log(System.Logger.Level.WARNING,
                    "Dashboard TODO count loading failed", task.getException());
        });
        Thread thread = new Thread(task, "dashboard-todo-count");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshLog() {
        if (logTask != null) {
            logTask.cancel(true);
            logTask = null;
        }
        latestLog.setText(project == null ? "最近开发日志：—" : "最近开发日志：正在读取…");
        if (project == null) return;
        Project selected = project;
        Task<DevelopmentLog> task = new Task<>() {
            @Override
            protected DevelopmentLog call() throws IOException {
                return logs.latest(selected).orElse(null);
            }
        };
        logTask = task;
        task.setOnSucceeded(event -> {
            if (logTask != task) return;
            logTask = null;
            DevelopmentLog log = task.getValue();
            latestLog.setText(log == null ? "最近开发日志：暂无日志。"
                    : "最近开发日志：" + log.title() + "\n日期："
                    + (log.date() == null ? "未记录" : log.date()) + "\n"
                    + (log.content().length() > 200 ? log.content().substring(0, 200) + "…" : log.content()));
        });
        task.setOnFailed(event -> {
            if (logTask != task) return;
            logTask = null;
            latestLog.setText("最近开发日志：读取失败，请检查日志文件及访问权限，点击刷新重试。");
            System.getLogger(DashboardView.class.getName()).log(System.Logger.Level.WARNING,
                    "Dashboard log loading failed", task.getException());
        });
        Thread thread = new Thread(task, "dashboard-latest-log");
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    private interface LaunchAction {
        void run() throws IOException;
    }

    private void addLauncher(String title, LaunchAction action) {
        Button button = new Button(title);
        button.setOnAction(event -> {
            if (project == null) return;
            try {
                action.run();
            } catch (IOException | SecurityException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "启动失败：" + exception.getMessage());
                alert.initOwner(getScene().getWindow());
                alert.setHeaderText(title + "失败");
                alert.showAndWait();
            }
        });
        launchers.getChildren().add(button);
    }
}
