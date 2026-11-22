package com.fhaicl.aidevhub.project;

import com.fhaicl.aidevhub.navigation.MainView;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.settings.AppSettings;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import com.fhaicl.aidevhub.ui.Ui;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
@Tag("ui")
class ProjectListViewTest {
    private enum Input { MOUSE, KEYBOARD }

    @TempDir Path directory;
    private Path file;
    private ProjectStore store;
    private Project a, b, workspaceProject;
    private Path terminalDirectory;

    @BeforeAll static void startJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    @BeforeEach void saveTwoProjects() throws Exception {
        file = directory.resolve("projects.xml");
        store = new ProjectStore(file);
        a = new Project("A", Files.createDirectory(directory.resolve("a")));
        b = new Project("B", Files.createDirectory(directory.resolve("b")));
        store.save(List.of(), List.of(a, b));
    }

    @Test void keyboardOpensSelectedProjectWhileMarkerAndWorkspaceStayOnCurrentProject() throws Exception {
        onFx(() -> {
            ProjectListView view = view();
            Ui.theme(view, AppSettings.Theme.DARK);
            Stage stage = new Stage();
            stage.setScene(new Scene(view, 760, 560));
            try {
                stage.show();
                var table = (TableView<?>) view.lookup(".table-view");
                var select = control(view, Button.class, "设为当前项目");
                var open = control(view, MenuButton.class, "打开方式");
                assertTrue(open.isDisabled());
                table.getSelectionModel().select(0);
                select.fire();
                table.requestFocus();
                press(table, KeyCode.DOWN);
                assertEquals(b, table.getSelectionModel().getSelectedItem());
                assertTrue(control(view, Label.class, "已选：B").getTooltip().getText().contains(b.path().toString()));
                assertTrue(isMarked(table, a));
                assertFalse(isMarked(table, b));

                for (int i = 0; i < 4 && view.getScene().getFocusOwner() != open; i++) {
                    press(view.getScene().getFocusOwner(), KeyCode.TAB);
                }
                assertSame(open, view.getScene().getFocusOwner());
                press(open, KeyCode.ENTER);
                var terminal = item(open, "用终端打开");
                var popup = terminal.getParentPopup();
                assertTrue(popup.isShowing());
                for (int i = 0; i < open.getItems().size()
                        && popup.getScene().getFocusOwner() != terminal.getStyleableNode(); i++) {
                    press(popup.getSkin().getNode(), KeyCode.DOWN);
                }
                assertSame(terminal.getStyleableNode(), popup.getScene().getFocusOwner());
                press(popup.getScene().getFocusOwner(), KeyCode.ENTER);
                assertEquals(b.path(), terminalDirectory);
                assertFalse(open.isShowing());
                assertSame(open, view.getScene().getFocusOwner());
                assertEquals(a, workspaceProject);

                select.fire();
                assertEquals(b, workspaceProject);
                assertFalse(isMarked(table, a));
                assertTrue(isMarked(table, b));
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @ParameterizedTest
    @EnumSource(AppSettings.Theme.class)
    void defaultWindowKeepsActionsVisibleAndNotesFollowsOnlyExplicitSwitch(AppSettings.Theme theme) throws Exception {
        var longName = new Project("长项目名".repeat(18), Files.createDirectories(b.path().resolve("长路径".repeat(20))));
        store.save(List.of(a, b), List.of(a, longName));
        onFx(() -> {
            MainView root = new MainView(directory);
            Ui.theme(root, theme);
            Scene scene = new Scene(root, 960, 640);
            var navigation = (ListView<?>) root.lookup(".navigation");
            int projects = navigation.getItems().indexOf("Projects");
            int notes = navigation.getItems().indexOf("Notes");
            navigation.getSelectionModel().select(projects);
            root.applyCss();
            root.layout();
            var table = (TableView<?>) root.lookup(".table-view");
            var select = control(root, Button.class, "设为当前项目");
            table.getSelectionModel().select(0);
            select.fire();
            table.getSelectionModel().select(1);
            root.layout();
            var selected = control(root, Label.class, "已选：" + longName.name());
            assertTrue(selected.getTooltip().getText().contains(longName.path().toString()));
            var tableBounds = table.localToScene(table.getLayoutBounds());
            for (var action : select.getParent().getChildrenUnmodifiable()) {
                var bounds = action.localToScene(action.getLayoutBounds());
                assertTrue(bounds.getMinX() >= tableBounds.getMinX() && bounds.getMaxX() <= scene.getWidth());
                assertTrue(bounds.getMaxY() <= tableBounds.getMinY());
            }
            assertTrue(isMarked(table, a));
            navigation.getSelectionModel().select(notes);
            root.applyCss();
            root.layout();
            assertNotNull(control(root, Label.class, "当前项目：A"));
            assertNotNull(control(root, Label.class, "当前项目：A\n路径：" + a.path()));

            navigation.getSelectionModel().select(projects);
            select.fire();
            assertTrue(isMarked(table, longName));
            navigation.getSelectionModel().select(notes);
            root.applyCss();
            root.layout();
            var header = control(root, Label.class, "当前项目：" + longName.name());
            assertTrue(header.getWidth() > 0);
            assertTrue(header.getTooltip().getText().contains(longName.path().toString()));
            String fullProject = "当前项目：" + longName.name() + "\n路径：" + longName.path();
            assertNotNull(control(root, Label.class, fullProject));
            for (String page : List.of("TODO", "Agents")) {
                navigation.getSelectionModel().select(navigation.getItems().indexOf(page));
                root.applyCss();
                root.layout();
                assertEquals(fullProject, control(root, Label.class, fullProject).getTooltip().getText());
                var actions = page.equals("TODO") ? List.of("添加任务") : List.of("复制项目上下文", "启动 Codex CLI");
                for (var action : actions) assertInsideWindow(control(root, Button.class, action));
            }
            return null;
        });
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({"LIGHT,MOUSE", "DARK,MOUSE", "LIGHT,KEYBOARD", "DARK,KEYBOARD"})
    void firstLogIsSavedOnlyInTheExplicitlyChosenProject(AppSettings.Theme theme, Input input) throws Exception {
        Project target = new Project("B-" + "长项目名".repeat(30), Files.createDirectories(directory
                .resolve("较长的项目路径".repeat(8)).resolve("完整路径的末尾")));
        store.save(List.of(a, b), List.of(a, target));
        Stage window = onFx(() -> {
            MainView root = new MainView(directory);
            Ui.theme(root, theme);
            var navigation = (ListView<?>) root.lookup(".navigation");
            navigation.getSelectionModel().select(navigation.getItems().indexOf("Projects"));
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 960, 640));
            stage.show();
            return stage;
        });
        try {
            Button create = onFx(() -> {
                String scale = System.getProperty("glass.win.uiScale", "");
                if (scale.matches("[1-9]\\d*(\\.\\d+)?")) {
                    assertEquals(Double.parseDouble(scale), window.getOutputScaleX(), 0.01);
                    assertEquals(Double.parseDouble(scale), window.getOutputScaleY(), 0.01);
                }
                Parent root = window.getScene().getRoot();
                var table = (TableView<?>) root.lookup(".project-table");
                var select = control(root, Button.class, "设为当前项目");
                table.getSelectionModel().select(0);
                select.fire();
                var path = control(table, TableCell.class, target.path().toString());
                assertEquals(target.path().toString(), path.getTooltip().getText());
                if (input == Input.MOUSE) click(path);
                else {
                    tabTo(table);
                    press(table, KeyCode.DOWN);
                }
                assertEquals(target, table.getSelectionModel().getSelectedItem());
                assertTrue(path.getTableRow().isSelected());
                assertSame(table, window.getScene().getFocusOwner());
                assertTrue(isMarked(table, a));
                assertFalse(isMarked(table, target));
                assertNotNull(control(root, Label.class, "已选：" + target.name()));

                var more = control(root, MenuButton.class, "更多");
                activate(more, input);
                var popup = item(more, "检查路径").getParentPopup();
                assertTrue(popup.isShowing());
                if (input == Input.MOUSE) click(more);
                else press(popup.getSkin().getNode(), KeyCode.ESCAPE);
                assertFalse(popup.isShowing());
                assertSame(more, window.getScene().getFocusOwner());

                activate(select, input);
                assertTrue(isMarked(table, target));
                assertFalse(isMarked(table, a));
                var navigation = (ListView<?>) root.lookup(".navigation");
                if (input == Input.MOUSE) {
                    click(control(navigation, ListCell.class, "Notes"));
                } else {
                    tabTo(navigation);
                    int steps = navigation.getItems().indexOf("Notes") - navigation.getSelectionModel().getSelectedIndex();
                    for (int i = 0; i < steps; i++) {
                        press(navigation, KeyCode.DOWN);
                    }
                }
                assertEquals("Notes", navigation.getSelectionModel().getSelectedItem());
                root.applyCss();
                root.layout();
                var project = control(root, Label.class, "当前项目：" + target.name() + "\n路径：" + target.path());
                assertEquals(project.getText(), project.getTooltip().getText());
                return control(root, Button.class, "新建日志");
            });
            onFx(() -> whenEnabled(create)).get(10, TimeUnit.SECONDS);

            // showAndWait runs a nested event loop; drive the dialog in the next FX event.
            FutureTask<Void> opening = new FutureTask<>(() -> { activate(create, input); return null; });
            Platform.runLater(opening);
            onFx(() -> {
                Stage editor = Window.getWindows().stream()
                        .filter(w -> w instanceof Stage s && s.getOwner() == window && s.isShowing())
                        .map(Stage.class::cast).findFirst().orElseThrow();
                var pane = assertInstanceOf(DialogPane.class, editor.getScene().getRoot());
                assertEquals("新建日志", editor.getTitle());
                var header = assertInstanceOf(Label.class, pane.getHeader());
                assertEquals("项目：" + target.name(), header.getText());
                assertEquals(header.getText() + "\n路径：" + target.path(), header.getTooltip().getText());
                var title = (TextField) pane.getContent().lookup(".text-field");
                Button save = control(pane, Button.class, "保存");
                assertTrue(header.getWidth() > 0 && pane.getWidth() <= window.getScene().getWidth()
                        && pane.getHeight() <= window.getScene().getHeight(),
                        "日志对话框应适合默认窗口：" + pane.getWidth() + " × " + pane.getHeight());
                assertInsideWindow(save);
                assertTrue(save.isDisabled());
                if (input == Input.MOUSE) click(title);
                else tabTo(title);
                assertSame(title, editor.getScene().getFocusOwner());
                title.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, "第一条日志", "", KeyCode.UNDEFINED,
                        false, false, false, false));
                assertFalse(save.isDisabled());
                if (input == Input.MOUSE) click(save);
                else press(title, KeyCode.ENTER);
                assertFalse(editor.isShowing(), "保存后应返回 Notes");
                return null;
            });
            opening.get(10, TimeUnit.SECONDS);
            var logs = new ProjectLogStore(directory.resolve("notes"));
            assertTrue(logs.load(a).isEmpty());
            var saved = logs.load(target);
            assertEquals(1, saved.size());
            assertEquals("第一条日志", saved.getFirst().title());
        } finally {
            onFx(() -> { window.close(); return null; });
        }
    }

    @Test void conflictPreservesBothListsAndOffersReloadUntilRecoverySucceeds() throws Exception {
        onFx(() -> {
            ProjectListView view = view();
            Scene scene = new Scene(view, 760, 560);
            view.applyCss();
            view.layout();
            var table = (TableView<?>) view.lookup(".table-view");
            var add = control(view, Button.class, "添加项目");
            var reload = control(view, Button.class, "重新加载列表");
            var more = control(view, MenuButton.class, "更多");
            var remove = item(more, "从列表移除");
            assertFalse(reload.getParent().isVisible());
            table.getSelectionModel().select(0);
            control(view, Button.class, "设为当前项目").fire();
            table.getSelectionModel().select(1);

            var renamed = new Project("新名称", a.path());
            store.save(List.of(a, b), List.of(renamed, b));
            remove.fire();
            assertEquals(List.of(a, b), table.getItems());
            assertEquals(List.of(renamed, b), store.load());
            assertEquals(a, workspaceProject);
            assertTrue(reload.getParent().isVisible());
            assertTrue(remove.isDisable());
            assertTrue(add.isDisabled());

            byte[] saved = Files.readAllBytes(file);
            Files.writeString(file, "invalid XML");
            reload.fire();
            assertTrue(reload.getParent().isVisible());
            assertTrue(add.isDisabled());
            assertEquals(List.of(a, b), table.getItems());
            Files.write(file, saved);
            more.requestFocus();
            press(more, KeyCode.TAB);
            assertSame(reload, scene.getFocusOwner());
            press(reload, KeyCode.ENTER);
            assertEquals(List.of(renamed, b), table.getItems());
            assertEquals(renamed, workspaceProject);
            assertFalse(reload.getParent().isVisible());
            assertFalse(add.isDisabled());

            table.getSelectionModel().select(1);
            assertFalse(remove.isDisable());
            remove.fire();
            assertEquals(List.of(renamed), table.getItems());
            assertEquals(List.of(renamed), store.load());
            assertTrue(Files.isDirectory(b.path()));
            assertEquals(renamed, workspaceProject);
            return null;
        });
    }

    private ProjectListView view() {
        return new ProjectListView(new IdePathSettings(directory.resolve("ide.xml")),
                new AppSettings(directory.resolve("settings.xml")), file, project -> workspaceProject = project,
                new ProjectTerminalOpener("Windows", builder -> terminalDirectory = builder.directory().toPath()));
    }

    private static boolean isMarked(TableView<?> table, Project project) {
        table.layout();
        var row = table.lookupAll(".table-row-cell").stream()
                .filter(node -> node instanceof TableRow<?> r && project.equals(r.getItem())).findFirst().orElseThrow();
        return row.lookupAll(".table-cell").stream()
                .anyMatch(node -> node instanceof TableCell<?, ?> cell && "当前".equals(cell.getText()));
    }

    private static <T extends Labeled> T control(Parent view, Class<T> type, String text) {
        return view.lookupAll("*").stream().filter(type::isInstance).map(type::cast)
                .filter(node -> text.equals(node.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("找不到 " + type.getSimpleName() + "：" + text));
    }

    private static MenuItem item(MenuButton menu, String text) {
        return menu.getItems().stream().filter(it -> text.equals(it.getText())).findFirst().orElseThrow();
    }

    private static void assertInsideWindow(Node node) {
        var bounds = node.localToScene(node.getLayoutBounds());
        assertTrue(bounds.getWidth() > 0 && bounds.getHeight() > 0 && bounds.getMinX() >= 0
                && bounds.getMaxX() <= node.getScene().getWidth() && bounds.getMinY() >= 0
                && bounds.getMaxY() <= node.getScene().getHeight(), "操作应在窗口内：" + bounds);
    }

    private static void activate(Node node, Input input) {
        assertInsideWindow(node);
        if (input == Input.MOUSE) click(node);
        else {
            tabTo(node);
            press(node, KeyCode.ENTER);
        }
    }

    private static CompletableFuture<Void> whenEnabled(Button button) {
        var enabled = new CompletableFuture<Void>();
        if (!button.isDisabled()) enabled.complete(null);
        else button.disabledProperty().addListener((obs, old, disabled) -> {
            if (!disabled) enabled.complete(null);
        });
        return enabled;
    }

    private static void tabTo(Node target) {
        Scene scene = target.getScene();
        for (int i = 0; i < 24 && scene.getFocusOwner() != target; i++) {
            Node focused = scene.getFocusOwner();
            press(focused == null ? scene.getRoot() : focused, KeyCode.TAB);
        }
        assertSame(target, scene.getFocusOwner(), "Tab 应能到达 " + target);
    }

    private static void click(Node target) {
        var bounds = target.getLayoutBounds();
        var point = target.localToScene(bounds.getCenterX(), bounds.getCenterY());
        var screen = target.localToScreen(bounds.getCenterX(), bounds.getCenterY());
        var pick = new PickResult(target, point.getX(), point.getY());
        for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            target.fireEvent(new MouseEvent(type, point.getX(), point.getY(), screen.getX(), screen.getY(),
                    MouseButton.PRIMARY, 1, false, false, false, false, type == MouseEvent.MOUSE_PRESSED,
                    false, false, false, false, true, pick));
        }
    }

    private static void press(Node target, KeyCode key) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
        target.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, false, false, false));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
