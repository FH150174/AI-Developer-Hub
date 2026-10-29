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
        Label commandHelp = new Label("自定义命令优先于启动程序路径，按单行 CMD 语法执行，不自动追加参数或首条提示。留空恢复默认启动。可使用 %AIDEVHUB_PROJECT_PATH% 和 %AIDEVHUB_PROJECT_CONTEXT% 环境变量；含空格的路径请加双引号。");
        commandHelp.setWrapText(true);
        Label status = new Label();
        status.setWrapText(true);
        VBox form = new VBox(12);
        Map<AgentLauncher.Tool, TextField> fields = new EnumMap<>(AgentLauncher.Tool.class);
        Map<AgentLauncher.Tool, TextField> commands = new EnumMap<>(AgentLauncher.Tool.class);
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
            TextField command = new TextField(settings.command(ide));
            command.setPromptText("自定义 CMD 命令（留空使用默认启动）");
            commands.put(ide, command);
            Button clearCommand = new Button("清空命令");
            clearCommand.setOnAction(event -> command.clear());
            HBox commandRow = new HBox(12, command, clearCommand);
            HBox.setHgrow(command, Priority.ALWAYS);
            form.getChildren().addAll(new Label(ide.displayName()), row, commandRow);
        }
        Button save = new Button("保存 Agent 配置");
        save.setOnAction(event -> {
            Map<AgentLauncher.Tool, String> values = new EnumMap<>(AgentLauncher.Tool.class);
            fields.forEach((ide, field) -> values.put(ide, field.getText()));
            try {
                Map<AgentLauncher.Tool, String> commandValues = new EnumMap<>(AgentLauncher.Tool.class);
                commands.forEach((tool, field) -> commandValues.put(tool, field.getText()));
                settings.save(values, commandValues);
                commands.forEach((tool, field) -> field.setText(settings.command(tool)));
                fields.forEach((ide, field) -> field.setText(
                        settings.path(ide) == null ? "" : settings.path(ide).toString()));
                status.setText("Agent 配置已保存。");
            } catch (IOException | SecurityException exception) {
                status.setText("保存失败，原配置保持不变。原因：" + exception.getMessage());
            }
        });
        form.getChildren().add(save);
        getChildren().addAll(title, description, commandHelp, form, status);
    }
}
