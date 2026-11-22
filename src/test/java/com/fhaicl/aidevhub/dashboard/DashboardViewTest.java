package com.fhaicl.aidevhub.dashboard;

import com.fhaicl.aidevhub.git.GitCommandExecutor;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.settings.AppSettings;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import com.fhaicl.aidevhub.todo.ProjectTodoService;
import com.fhaicl.aidevhub.todo.ProjectTodoStore;
import com.fhaicl.aidevhub.todo.TodoItem;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
@Tag("ui")
class DashboardViewTest {
    @TempDir Path directory;

    @BeforeAll static void startJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    @Test void compactMetricsShowBranchChangesAndTodoWithinTheDefaultContentHeight() throws Exception {
        Path repository = Files.createDirectories(directory.resolve("较长的项目路径".repeat(8)));
        assertEquals(0, new GitCommandExecutor().execute(repository, "init", "--initial-branch=main").exitCode());
        Project project = new Project("长项目名".repeat(24), repository);
        ProjectTodoStore todoStore = new ProjectTodoStore(directory.resolve("todos"));
        todoStore.save(project, List.of(new TodoItem("一"), new TodoItem("二")));
        AtomicReference<DashboardView> reference = new AtomicReference<>();
        onFx(() -> {
            DashboardView view = dashboard(todoStore);
            new Scene(view, 760, 586);
            view.resize(760, 586);
            view.applyCss();
            view.layout();
            view.setProject(project);
            view.refresh();
            reference.set(view);
            return null;
        });
        DashboardView view = reference.get();
        await(() -> label(view, "main") != null
                && label(view, "无变更") != null
                && label(view, "2 项未完成") != null
                && label(view, "暂无提交") != null, "Dashboard 摘要");
        onFx(() -> {
            view.applyCss();
            view.layout();
            Label branch = label(view, "main");
            Label changes = label(view, "无变更");
            Label todo = label(view, "2 项未完成");
            assertNotNull(branch);
            assertNotNull(changes);
            assertNotNull(todo);
            Label commit = label(view, "暂无提交");
            assertNotNull(commit);
            assertEquals("暂无提交", commit.getText());
            Button refresh = control(view, Button.class, "刷新 Dashboard");
            assertSame(refresh.getParent(), control(view, Label.class, "Dashboard").getParent());
            Label current = view.lookupAll(".label").stream().map(Label.class::cast)
                    .filter(node -> node.isVisible() && node.getText().startsWith("当前项目：")).findFirst().orElseThrow();
            assertEquals("当前项目：" + project.name() + "\n路径：" + repository, current.getTooltip().getText());
            var refreshBounds = refresh.localToScene(refresh.getLayoutBounds());
            assertTrue(refreshBounds.getMaxX() <= view.getScene().getWidth() && refreshBounds.getMinY() >= 0);
            javafx.scene.control.TitledPane explanation = assertInstanceOf(
                    javafx.scene.control.TitledPane.class, view.lookup(".stats-explanation"));
            assertEquals("统计说明", explanation.getText());
            assertFalse(explanation.isExpanded());
            double contentHeight = view.prefHeight(760);
            assertTrue(contentHeight <= 586,
                    "Dashboard content height " + contentHeight + " should fit the default content viewport");
            assertTrue(branch.localToScene(branch.getBoundsInLocal()).getMaxY() <= 586);
            assertTrue(changes.localToScene(changes.getBoundsInLocal()).getMaxY() <= 586);
            assertTrue(todo.localToScene(todo.getBoundsInLocal()).getMaxY() <= 586);
            return null;
        });
    }

    @Test void nonRepositorySummaryDoesNotPretendTheChangeCountIsZero() throws Exception {
        Path plain = Files.createDirectories(directory.resolve("plain"));
        Project project = new Project("Plain", plain);
        ProjectTodoStore todoStore = new ProjectTodoStore(directory.resolve("todos"));
        AtomicReference<DashboardView> reference = new AtomicReference<>();
        onFx(() -> {
            DashboardView view = dashboard(todoStore);
            new Scene(view, 760, 586);
            view.resize(760, 586);
            view.applyCss();
            view.layout();
            view.setProject(project);
            view.refresh();
            reference.set(view);
            return null;
        });
        DashboardView view = reference.get();
        await(() -> label(view, "不是 Git 仓库") != null, "Dashboard 非 Git 仓库提示");
        onFx(() -> {
            assertEquals("不是 Git 仓库", label(view, "不是 Git 仓库").getText());
            assertNull(label(view, "0 个文件变更"));
            assertNull(label(view, "main"));
            return null;
        });
    }

    private DashboardView dashboard(ProjectTodoStore todoStore) {
        return new DashboardView(new ProjectTodoService(todoStore),
                new ProjectLogStore(directory.resolve("notes")),
                new IdePathSettings(directory.resolve("ide-paths.xml")),
                new AppSettings(directory.resolve("settings.xml")));
    }

    private static Label label(Parent view, String text) {
        return view.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> text.equals(label.getText())).findFirst().orElse(null);
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
