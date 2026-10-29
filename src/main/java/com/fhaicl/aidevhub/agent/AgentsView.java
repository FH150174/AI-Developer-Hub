package com.fhaicl.aidevhub.agent;

import javafx.concurrent.Task;
import com.fhaicl.aidevhub.project.Project;
import java.util.EnumMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public final class AgentsView extends VBox {
    private final AgentLauncher launcher = new AgentLauncher();

    private Project project;
    private final TextArea contextPreview = new TextArea();
    private final Button copyContext = new Button("复制项目上下文");
    private Task<String> contextTask;
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
        Label contextHelp = new Label("Codex / Claude 启动后会收到读取上下文的首条提示。Antigravity / PowerShell / WSL 携带 AIDEVHUB_PROJECT_CONTEXT 环境变量，可复制下方上下文用于聊天。");
        contextHelp.setWrapText(true);
        contextPreview.setEditable(false);
        contextPreview.setWrapText(true);
        contextPreview.setPrefRowCount(5);
        contextPreview.setMinHeight(100);
        copyContext.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(contextPreview.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        getChildren().addAll(title, description, currentProject, contextHelp, contextPreview, copyContext);
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
        if (contextTask != null) {
            contextTask.cancel(true);
            contextTask = null;
        }
        copyContext.setDisable(true);
        contextPreview.clear();
        if (project != null) loadContext(project);
        currentProject.setText(project == null ? "未选择当前项目，请先在 Projects 中设为当前项目。"
                : "当前项目：" + project.name() + "\n路径：" + project.path());
        // A launch already requested keeps its captured project; its status belongs to that selection.
        activeTasks.clear();
        buttons.values().forEach(button -> button.setDisable(project == null));
        statuses.values().forEach(label -> label.setText(""));
    }

    private void loadContext(Project selected) {
        contextPreview.setText("正在准备项目上下文…");
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return new ProjectContextService().context(selected);
            }
        };
        contextTask = task;
        task.setOnSucceeded(event -> {
            if (contextTask != task) return;
            contextTask = null;
            contextPreview.setText(task.getValue());
            copyContext.setDisable(false);
        });
        task.setOnFailed(event -> {
            if (contextTask != task) return;
            contextTask = null;
            contextPreview.setText("上下文准备失败，请检查项目目录及访问权限。");
            System.getLogger(AgentsView.class.getName()).log(System.Logger.Level.WARNING,
                    "Project context loading failed", task.getException());
        });
        Thread thread = new Thread(task, "agent-project-context");
        thread.setDaemon(true);
        thread.start();
    }
}
