package com.fhaicl.aidevhub.environment;

import javafx.concurrent.Task;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import java.util.concurrent.Callable;
import java.util.HashMap;
import java.util.Objects;
import javafx.scene.control.TextArea;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.EnumMap;
import java.util.Map;

public final class EnvironmentView extends VBox {
    private final EnvironmentVersionService service = new EnvironmentVersionService();
    private final Map<EnvironmentVersionService.Tool, Label> labels = new EnumMap<>(EnvironmentVersionService.Tool.class);
    private final Map<EnvironmentVersionService.Tool, Task<String>> tasks = new EnumMap<>(EnvironmentVersionService.Tool.class);

    private final IdePathSettings settings;
    private final EnvironmentDiagnosticsService diagnostics = new EnvironmentDiagnosticsService();
    private final Map<IdePathSettings.Ide, Label> ideLabels = new EnumMap<>(IdePathSettings.Ide.class);
    private final Map<String, Task<String>> diagnosticTasks = new HashMap<>();
    private final Label wsl = new Label("WSL：尚未检测");
    private final TextArea pathReport = new TextArea();

    public EnvironmentView(IdePathSettings settings) {
        super(12);
        this.settings = Objects.requireNonNull(settings);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Environment");
        title.setFont(Font.font(24));
        Label scope = new Label("检测应用继承的 PATH 中的工具版本。Java 为 java 命令版本，可能与运行本应用的 JDK 不同。修改环境变量后请重启应用。");
        scope.setWrapText(true);
        Button refresh = new Button("刷新环境信息");
        refresh.setOnAction(event -> refresh());
        getChildren().addAll(title, scope, refresh);
        for (var tool : EnvironmentVersionService.Tool.values()) {
            Label label = new Label(tool.displayName() + "：尚未检测");
            label.setWrapText(true);
            labels.put(tool, label);
            getChildren().add(label);
        }
        for (var ide : IdePathSettings.Ide.values()) {
            Label label = new Label(ide.displayName() + " 路径：尚未检测");
            label.setWrapText(true);
            ideLabels.put(ide, label);
            getChildren().add(label);
        }
        wsl.setWrapText(true);
        pathReport.setEditable(false);
        pathReport.setWrapText(true);
        pathReport.setPrefRowCount(10);
        pathReport.setMinHeight(180);
        getChildren().addAll(wsl, new Label("PATH 检查（应用启动时继承的环境）"), pathReport);
    }

    public void refresh() {
        for (var ide : IdePathSettings.Ide.values()) {
            Label label = ideLabels.get(ide);
            String key = ide.name();
            java.nio.file.Path path;
            try {
                path = settings.requirePath(ide);
            } catch (java.io.IOException exception) {
                Task<String> previous = diagnosticTasks.remove(key);
                if (previous != null) previous.cancel(true);
                label.setText(ide.displayName() + " 路径：" + exception.getMessage());
                continue;
            }
            label.setText(ide.displayName() + " 路径：正在检查…");
            runDiagnostic(key, () -> diagnostics.idePath(path),
                    text -> label.setText(ide.displayName() + " 路径：" + text));
        }
        wsl.setText("WSL：正在检测…");
        runDiagnostic("WSL", diagnostics::wslStatus, text -> wsl.setText("WSL：" + text));
        pathReport.setText("正在检查 PATH…");
        runDiagnostic("PATH", () -> diagnostics.checkPath(System.getenv("PATH")), pathReport::setText);
        for (var tool : EnvironmentVersionService.Tool.values()) {
            Task<String> previous = tasks.remove(tool);
            if (previous != null) previous.cancel(true);
            Label label = labels.get(tool);
            label.setText(tool.displayName() + "：正在检测…");
            Task<String> task = new Task<>() {
                @Override protected String call() throws Exception { return service.version(tool); }
            };
            tasks.put(tool, task);
            task.setOnSucceeded(event -> {
                if (tasks.get(tool) != task) return;
                tasks.remove(tool);
                label.setText(tool.displayName() + "：" + task.getValue());
            });
            task.setOnFailed(event -> {
                if (tasks.get(tool) != task) return;
                tasks.remove(tool);
                label.setText(tool.displayName() + "：" + task.getException().getMessage());
                System.getLogger(EnvironmentView.class.getName()).log(System.Logger.Level.WARNING,
                        tool.displayName() + " version detection failed", task.getException());
            });
            Thread thread = new Thread(task, "environment-version-" + tool.name().toLowerCase(java.util.Locale.ROOT));
            thread.setDaemon(true);
            thread.start();
        }
    }
    private void runDiagnostic(String key, Callable<String> action, java.util.function.Consumer<String> display) {
        Task<String> previous = diagnosticTasks.remove(key);
        if (previous != null) previous.cancel(true);
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception { return action.call(); }
        };
        diagnosticTasks.put(key, task);
        task.setOnSucceeded(event -> {
            if (diagnosticTasks.get(key) != task) return;
            diagnosticTasks.remove(key);
            display.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (diagnosticTasks.get(key) != task) return;
            diagnosticTasks.remove(key);
            display.accept("检查失败：" + task.getException().getMessage());
            System.getLogger(EnvironmentView.class.getName()).log(System.Logger.Level.WARNING,
                    key + " diagnostic failed", task.getException());
        });
        Thread thread = new Thread(task, "environment-check-" + key);
        thread.setDaemon(true);
        thread.start();
    }
}
