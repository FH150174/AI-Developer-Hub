package com.fhaicl.aidevhub.settings;

import java.io.IOException;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public final class AppSettingsView extends VBox {
    public AppSettingsView(AppSettings settings, Runnable applyTheme, String loadError) {
        super(12);
        Label status = new Label(loadError);
        status.setWrapText(true);
        ComboBox<AppSettings.Terminal> terminal = new ComboBox<>();
        terminal.getItems().setAll(AppSettings.Terminal.values());
        terminal.setValue(settings.values().terminal());
        terminal.setConverter(new javafx.util.StringConverter<>() {
            public String toString(AppSettings.Terminal value) {
                if (value == null) return "";
                return switch (value) {
                    case POWERSHELL_7 -> "PowerShell 7 (pwsh.exe)";
                    case WINDOWS_POWERSHELL -> "Windows PowerShell";
                    case CMD -> "CMD";
                };
            }
            public AppSettings.Terminal fromString(String value) { throw new UnsupportedOperationException(); }
        });
        TextField directory = new TextField(settings.values().projectDirectory());
        directory.setPromptText("留空使用系统默认目录");
        Button browse = new Button("选择默认项目目录…");
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var selected = chooser.showDialog(getScene().getWindow());
            if (selected != null) directory.setText(selected.getAbsolutePath());
        });
        ComboBox<AppSettings.Theme> theme = new ComboBox<>();
        theme.getItems().setAll(AppSettings.Theme.values());
        theme.setValue(settings.values().theme());
        ComboBox<AppSettings.StartPage> startPage = new ComboBox<>();
        startPage.getItems().setAll(AppSettings.StartPage.values());
        startPage.setValue(settings.values().startPage());
        Button save = new Button("保存应用设置");
        save.setOnAction(event -> {
            try {
                settings.save(new AppSettings.Values(terminal.getValue(), directory.getText(), theme.getValue(), startPage.getValue()));
                directory.setText(settings.values().projectDirectory());
                applyTheme.run();
                status.setText("已保存。终端、目录与主题立即生效；启动首页在下次启动生效。");
            } catch (IOException | SecurityException exception) {
                status.setText("保存失败：" + exception.getMessage());
            }
        });
        VBox form = new VBox(12, new Label("Terminal（需已安装并加入 PATH）"), terminal,
                new Label("默认项目目录（添加项目时使用）"), directory, browse,
                new Label("主题：LIGHT 浅色 / DARK 深色"), theme, new Label("启动首页"), startPage, save);
        form.setDisable(!loadError.isEmpty());
        getChildren().addAll(new Label("应用设置"), form, status);
    }
}
