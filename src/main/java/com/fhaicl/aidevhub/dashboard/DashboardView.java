package com.fhaicl.aidevhub.dashboard;

import com.fhaicl.aidevhub.project.Project;
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
    private Project project;
    private Task<Long> activeTask;

    public DashboardView(ProjectTodoService todos) {
        super(12);
        this.todos = Objects.requireNonNull(todos);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Dashboard");
        title.setFont(Font.font(24));
        projectLabel.setWrapText(true);
        incomplete.setWrapText(true);
        getChildren().addAll(title, projectLabel, incomplete);
        setProject(null);
    }

    public void setProject(Project project) {
        this.project = project;
        projectLabel.setText(project == null ? "未选择当前项目，请先在 Projects 中设为当前项目。"
                : "当前项目：" + project.name() + "\n路径：" + project.path());
        refresh();
    }

    public void refresh() {
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
}
