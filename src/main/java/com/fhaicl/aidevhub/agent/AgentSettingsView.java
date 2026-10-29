package com.fhaicl.aidevhub.agent;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public final class AgentSettingsView extends VBox {
    public AgentSettingsView(AgentPathSettings settings) {
        super(12);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Agent 配置");
        title.setFont(Font.font(24));
        Label description = new Label("设置 Agent 启动程序路径。留空并保存则从 PATH 查找；保存后立即生效，重启后保留。路径不支持 %、!、&、^ 等终端特殊字符。");
        description.setWrapText(true);
        Label status = new Label();
        status.setWrapText(true);
        VBox form = new VBox(12);
        Map<AgentLauncher.Tool, TextField> fields = new EnumMap<>(AgentLauncher.Tool.class);
        try {
            settings.load();
        } catch (IOException | SecurityException exception) {
            status.setText("Agent 配置加载失败，已禁用保存以保护原文件。请检查用户目录下的 .aidevhub/agent-paths.xml 后重启。原因："
                    + exception.getMessage());
            form.setDisable(true);
        }
        for (AgentLauncher.Tool ide : AgentLauncher.Tool.values()) {
            TextField field = new TextField(settings.path(ide) == null ? "" : settings.path(ide).toString());
            field.setPromptText(String.join(" / ", AgentPathSettings.executableNames(ide)));
            fields.put(ide, field);
            Button browse = new Button("浏览…");
            browse.setOnAction(event -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("选择 " + ide.displayName() + " 启动程序");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                        ide.displayName() + " 启动程序", AgentPathSettings.executableNames(ide)));
                File selected = chooser.showOpenDialog(getScene().getWindow());
                if (selected != null) field.setText(selected.toPath().toAbsolutePath().toString());
            });
            Button clear = new Button("清空");
            clear.setOnAction(event -> field.clear());
            HBox row = new HBox(12, field, browse, clear);
            HBox.setHgrow(field, Priority.ALWAYS);
            form.getChildren().addAll(new Label(ide.displayName()), row);
        }
        Button save = new Button("保存 Agent 路径");
        save.setOnAction(event -> {
            Map<AgentLauncher.Tool, String> values = new EnumMap<>(AgentLauncher.Tool.class);
            fields.forEach((ide, field) -> values.put(ide, field.getText()));
            try {
                settings.save(values);
                fields.forEach((ide, field) -> field.setText(
                        settings.path(ide) == null ? "" : settings.path(ide).toString()));
                status.setText("Agent 路径已保存。");
            } catch (IOException | SecurityException exception) {
                status.setText("保存失败，原配置保持不变。原因：" + exception.getMessage());
            }
        });
        form.getChildren().add(save);
        getChildren().addAll(title, description, form, status);
    }
}
