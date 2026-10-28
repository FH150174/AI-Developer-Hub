package com.fhaicl.aidevhub.notes;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class ProjectLogsView extends VBox {
    private final ProjectLogStore store;
    private final Label projectLabel = new Label();
    private final Label status = new Label();
    private final ListView<DevelopmentLog> logs = new ListView<>();
    private final TextArea content = new TextArea();
    private Project project;
    private Task<List<DevelopmentLog>> activeTask;

    public ProjectLogsView(ProjectLogStore store) {
        super(12);
        this.store = Objects.requireNonNull(store);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Notes");
        title.setFont(Font.font(24));
        projectLabel.setWrapText(true);
        status.setWrapText(true);
        logs.setPlaceholder(new Label("暂无可显示的日志"));
        logs.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(DevelopmentLog log, boolean empty) {
                super.updateItem(log, empty);
                setText(empty || log == null ? null : log.title());
            }
        });
        content.setEditable(false);
        content.setWrapText(true);
        content.setPromptText("选择日志查看内容");
        logs.getSelectionModel().selectedItemProperty().addListener((observable, before, selected) ->
                content.setText(selected == null ? "" : selected.content()));
        VBox.setVgrow(logs, Priority.ALWAYS);
        VBox.setVgrow(content, Priority.ALWAYS);
        getChildren().addAll(title, projectLabel, status, logs, content);
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
        logs.getItems().clear();
        content.clear();
        logs.setDisable(project == null);
        status.setText(project == null ? "" : "正在读取项目日志…");
        if (project == null) {
            return;
        }
        Project selected = project;
        Task<List<DevelopmentLog>> task = new Task<>() {
            @Override
            protected List<DevelopmentLog> call() throws IOException {
                return store.load(selected);
            }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            if (activeTask != task) return;
            activeTask = null;
            logs.getItems().setAll(task.getValue());
            status.setText(task.getValue().isEmpty() ? "当前项目暂无开发日志。" : "开发日志：" + task.getValue().size() + " 条");
        });
        task.setOnFailed(event -> {
            if (activeTask != task) return;
            activeTask = null;
            logs.setDisable(true);
            status.setText("项目日志读取失败，请检查日志文件内容、访问权限及磁盘状态。重新进入 Notes 可重试。");
            System.getLogger(ProjectLogsView.class.getName()).log(System.Logger.Level.WARNING,
                    "Project log loading failed", task.getException());
        });
        Thread thread = new Thread(task, "project-logs-load");
        thread.setDaemon(true);
        thread.start();
    }
}
