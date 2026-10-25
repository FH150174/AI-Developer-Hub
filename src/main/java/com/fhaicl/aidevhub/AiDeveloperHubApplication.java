package com.fhaicl.aidevhub;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class AiDeveloperHubApplication extends Application {

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane(new Label("AI Developer Hub"));
        stage.setTitle("AI Developer Hub");
        stage.setScene(new Scene(root, 960, 640));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
