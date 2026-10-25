package com.fhaicl.aidevhub.project;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;

import java.io.File;

public final class ProjectListView extends VBox {
    private final ProjectList projects = new ProjectList();

    public ProjectListView() {
        super(12);
        Label title = new Label("Projects");
        title.setFont(Font.font(24));
        Label description = new Label("集中管理本地开发项目。");
        description.setWrapText(true);

        TableView<Project> table = new TableView<>();
        table.setPlaceholder(new Label("暂无项目"));
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
        addButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择本地项目目录");
            File directory = chooser.showDialog(getScene().getWindow());
            if (directory == null) {
                return;
            }
            if (!projects.add(directory.toPath())) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "该项目路径已在列表中。");
                alert.initOwner(getScene().getWindow());
                alert.setTitle("重复项目");
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }
            table.getItems().setAll(projects.projects());
        });

        Button removeButton = new Button("删除项目");
        removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && projects.remove(selected)) {
                table.getItems().remove(selected);
            }
        });

        getChildren().addAll(title, description, new HBox(12, addButton, removeButton), table);
        setAlignment(Pos.TOP_LEFT);
        setMinWidth(0);
        setPadding(new Insets(24));
    }
}
