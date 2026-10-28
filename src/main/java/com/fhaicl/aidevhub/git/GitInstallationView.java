package com.fhaicl.aidevhub.git;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;

public final class GitInstallationView extends VBox {
    public GitInstallationView() {
        super(12);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Git");
        title.setFont(Font.font(24));
        Label description = new Label("检测当前应用能否通过 PATH 调用 Git，无需选择项目。");
        description.setWrapText(true);
        Label status = new Label("尚未检测 Git。");
        status.setWrapText(true);
        Button check = new Button("检测 Git 是否安装");
        check.setOnAction(event -> {
            check.setDisable(true);
            status.setText("正在检测 Git…");
            Task<String> task = new Task<>() {
                @Override
                protected String call() throws IOException {
                    return new GitInstallationChecker().version();
                }
            };
            task.setOnSucceeded(done -> {
                check.setDisable(false);
                status.setText("Git 已安装且可用：" + task.getValue());
            });
            task.setOnFailed(done -> {
                check.setDisable(false);
                status.setText("Git 当前不可用。请确认已安装 Git 并加入 PATH；修改后请重启应用再检测。\n原因："
                        + task.getException().getMessage());
            });
            Thread thread = new Thread(task, "git-installation-check");
            thread.setDaemon(true);
            thread.start();
        });
        getChildren().addAll(title, description, check, status);
    }
}
