package com.fhaicl.aidevhub;

import com.fhaicl.aidevhub.navigation.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class AiDeveloperHubApplication extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("AI Developer Hub");
        stage.setScene(new Scene(new MainView(), 960, 640));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
