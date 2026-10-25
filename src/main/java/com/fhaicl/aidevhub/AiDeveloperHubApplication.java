package com.fhaicl.aidevhub;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
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
        Label placeholder = new Label();
        VBox content = new VBox(12, pageTitle, placeholder);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(24));

        navigation.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (selected != null) {
                        pageTitle.setText(selected);
                        placeholder.setText(selected + " — Coming soon");
                    }
                });
        navigation.getSelectionModel().selectFirst();

        BorderPane root = new BorderPane();
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
