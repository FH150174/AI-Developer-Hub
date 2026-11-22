package com.fhaicl.aidevhub.agent;

import com.fhaicl.aidevhub.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
@Tag("ui")
class AgentsViewTest {
    @TempDir Path directory;

    @BeforeAll static void startJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    @Test void toolsAreBeforeContextAndCanLaunchWithNamedButtons() throws Exception {
        Path projectPath = Files.createDirectories(directory.resolve("project"));
        Files.writeString(projectPath.resolve("AGENTS.md"), "project rules");
        Project project = new Project("Demo", projectPath);
        AgentLauncher launcher = new AgentLauncher("Windows 11", directory.toString(),
                path -> true, builder -> { });
        AtomicReference<AgentsView> reference = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        onFx(() -> {
            AgentsView view = new AgentsView(launcher);
            Stage window = new Stage();
            window.setScene(new Scene(view, 960, 640));
            window.show();
            view.applyCss();
            view.layout();
            view.setProject(project);
            Button copy = control(view, Button.class, "复制项目上下文");
            assertTrue(copy.isDisabled());
            assertFalse(control(view, Button.class, "启动 Codex CLI").isDisabled());
            reference.set(view);
            stage.set(window);
            return null;
        });
        AgentsView view = reference.get();
        try {
            await(() -> !control(view, Button.class, "复制项目上下文").isDisabled(), "上下文准备完成");
            onFx(() -> {
                List<String> expected = List.of("启动 Codex CLI", "启动 Claude CLI", "启动 Antigravity",
                        "启动 PowerShell", "启动 WSL");
                List<Button> launches = view.lookupAll(".button").stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getText().startsWith("启动 "))
                        .toList();
                assertEquals(expected, launches.stream().map(Button::getText).toList());
                TitledPane context = assertInstanceOf(TitledPane.class, view.lookup(".agent-context"));
                assertEquals("查看项目上下文", context.getText());
                assertFalse(context.isExpanded());
                assertTrue(view.getChildren().indexOf(context) > view.getChildren().indexOf(launches.getFirst().getParent()));
                Button codex = control(view, Button.class, "启动 Codex CLI");
                codex.requestFocus();
                assertSame(codex, view.getScene().getFocusOwner());
                codex.fire();
                return null;
            });
            await(() -> status(view, "已发送启动请求，请查看独立窗口。"), "成功启动反馈");
            onFx(() -> {
                assertFalse(control(view, Button.class, "启动 Codex CLI").isDisabled());
                return null;
            });
        } finally {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    @Test void launchFailureIsReportedAndKeepsTheButtonAvailable() throws Exception {
        Path projectPath = Files.createDirectories(directory.resolve("project"));
        Project project = new Project("Demo", projectPath);
        AgentLauncher launcher = new AgentLauncher("Windows 11", directory.toString(),
                path -> true, builder -> {
                    throw new IOException("cannot create process");
                });
        AtomicReference<AgentsView> reference = new AtomicReference<>();
        onFx(() -> {
            AgentsView view = new AgentsView(launcher);
            new Scene(view, 960, 640);
            view.applyCss();
            view.layout();
            view.setProject(project);
            reference.set(view);
            return null;
        });
        AgentsView view = reference.get();
        await(() -> !control(view, Button.class, "复制项目上下文").isDisabled(), "上下文准备完成");
        onFx(() -> {
            control(view, Button.class, "启动 Claude CLI").fire();
            return null;
        });
        await(() -> statusStartsWith(view, "启动失败："), "启动失败反馈");
        onFx(() -> {
            assertTrue(statusStartsWith(view, "启动失败："));
            assertFalse(control(view, Button.class, "启动 Claude CLI").isDisabled());
            return null;
        });
    }

    private static boolean status(Parent view, String exact) {
        return view.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> exact.equals(label.getText()));
    }

    private static boolean statusStartsWith(Parent view, String prefix) {
        return view.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> label.getText() != null && label.getText().startsWith(prefix));
    }

    private static <T extends javafx.scene.control.Labeled> T control(Parent view, Class<T> type, String text) {
        return view.lookupAll("*").stream().filter(type::isInstance).map(type::cast)
                .filter(node -> text.equals(node.getText())).findFirst().orElseThrow();
    }

    private static void await(Callable<Boolean> condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (callOnFx(condition)) return;
            Thread.sleep(25);
        }
        fail("等待超时：" + description);
    }

    private static void onFx(Callable<Void> action) throws Exception {
        callOnFx(action);
    }

    private static <T> T callOnFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
