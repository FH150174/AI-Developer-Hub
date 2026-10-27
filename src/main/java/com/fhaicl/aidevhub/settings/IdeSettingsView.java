package com.fhaicl.aidevhub.settings;

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

public final class IdeSettingsView extends VBox {
    public IdeSettingsView(IdePathSettings settings) {
        super(12);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Settings");
        title.setFont(Font.font(24));
        Label description = new Label("设置 IDE 启动程序路径。留空并保存可清除配置；保存后立即生效，重启后保留。");
        description.setWrapText(true);
        Label status = new Label();
        status.setWrapText(true);
        VBox form = new VBox(12);
        Map<IdePathSettings.Ide, TextField> fields = new EnumMap<>(IdePathSettings.Ide.class);
        try {
            settings.load();
        } catch (IOException | SecurityException exception) {
            status.setText("IDE 配置加载失败，已禁用保存以保护原文件。请检查用户目录下的 .aidevhub/ide-paths.xml 后重启。原因："
                    + exception.getMessage());
            form.setDisable(true);
        }
        for (IdePathSettings.Ide ide : IdePathSettings.Ide.values()) {
            TextField field = new TextField(settings.path(ide) == null ? "" : settings.path(ide).toString());
            field.setPromptText(String.join(" / ", ide.executableNames()));
            fields.put(ide, field);
            Button browse = new Button("浏览…");
            browse.setOnAction(event -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("选择 " + ide.displayName() + " 启动程序");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                        ide.displayName() + " 启动程序", ide.executableNames()));
                File selected = chooser.showOpenDialog(getScene().getWindow());
                if (selected != null) field.setText(selected.toPath().toAbsolutePath().toString());
            });
            Button clear = new Button("清空");
            clear.setOnAction(event -> field.clear());
            HBox row = new HBox(12, field, browse, clear);
            HBox.setHgrow(field, Priority.ALWAYS);
            form.getChildren().addAll(new Label(ide.displayName()), row);
        }
        Button save = new Button("保存 IDE 路径");
        save.setOnAction(event -> {
            Map<IdePathSettings.Ide, String> values = new EnumMap<>(IdePathSettings.Ide.class);
            fields.forEach((ide, field) -> values.put(ide, field.getText()));
            try {
                settings.save(values);
                fields.forEach((ide, field) -> field.setText(
                        settings.path(ide) == null ? "" : settings.path(ide).toString()));
                status.setText("IDE 路径已保存。");
            } catch (IOException | SecurityException exception) {
                status.setText("保存失败，原配置保持不变。原因：" + exception.getMessage());
            }
        });
        form.getChildren().add(save);
        getChildren().addAll(title, description, form, status);
    }
}
