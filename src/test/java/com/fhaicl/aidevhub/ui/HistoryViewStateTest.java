package com.fhaicl.aidevhub.ui;

import com.fhaicl.aidevhub.navigation.MainView;
import com.fhaicl.aidevhub.notes.DevelopmentLog;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.notes.ProjectLogsView;
import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.todo.ProjectTodoStore;
import com.fhaicl.aidevhub.todo.ProjectTodoView;
import com.fhaicl.aidevhub.todo.TodoItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledOnOs(OS.WINDOWS)
@Tag("ui")
class HistoryViewStateTest {
    @TempDir Path directory;

    @BeforeAll static void startJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    @Test void placeholderShowsOnlyTheCurrentStateAndWiresItsActions() throws Exception {
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger retried = new AtomicInteger();
        AtomicReference<ProjectHistoryPlaceholder> reference = new AtomicReference<>();
        onFx(() -> {
            ProjectHistoryPlaceholder placeholder = new ProjectHistoryPlaceholder(
                    "开发日志", "记录今天的第一条进展吧。", opened::incrementAndGet, retried::incrementAndGet);
            placeholder.noProject();
            assertTrue(text(placeholder).contains("尚未选择当前项目"));
            assertTrue(text(placeholder).contains("请到 Projects 选中项目"));
            assertEquals("前往 Projects", button(placeholder, "前往 Projects").getText());
            button(placeholder, "前往 Projects").fire();
            assertEquals(1, opened.get());

            placeholder.loading("A");
            assertEquals("正在读取 A 的开发日志…", text(placeholder));
            assertTrue(buttons(placeholder).isEmpty());

            placeholder.empty("A");
            assertTrue(text(placeholder).contains("A 还没有开发日志。"));
            assertTrue(text(placeholder).contains("记录今天的第一条进展吧。"));
            assertTrue(buttons(placeholder).isEmpty());

            placeholder.failed(new IOException("日志文件损坏，原文件已保留。"));
            assertTrue(text(placeholder).contains("开发日志读取失败"));
            assertTrue(text(placeholder).contains("日志文件损坏"));
            button(placeholder, "重新读取").fire();
            assertEquals(1, retried.get());

            placeholder.failed(new AccessDeniedException("private"));
            assertTrue(text(placeholder).contains("读取权限不足"));
            reference.set(placeholder);
            return null;
        });
        assertNotNull(reference.get());
    }

    @Test void notesGuideNoProjectThenEmptyProjectAndClearThePreviousProject() throws Exception {
        Project a = project("a");
        Project b = project("b");
        ProjectLogStore store = new ProjectLogStore(directory.resolve("notes"));
        store.save(b, List.of(new DevelopmentLog("B日志", "B正文")));
        AtomicBoolean openedProjects = new AtomicBoolean();
        AtomicReference<ProjectLogsView> reference = new AtomicReference<>();
        onFx(() -> {
            ProjectLogsView view = new ProjectLogsView(store, () -> openedProjects.set(true));
            new Scene(view, 960, 640);
            view.applyCss();
            view.layout();
            reference.set(view);
            return null;
        });
        ProjectLogsView view = reference.get();

        onFx(() -> {
            assertTrue(placeholderText(view).contains("尚未选择当前项目"));
            assertTrue(control(view, Button.class, "新建日志").isDisabled());
            control(view, Button.class, "前往 Projects").fire();
            return null;
        });
        assertTrue(openedProjects.get());

        onFx(() -> {
            view.setProject(a);
            return null;
        });
        await(() -> placeholderText(view).contains("A 还没有开发日志。"), "Notes 空项目提示");
        onFx(() -> {
            assertEquals(0, list(view).getItems().size());
            assertFalse(control(view, Button.class, "新建日志").isDisabled());
            assertFalse(placeholderText(view).contains("B日志"));
            return null;
        });

        onFx(() -> {
            view.setProject(b);
            return null;
        });
        await(() -> list(view).getItems().size() == 1, "Notes 切换到 B");
        onFx(() -> {
            ListView<?> logs = list(view);
            assertNull(logs.getSelectionModel().getSelectedItem());
            assertEquals("", content(view).getText());
            assertEquals("当前项目：B\n路径：" + b.path(), projectLabel(view).getText());
            logs.getSelectionModel().select(0);
            assertTrue(content(view).getText().contains("B正文"));
            return null;
        });

        onFx(() -> {
            view.setProject(a);
            return null;
        });
        onFx(() -> {
            assertEquals(0, list(view).getItems().size());
            assertEquals("", content(view).getText());
            assertEquals("当前项目：A\n路径：" + a.path(), projectLabel(view).getText());
            return null;
        });
        await(() -> placeholderText(view).contains("A 还没有开发日志。"), "Notes 切回空项目 A");
    }

    @Test void todoGuideNoProjectThenEmptyProjectAndClearThePreviousProject() throws Exception {
        Project a = project("a");
        Project b = project("b");
        ProjectTodoStore store = new ProjectTodoStore(directory.resolve("todos"));
        store.save(a, List.of(new TodoItem("A任务")));
        AtomicReference<ProjectTodoView> reference = new AtomicReference<>();
        onFx(() -> {
            ProjectTodoView view = new ProjectTodoView(store, () -> { });
            new Scene(view, 960, 640);
            view.applyCss();
            view.layout();
            reference.set(view);
            return null;
        });
        ProjectTodoView view = reference.get();

        onFx(() -> {
            assertTrue(placeholderText(view).contains("尚未选择当前项目"));
            assertTrue(control(view, Button.class, "添加任务").isDisabled());
            return null;
        });

        onFx(() -> {
            view.setProject(b);
            return null;
        });
        await(() -> placeholderText(view).contains("B 还没有待办。"), "TODO 空项目提示");
        onFx(() -> {
            assertEquals(0, list(view).getItems().size());
            assertFalse(control(view, Button.class, "添加任务").isDisabled());
            return null;
        });

        onFx(() -> {
            view.setProject(a);
            return null;
        });
        await(() -> list(view).getItems().size() == 1, "TODO 切换到 A");
        onFx(() -> {
            assertEquals("A任务", ((TodoItem) list(view).getItems().getFirst()).title());
            view.setProject(b);
            return null;
        });
        onFx(() -> {
            assertEquals(0, list(view).getItems().size());
            assertFalse(placeholderText(view).contains("A任务"));
            return null;
        });
        await(() -> placeholderText(view).contains("B 还没有待办。"), "TODO 清空旧项目任务");
    }

    @Test void notesReadFailureKeepsWritesDisabledAndRetryLoadsAfterRepair() throws Exception {
        Project a = project("a");
        Path notes = directory.resolve("notes");
        ProjectLogStore store = new ProjectLogStore(notes);
        store.save(a, List.of(new DevelopmentLog("旧日志", "正文")));
        Path history = onlyFile(notes);
        Files.writeString(history, "not XML", StandardCharsets.UTF_8);
        AtomicReference<ProjectLogsView> reference = new AtomicReference<>();
        onFx(() -> {
            ProjectLogsView view = new ProjectLogsView(store, () -> { });
            new Scene(view, 960, 640);
            view.applyCss();
            view.layout();
            view.setProject(a);
            reference.set(view);
            return null;
        });
        ProjectLogsView view = reference.get();

        await(() -> placeholderText(view).contains("开发日志读取失败"), "Notes 读取失败提示");
        onFx(() -> {
            assertTrue(placeholderText(view).contains("损坏"));
            assertFalse(placeholderText(view).contains("还没有开发日志"));
            assertTrue(control(view, Button.class, "新建日志").isDisabled());
            assertEquals(0, list(view).getItems().size());
            return null;
        });

        Files.delete(history);
        onFx(() -> {
            control(view, Button.class, "重新读取").fire();
            return null;
        });
        await(() -> placeholderText(view).contains("A 还没有开发日志。"), "Notes 修复后重试");
        onFx(() -> {
            assertFalse(control(view, Button.class, "新建日志").isDisabled());
            return null;
        });
    }

    @Test void todoReadFailureKeepsWritesDisabledAndRetryLoadsAfterRepair() throws Exception {
        Project a = project("a");
        Path todos = directory.resolve("todos");
        ProjectTodoStore store = new ProjectTodoStore(todos);
        store.save(a, List.of(new TodoItem("旧任务")));
        Path history = onlyFile(todos);
        Files.writeString(history, "not XML", StandardCharsets.UTF_8);
        AtomicReference<ProjectTodoView> reference = new AtomicReference<>();
        onFx(() -> {
            ProjectTodoView view = new ProjectTodoView(store, () -> { });
            new Scene(view, 960, 640);
            view.applyCss();
            view.layout();
            view.setProject(a);
            reference.set(view);
            return null;
        });
        ProjectTodoView view = reference.get();

        await(() -> placeholderText(view).contains("待办读取失败"), "TODO 读取失败提示");
        onFx(() -> {
            assertTrue(placeholderText(view).contains("损坏"));
            assertFalse(placeholderText(view).contains("还没有待办"));
            assertTrue(control(view, Button.class, "添加任务").isDisabled());
            assertEquals(0, list(view).getItems().size());
            return null;
        });

        Files.delete(history);
        onFx(() -> {
            control(view, Button.class, "重新读取").fire();
            return null;
        });
        await(() -> placeholderText(view).contains("A 还没有待办。"), "TODO 修复后重试");
        onFx(() -> {
            assertFalse(control(view, Button.class, "添加任务").isDisabled());
            return null;
        });
    }

    @Test void ambiguousHistoryExplainsBothFilesAndReloadsAfterResolution() throws Exception {
        Path actual = Files.createDirectory(directory.resolve("Demo"));
        Path alias = directory.resolve("DEMO");
        assumeTrue(Files.isDirectory(alias), "Requires a case-insensitive directory");
        Path notes = directory.resolve("notes");
        Project project = new Project("Demo", actual);
        Path first = legacy(notes, actual, "第一份");
        Path second = legacy(notes, alias, "第二份");
        AtomicReference<ProjectLogsView> reference = new AtomicReference<>();
        onFx(() -> {
            ProjectLogsView view = new ProjectLogsView(new ProjectLogStore(notes), () -> { });
            new Scene(view, 960, 640);
            view.applyCss();
            view.layout();
            view.setProject(project);
            reference.set(view);
            return null;
        });
        ProjectLogsView view = reference.get();

        await(() -> placeholderText(view).contains("开发日志读取失败"), "Notes 冲突提示");
        onFx(() -> {
            assertTrue(placeholderText(view).contains("冲突"));
            assertTrue(placeholderText(view).contains(first.toString()));
            assertTrue(placeholderText(view).contains(second.toString()));
            assertFalse(placeholderText(view).contains("还没有开发日志"));
            assertTrue(control(view, Button.class, "新建日志").isDisabled());
            assertEquals("处理后重新读取", control(view, Button.class, "处理后重新读取").getText());
            return null;
        });

        Files.delete(second);
        onFx(() -> {
            control(view, Button.class, "处理后重新读取").fire();
            return null;
        });
        await(() -> list(view).getItems().size() == 1, "Notes 冲突处理后重试");
        onFx(() -> {
            assertEquals("第一份", ((DevelopmentLog) list(view).getItems().getFirst()).title());
            assertFalse(control(view, Button.class, "新建日志").isDisabled());
            return null;
        });
    }

    @Test void emptyStateActionIsReachableWithTheKeyboard() throws Exception {
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<ProjectLogsView> reference = new AtomicReference<>();
        onFx(() -> {
            ProjectLogsView view = new ProjectLogsView(
                    new ProjectLogStore(directory.resolve("notes")), () -> { });
            Stage window = new Stage();
            window.setScene(new Scene(view, 960, 640));
            window.show();
            stage.set(window);
            reference.set(view);
            return null;
        });
        ProjectLogsView view = reference.get();
        try {
            onFx(() -> {
                ListView<?> logs = list(view);
                logs.requestFocus();
                assertSame(logs, view.getScene().getFocusOwner());
                press(logs, KeyCode.TAB);
                assertTrue(view.getScene().getFocusOwner() instanceof Button button
                        && "前往 Projects".equals(button.getText()));
                return null;
            });
        } finally {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    @Test void emptyGuidanceOpensProjectsFromTheSharedNavigation() throws Exception {
        AtomicReference<MainView> reference = new AtomicReference<>();
        onFx(() -> {
            MainView root = new MainView(directory.resolve("navigation"));
            new Scene(root, 960, 640);
            root.applyCss();
            root.layout();
            reference.set(root);
            return null;
        });
        MainView root = reference.get();
        onFx(() -> {
            ListView<?> navigation = (ListView<?>) root.lookup(".navigation");
            navigation.getSelectionModel().select(navigation.getItems().indexOf("Notes"));
            root.applyCss();
            root.layout();
            assertEquals("Notes", navigation.getSelectionModel().getSelectedItem());
            control(root, Button.class, "前往 Projects").fire();
            assertEquals("Projects", navigation.getSelectionModel().getSelectedItem());
            return null;
        });
    }

    private Project project(String name) throws IOException {
        return new Project(name.toUpperCase(), Files.createDirectory(directory.resolve(name)));
    }

    private static Path onlyFile(Path storage) throws IOException {
        try (var files = Files.list(storage)) {
            return files.findFirst().orElseThrow();
        }
    }

    private static Path legacy(Path storage, Path project, String title) throws Exception {
        String key = project.toAbsolutePath().normalize().toString();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        Path file = Files.createDirectories(storage).resolve(HexFormat.of().formatHex(hash) + ".xml");
        Properties data = new Properties();
        data.setProperty("project.path", key);
        data.setProperty("count", "1");
        data.setProperty("0.title", title);
        data.setProperty("0.content", "正文");
        try (var output = Files.newOutputStream(file)) {
            data.storeToXML(output, "v1.0 fixture");
        }
        return file;
    }

    private static ListView<?> list(Parent view) {
        return view.lookupAll("*").stream()
                .filter(ListView.class::isInstance).map(ListView.class::cast)
                .filter(list -> list.getPlaceholder() instanceof ProjectHistoryPlaceholder)
                .findFirst().orElseThrow();
    }

    private static ProjectHistoryPlaceholder placeholder(Parent view) {
        return assertInstanceOf(ProjectHistoryPlaceholder.class, list(view).getPlaceholder());
    }

    private static String placeholderText(Parent view) {
        return text(placeholder(view));
    }

    private static String text(Parent root) {
        return root.lookupAll("*").stream()
                .filter(Labeled.class::isInstance).map(Labeled.class::cast)
                .map(Labeled::getText).filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private static Label projectLabel(Parent view) {
        return view.lookupAll("*").stream()
                .filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> label.getText() != null && label.getText().startsWith("当前项目："))
                .findFirst().orElseThrow();
    }

    private static TextArea content(Parent view) {
        return (TextArea) view.lookupAll("*").stream()
                .filter(TextArea.class::isInstance).findFirst().orElseThrow();
    }

    private static List<Button> buttons(Parent root) {
        return root.lookupAll("*").stream()
                .filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static Button button(Parent root, String text) {
        return control(root, Button.class, text);
    }

    private static <T extends Labeled> T control(Parent view, Class<T> type, String text) {
        return view.lookupAll("*").stream().filter(type::isInstance).map(type::cast)
                .filter(node -> text.equals(node.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("找不到 " + type.getSimpleName() + "：" + text));
    }

    private static void press(javafx.scene.Node target, KeyCode key) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
        target.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, false, false, false));
    }

    private static void await(Callable<Boolean> condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (callOnFx(condition)) {
                return;
            }
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
