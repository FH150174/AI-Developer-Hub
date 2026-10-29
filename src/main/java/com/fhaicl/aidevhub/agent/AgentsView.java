package com.fhaicl.aidevhub.agent;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public final class AgentsView extends VBox {
    private final AgentLauncher launcher = new AgentLauncher();

    public AgentsView() {
        super(12);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Agents");
        title.setFont(Font.font(24));
        Label description = new Label("从用户目录打开独立窗口。请先安装对应工具并加入 PATH；WSL 使用默认发行版。CLI 登录及启动错误请查看打开的终端。");
        description.setWrapText(true);
        getChildren().addAll(title, description);
        for (var tool : AgentLauncher.Tool.values()) {
            Label name = new Label(tool.displayName());
            name.setMinWidth(110);
            Button launch = new Button("启动");
            Label status = new Label();
            status.setWrapText(true);
            HBox row = new HBox(12, name, launch, status);
            HBox.setHgrow(status, Priority.ALWAYS);
            launch.setOnAction(event -> {
                launch.setDisable(true);
                status.setText("正在启动…");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() throws Exception {
                        launcher.launch(tool);
                        return null;
                    }
                };
                task.setOnSucceeded(done -> {
                    launch.setDisable(false);
                    status.setText("已发送启动请求，请查看独立窗口。");
                });
                task.setOnFailed(done -> {
                    launch.setDisable(false);
                    status.setText("启动失败：" + task.getException().getMessage());
                    System.getLogger(AgentsView.class.getName()).log(System.Logger.Level.WARNING,
                            tool.displayName() + " launch failed", task.getException());
                });
                Thread thread = new Thread(task, "agent-launch-" + tool.name());
                thread.setDaemon(true);
                thread.start();
            });
            getChildren().add(row);
        }
    }
}
