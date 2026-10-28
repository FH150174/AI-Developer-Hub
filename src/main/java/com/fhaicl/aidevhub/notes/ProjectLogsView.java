package com.fhaicl.aidevhub.notes;

import com.fhaicl.aidevhub.project.Project;
import javafx.concurrent.Task;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class ProjectLogsView extends VBox {
    private final ProjectLogStore store;
    private final ProjectLogService service;
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private final Label projectLabel = new Label();
    private final Label status = new Label();
    private final ListView<DevelopmentLog> logs = new ListView<>();
    private final Label logDate = new Label("日期：—");
    private final TextArea content = new TextArea();
    private final MarkdownPreview preview = new MarkdownPreview();
    private final TabPane reading = new TabPane();
    private Project project;
    private Task<List<DevelopmentLog>> activeTask;

    public ProjectLogsView(ProjectLogStore store) {
        super(12);
        this.store = Objects.requireNonNull(store);
        this.service = new ProjectLogService(store);
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
                setText(empty || log == null ? null : (log.date() == null ? "未记录日期" : log.date().toString()) + "  " + log.title());
            }
        });
        content.setEditable(false);
        content.setWrapText(true);
        content.setPromptText("选择日志查看内容");
        Tab plain = new Tab("普通文本", content);
        Tab markdown = new Tab("Markdown 预览", preview);
        reading.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        reading.getTabs().addAll(plain, markdown);
        reading.getSelectionModel().selectedItemProperty().addListener((observable, before, selected) -> {
            if (selected == markdown) preview.showMarkdown(content.getText());
        });
        logs.getSelectionModel().selectedItemProperty().addListener((observable, before, selected) -> {
            content.setText(selected == null ? "" : selected.content());
            preview.showMarkdown(selected == null ? "" : selected.content());
            logDate.setText(selected == null ? "日期：—"
                    : "日期：" + (selected.date() == null ? "未记录日期" : selected.date().toString()));
        });
        VBox.setVgrow(logs, Priority.ALWAYS);
        VBox.setVgrow(reading, Priority.ALWAYS);
        Button create = new Button("新建日志");
        Button edit = new Button("编辑日志");
        Button delete = new Button("删除日志");
        create.disableProperty().bind(ready.not());
        edit.disableProperty().bind(ready.not().or(logs.getSelectionModel().selectedItemProperty().isNull()));
        delete.disableProperty().bind(edit.disableProperty());
        create.setOnAction(event -> editLog(true));
        edit.setOnAction(event -> editLog(false));
        delete.setOnAction(event -> deleteLog());
        getChildren().addAll(title, projectLabel, status, new HBox(12, create, edit, delete), logs, logDate, reading);
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
        logs.getItems().clear();
        content.clear();
        preview.showMarkdown("");
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
            ready.set(true);
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

    private void editLog(boolean creating) {
        if (!ready.get()) return;
        Project selectedProject = project;
        int index = logs.getSelectionModel().getSelectedIndex();
        if (!creating && index < 0) return;
        List<DevelopmentLog> before = List.copyOf(logs.getItems());
        DevelopmentLog original = creating ? null : before.get(index);
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle(creating ? "新建日志" : "编辑日志");
        dialog.setHeaderText("项目：" + selectedProject.name());
        dialog.setResizable(true);
        TextField title = new TextField(original == null ? "" : original.title());
        DatePicker date = new DatePicker(creating ? LocalDate.now() : original.date());
        date.setEditable(false);
        date.setPromptText("选择日期");
        TextArea body = new TextArea(original == null ? "" : original.content());
        body.setWrapText(true);
        body.setPrefSize(600, 320);
        MarkdownPreview draftPreview = new MarkdownPreview();
        Tab editor = new Tab("普通文本 / Markdown 源码", body);
        Tab rendered = new Tab("Markdown 预览", draftPreview);
        TabPane editing = new TabPane(editor, rendered);
        editing.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        editing.setPrefSize(600, 320);
        editing.getSelectionModel().selectedItemProperty().addListener((observable, beforeTab, selectedTab) -> {
            if (selectedTab == rendered) draftPreview.showMarkdown(body.getText());
        });
        Label error = new Label();
        error.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8, new Label("标题（必填）"), title,
                new Label("日期"), date, new Label("内容"), editing, error));
        ButtonType save = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(title.textProperty().map(String::isBlank));
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (creating && date.getValue() == null) {
                event.consume();
                error.setText("请选择日志日期。");
                return;
            }
            try {
                DevelopmentLog replacement = new DevelopmentLog(title.getText(), body.getText(), date.getValue());
                List<DevelopmentLog> updated = creating
                        ? service.create(selectedProject, before, replacement)
                        : service.edit(selectedProject, before, index, replacement);
                logs.getItems().setAll(updated);
                logs.getSelectionModel().select(creating ? updated.size() - 1 : index);
                status.setText("日志已保存。开发日志：" + updated.size() + " 条");
            } catch (IOException | SecurityException exception) {
                event.consume();
                error.setText("保存失败，输入内容已保留。请检查文件权限、磁盘状态或日志是否已变化。");
                reportFailure(exception);
            }
        });
        dialog.showAndWait();
    }

    private void deleteLog() {
        if (!ready.get()) return;
        int index = logs.getSelectionModel().getSelectedIndex();
        if (index < 0) return;
        Project selectedProject = project;
        List<DevelopmentLog> before = List.copyOf(logs.getItems());
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "确定删除日志“" + before.get(index).title() + "”？", ButtonType.OK, ButtonType.CANCEL);
        confirmation.initOwner(getScene().getWindow());
        confirmation.setTitle("删除日志");
        confirmation.setHeaderText("项目：" + selectedProject.name());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            List<DevelopmentLog> updated = service.delete(selectedProject, before, index);
            logs.getItems().setAll(updated);
            if (!updated.isEmpty()) logs.getSelectionModel().select(Math.min(index, updated.size() - 1));
            status.setText(updated.isEmpty() ? "当前项目暂无开发日志。" : "日志已删除。开发日志：" + updated.size() + " 条");
        } catch (IOException | SecurityException exception) {
            status.setText("删除失败，列表保持不变。请检查文件权限、磁盘状态或日志是否已变化。");
            reportFailure(exception);
        }
    }

    private void reportFailure(Exception exception) {
        System.getLogger(ProjectLogsView.class.getName()).log(System.Logger.Level.WARNING,
                "Project log modification failed", exception);
    }
}
