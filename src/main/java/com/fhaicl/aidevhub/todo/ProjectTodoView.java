package com.fhaicl.aidevhub.todo;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;
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
    private final ProjectTodoService service;
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private final Label projectLabel = new Label();
    private final Label status = new Label();
    private final Label emptyState = new Label("未选择当前项目");
    private final ListView<TodoItem> items = new ListView<>();
    private Project project;
    private Task<List<TodoItem>> activeTask;

    public ProjectTodoView(ProjectTodoStore store) {
        super(12);
        this.store = Objects.requireNonNull(store);
        this.service = new ProjectTodoService(store);
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
                setText(empty || item == null ? null : (item.completed() ? "[已完成] " : "[未完成] ") + item.title());
            }
        });
        VBox.setVgrow(items, Priority.ALWAYS);
        Button add = new Button("添加任务");
        Button complete = new Button("完成 / 取消完成");
        Button delete = new Button("删除任务");
        add.disableProperty().bind(ready.not());
        complete.disableProperty().bind(ready.not().or(items.getSelectionModel().selectedItemProperty().isNull()));
        delete.disableProperty().bind(complete.disableProperty());
        add.setOnAction(event -> addItem());
        complete.setOnAction(event -> changeSelected(false));
        delete.setOnAction(event -> changeSelected(true));
        getChildren().addAll(title, projectLabel, status, new HBox(12, add, complete, delete), items);
        setProject(null);
    }

    public void setProject(Project project) {
        this.project = project;
        projectLabel.setText(project == null ? "未选择当前项目，请先在 Projects 中设为当前项目。"
                : "当前项目：" + project.name() + "\n路径：" + project.path());
        refresh();
    }

    public void refresh() {
        ready.set(false);
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
            ready.set(true);
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

    private void addItem() {
        if (!ready.get()) return;
        Project selected = project;
        List<TodoItem> before = List.copyOf(items.getItems());
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("添加任务");
        dialog.setHeaderText("项目：" + selected.name());
        dialog.setContentText("任务标题：");
        Label error = new Label();
        error.setWrapText(true);
        dialog.getDialogPane().setExpandableContent(error);
        Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        save.disableProperty().bind(dialog.getEditor().textProperty().map(String::isBlank));
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                var updated = service.add(selected, before, dialog.getEditor().getText());
                showSaved(updated, updated.size() - 1);
            } catch (IOException | SecurityException exception) {
                event.consume();
                error.setText("添加失败，输入已保留。请检查访问权限、磁盘状态或 TODO 是否已变化。");
                dialog.getDialogPane().setExpanded(true);
                reportFailure(exception);
            }
        });
        dialog.showAndWait();
    }

    private void changeSelected(boolean deleting) {
        if (!ready.get()) return;
        int index = items.getSelectionModel().getSelectedIndex();
        if (index < 0) return;
        Project selected = project;
        List<TodoItem> before = List.copyOf(items.getItems());
        if (deleting) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "确定删除任务“" + before.get(index).title() + "”？", ButtonType.OK, ButtonType.CANCEL);
            confirmation.initOwner(getScene().getWindow());
            confirmation.setTitle("删除任务");
            confirmation.setHeaderText("项目：" + selected.name());
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        try {
            var updated = deleting ? service.delete(selected, before, index)
                    : service.setCompleted(selected, before, index, !before.get(index).completed());
            showSaved(updated, Math.min(index, updated.size() - 1));
        } catch (IOException | SecurityException exception) {
            status.setText("保存失败，原列表保持不变。请检查访问权限、磁盘状态或 TODO 是否已变化。重新进入 TODO 可重新加载。");
            reportFailure(exception);
        }
    }

    private void showSaved(List<TodoItem> updated, int index) {
        items.getItems().setAll(updated);
        if (index >= 0) items.getSelectionModel().select(index);
        status.setText("TODO 已保存：" + updated.size() + " 项");
    }

    private void reportFailure(Exception exception) {
        System.getLogger(ProjectTodoView.class.getName()).log(System.Logger.Level.WARNING,
                "Project TODO modification failed", exception);
    }
}
