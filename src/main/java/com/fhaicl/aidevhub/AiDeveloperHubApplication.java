package com.fhaicl.aidevhub;

import com.fhaicl.aidevhub.navigation.MainView;
import javafx.application.Application;
import com.fhaicl.aidevhub.storage.AppDataDirectory;
import javafx.scene.control.Alert;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class AiDeveloperHubApplication extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle(com.fhaicl.aidevhub.ui.AppIdentity.NAME);
        stage.getIcons().addAll(com.fhaicl.aidevhub.ui.AppIdentity.icons());
        java.nio.file.Path dataDirectory;
        try {
            dataDirectory = AppDataDirectory.initialize();
        } catch (java.io.IOException | RuntimeException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            com.fhaicl.aidevhub.ui.Ui.dialog(alert, null);
            alert.setTitle("应用数据初始化失败");
            alert.setHeaderText("无法准备本地数据目录，应用未启动。");
            alert.setContentText("请检查 APPDATA 与旧 .aidevhub 目录的访问权限。原因：" + exception.getMessage());
            alert.showAndWait();
            javafx.application.Platform.exit();
            return;
        }
        stage.setScene(new Scene(new MainView(dataDirectory), 960, 640));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
