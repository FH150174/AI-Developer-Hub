package com.fhaicl.aidevhub;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public final class AiDeveloperHubApplication extends Application {

    @Override
    public void start(Stage stage) {
        ListView<String> navigation = new ListView<>();
        navigation.getItems().addAll(
                "Projects", "Git", "Dev Notes", "TODO",
                "AI Agents", "Environment", "Settings");
        navigation.setPrefWidth(200);

        Label pageTitle = new Label();
        pageTitle.setFont(Font.font(24));
        Label currentPage = new Label();
        currentPage.textProperty().bind(pageTitle.textProperty());
        ToolBar topBar = new ToolBar(
                new Label("AI Developer Hub"), new Separator(), currentPage);

        Label description = new Label();
        description.setWrapText(true);
        Label placeholder = new Label();
        placeholder.setWrapText(true);
        StackPane placeholderArea = new StackPane(placeholder);
        placeholderArea.setPadding(new Insets(24));
        placeholderArea.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 4;");
        VBox.setVgrow(placeholderArea, Priority.ALWAYS);

        VBox content = new VBox(12, pageTitle, description, placeholderArea);
        content.setAlignment(Pos.TOP_LEFT);
        content.setMinWidth(0);
        content.setPadding(new Insets(24));

        navigation.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (selected != null) {
                        pageTitle.setText(selected);
                        description.setText(switch (selected) {
                            case "Projects" -> "Manage your local development projects.";
                            case "Git" -> "View Git status for your projects.";
                            case "Dev Notes" -> "Keep notes about your development work.";
                            case "TODO" -> "Track your development tasks.";
                            case "AI Agents" -> "Access your AI development assistants.";
                            case "Environment" -> "View your development environment information.";
                            case "Settings" -> "Configure application preferences.";
                            default -> "";
                        });
                        placeholder.setText(selected + " — Coming soon");
                    }
                });
        navigation.getSelectionModel().selectFirst();

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(navigation);
        root.setCenter(content);
        stage.setTitle("AI Developer Hub");
        stage.setScene(new Scene(root, 960, 640));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
