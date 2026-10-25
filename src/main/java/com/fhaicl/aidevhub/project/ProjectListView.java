package com.fhaicl.aidevhub.project;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public final class ProjectListView extends VBox {
    private final ProjectList projects = new ProjectList();
    private final ProjectStore store = new ProjectStore(
            Path.of(System.getProperty("user.home"), ".aidevhub", "projects.xml"));
    private final BooleanProperty loadFailed = new SimpleBooleanProperty(false);

    public ProjectListView() {
        super(12);
        Label title = new Label("Projects");
        title.setFont(Font.font(24));
        Label description = new Label("集中管理本地开发项目。");
        description.setWrapText(true);

        TableView<Project> table = new TableView<>();
        table.setPlaceholder(new Label("暂无项目"));
        Label storageStatus = new Label();
        storageStatus.setWrapText(true);
        try {
            projects.replaceAll(store.load());
            table.getItems().setAll(projects.projects());
        } catch (IOException | SecurityException exception) {
            loadFailed.set(true);
            storageStatus.setText("项目列表加载失败，已禁用编辑以保护原文件。请检查用户目录下的 .aidevhub/projects.xml 后重启。原因："
                    + exception.getMessage());
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Project, String> nameColumn = new TableColumn<>("项目名称");
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().name()));
        TableColumn<Project, String> pathColumn = new TableColumn<>("项目路径");
        pathColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().path().toString()));
        table.getColumns().add(nameColumn);
        table.getColumns().add(pathColumn);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button addButton = new Button("添加项目");
        addButton.disableProperty().bind(loadFailed);
        addButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择本地项目目录");
            File directory = chooser.showDialog(getScene().getWindow());
            if (directory == null) {
                return;
            }
            List<Project> before = projects.projects();
            if (!projects.add(directory.toPath())) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "该项目路径已在列表中。");
                alert.initOwner(getScene().getWindow());
                alert.setTitle("重复项目");
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }
            if (saveChanges(before)) {
                table.getItems().setAll(projects.projects());
            }
        });

        Button removeButton = new Button("删除项目");
        removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(loadFailed));
        removeButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            List<Project> before = projects.projects();
            if (selected != null && projects.remove(selected) && saveChanges(before)) {
                table.getItems().remove(selected);
            }
        });

        Button renameButton = new Button("修改名称");
        renameButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(loadFailed));
        renameButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            TextInputDialog dialog = new TextInputDialog(selected.name());
            dialog.initOwner(getScene().getWindow());
            dialog.setTitle("修改项目名称");
            dialog.setHeaderText(null);
            dialog.setContentText("项目名称：");
            dialog.showAndWait().ifPresent(name -> {
                if (name.strip().isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "项目名称不能为空或仅包含空白字符。");
                    alert.initOwner(getScene().getWindow());
                    alert.setTitle("名称无效");
                    alert.setHeaderText(null);
                    alert.showAndWait();
                    return;
                }
                List<Project> before = projects.projects();
                Project renamed = projects.rename(selected, name);
                if (!saveChanges(before)) {
                    return;
                }
                table.getItems().setAll(projects.projects());
                table.getSelectionModel().select(renamed);
            });
        });

        getChildren().addAll(title, description, storageStatus,
                new HBox(12, addButton, renameButton, removeButton), table);
        setAlignment(Pos.TOP_LEFT);
        setMinWidth(0);
        setPadding(new Insets(24));
    }

    private boolean saveChanges(List<Project> before) {
        try {
            store.save(projects.projects());
            return true;
        } catch (IOException | SecurityException exception) {
            projects.replaceAll(before);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "项目列表保存失败，本次修改未生效。原因：" + exception.getMessage());
            alert.initOwner(getScene().getWindow());
            alert.setTitle("保存失败");
            alert.setHeaderText(null);
            alert.showAndWait();
            return false;
        }
    }
}
