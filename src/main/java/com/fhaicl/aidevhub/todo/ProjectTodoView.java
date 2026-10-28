package com.fhaicl.aidevhub.todo;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class ProjectTodoView extends VBox {
    private final ProjectTodoStore store;
    private final Label projectLabel = new Label();
    private final Label status = new Label();
    private final Label emptyState = new Label("未选择当前项目");
    private final ListView<TodoItem> items = new ListView<>();
    private Project project;
    private Task<List<TodoItem>> activeTask;

    public ProjectTodoView(ProjectTodoStore store) {
        super(12);
        this.store = Objects.requireNonNull(store);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("TODO");
        title.setFont(Font.font(24));
        projectLabel.setWrapText(true);
        status.setWrapText(true);
        items.setPlaceholder(emptyState);
        items.setEditable(false);
        items.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(TodoItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title());
            }
        });
        VBox.setVgrow(items, Priority.ALWAYS);
        getChildren().addAll(title, projectLabel, status, items);
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
        items.getItems().clear();
        items.setDisable(project == null);
        emptyState.setText(project == null ? "未选择当前项目" : "正在读取项目 TODO…");
        status.setText("");
        if (project == null) return;
        Project selected = project;
        Task<List<TodoItem>> task = new Task<>() {
            @Override
            protected List<TodoItem> call() throws IOException {
                return store.load(selected);
            }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            if (activeTask != task) return;
            activeTask = null;
            items.getItems().setAll(task.getValue());
            emptyState.setText("当前项目暂无 TODO。");
            status.setText("TODO：" + task.getValue().size() + " 项");
        });
        task.setOnFailed(event -> {
            if (activeTask != task) return;
            activeTask = null;
            items.setDisable(true);
            emptyState.setText("TODO 读取失败");
            status.setText("项目 TODO 读取失败，请检查存储文件、访问权限及磁盘状态。重新进入 TODO 可重试。");
            System.getLogger(ProjectTodoView.class.getName()).log(System.Logger.Level.WARNING,
                    "Project TODO loading failed", task.getException());
        });
        Thread thread = new Thread(task, "project-todos-load");
        thread.setDaemon(true);
        thread.start();
    }
}
