package com.fhaicl.aidevhub.agent;

import javafx.concurrent.Task;
import com.fhaicl.aidevhub.project.Project;
import java.util.EnumMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public final class AgentsView extends VBox {
    private final AgentLauncher launcher = new AgentLauncher();

    private Project project;
    private final Label currentProject = new Label();
    private final Map<AgentLauncher.Tool, Button> buttons = new EnumMap<>(AgentLauncher.Tool.class);
    private final Map<AgentLauncher.Tool, Label> statuses = new EnumMap<>(AgentLauncher.Tool.class);
    private final Map<AgentLauncher.Tool, Task<Void>> activeTasks = new EnumMap<>(AgentLauncher.Tool.class);

    public AgentsView() {
        super(12);
        setPadding(new Insets(24));
        setMinWidth(0);
        Label title = new Label("Agents");
        title.setFont(Font.font(24));
        Label description = new Label("从当前项目目录打开独立窗口。请先安装对应工具并加入 PATH；WSL 使用默认发行版。CLI 登录及启动错误请查看打开的终端。");
        description.setWrapText(true);
        currentProject.setWrapText(true);
        getChildren().addAll(title, description, currentProject);
        for (var tool : AgentLauncher.Tool.values()) {
            Label name = new Label(tool.displayName());
            name.setMinWidth(110);
            Button launch = new Button("启动");
            buttons.put(tool, launch);
            Label status = new Label();
            statuses.put(tool, status);
            status.setWrapText(true);
            HBox row = new HBox(12, name, launch, status);
            HBox.setHgrow(status, Priority.ALWAYS);
            launch.setOnAction(event -> {
                Project selected = project;
                if (selected == null) return;
                launch.setDisable(true);
                status.setText("正在启动…");
                Task<Void> task = new Task<>() {
                    @Override protected Void call() throws Exception {
                        launcher.launch(tool, selected);
                        return null;
                    }
                };
                activeTasks.put(tool, task);
                task.setOnSucceeded(done -> {
                    if (activeTasks.get(tool) != task) return;
                    activeTasks.remove(tool);
                    launch.setDisable(project == null);
                    status.setText("已发送启动请求，请查看独立窗口。");
                });
                task.setOnFailed(done -> {
                    if (activeTasks.get(tool) != task) return;
                    activeTasks.remove(tool);
                    launch.setDisable(project == null);
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
        setProject(null);
    }

    public void setProject(Project project) {
        this.project = project;
        currentProject.setText(project == null ? "未选择当前项目，请先在 Projects 中设为当前项目。"
                : "当前项目：" + project.name() + "\n路径：" + project.path());
        // A launch already requested keeps its captured project; its status belongs to that selection.
        activeTasks.clear();
        buttons.values().forEach(button -> button.setDisable(project == null));
        statuses.values().forEach(label -> label.setText(""));
    }
}
