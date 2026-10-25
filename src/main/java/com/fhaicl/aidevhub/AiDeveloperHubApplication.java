package com.fhaicl.aidevhub;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class AiDeveloperHubApplication extends Application {

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(12,
                new Label("AI Developer Hub"),
                new Label("Project Foundation"));
        root.setAlignment(Pos.CENTER);
        stage.setTitle("AI Developer Hub");
        stage.setScene(new Scene(root, 960, 640));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
