package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledOnOs(OS.WINDOWS)
@org.junit.jupiter.api.Tag("ui")
class GitChangeCountViewRefreshTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    @Test
    void hiddenProjectChangeDoesNotReadWorkingTreeUntilRefresh() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        AtomicReference<GitChangeCountView> view = new AtomicReference<>();

        runOnFx(() -> {
            GitChangeCountView created = new GitChangeCountView(false, project -> {
                queries.incrementAndGet();
                return cleanResult();
            });
            created.setProject(new Project("demo", directory));
            view.set(created);
        });

        Thread.sleep(200);
        assertEquals(0, queries.get());
        assertEquals("修改文件数量：—", countText(view.get()));

        runOnFx(() -> view.get().refresh());
        await(() -> queries.get() == 1);
        awaitCount(view.get(), "修改文件数量：0");

        runOnFx(() -> view.get().refresh());
        await(() -> queries.get() == 2);
        awaitCount(view.get(), "修改文件数量：0");
    }

    @Test
    void lateResultFromPreviousProjectIsNotDisplayed() throws Exception {
        CountDownLatch firstQueryStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstQuery = new CountDownLatch(1);
        AtomicInteger queries = new AtomicInteger();
        AtomicReference<GitChangeCountView> view = new AtomicReference<>();

        runOnFx(() -> {
            GitChangeCountView created = new GitChangeCountView(false, project -> {
                queries.incrementAndGet();
                if (project.name().equals("first")) {
                    firstQueryStarted.countDown();
                    try {
                        releaseFirstQuery.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return dirtyResult();
                }
                return cleanResult();
            });
            created.setProject(new Project("first", directory));
            created.refresh();
            view.set(created);
        });

        assertTrue(firstQueryStarted.await(5, TimeUnit.SECONDS));
        runOnFx(() -> {
            view.get().setProject(new Project("second", directory));
            view.get().refresh();
        });
        awaitCount(view.get(), "修改文件数量：0");

        releaseFirstQuery.countDown();
        Thread.sleep(200);

        assertEquals("修改文件数量：0", countText(view.get()));
        assertEquals(2, queries.get());
    }

    @Test
    void compactSummaryNamesCleanDirtyAndNonRepositoryStates() throws Exception {
        AtomicReference<GitChangeCountView> view = new AtomicReference<>();
        runOnFx(() -> {
            GitChangeCountView created = new GitChangeCountView(false, true, project -> switch (project.name()) {
                case "dirty" -> dirtyResult(3);
                case "plain" -> new GitWorkingTreeReader.Result(
                        GitWorkingTreeReader.Status.NOT_REPOSITORY, List.of());
                default -> cleanResult();
            });
            created.setProject(new Project("clean", directory));
            created.refresh();
            view.set(created);
        });
        awaitCount(view.get(), "无变更");
        runOnFx(() -> {
            TitledPane explanation = (TitledPane) view.get().getChildren().get(1);
            assertEquals("统计说明", explanation.getText());
            assertFalse(explanation.isExpanded());
            view.get().setProject(new Project("dirty", directory));
            view.get().refresh();
        });
        awaitCount(view.get(), "3 个文件变更");
        runOnFx(() -> {
            view.get().setProject(new Project("plain", directory));
            view.get().refresh();
        });
        awaitCount(view.get(), "不是 Git 仓库");
    }

    @Test
    void compactExplanationCanBeExpandedWithTheKeyboard() throws Exception {
        AtomicReference<GitChangeCountView> view = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        runOnFx(() -> {
            GitChangeCountView created = new GitChangeCountView(false, true, project -> cleanResult());
            stage.set(new Stage());
            stage.get().setScene(new Scene(created, 420, 260));
            stage.get().show();
            view.set(created);
        });
        try {
            runOnFx(() -> {
                TitledPane explanation = (TitledPane) view.get().getChildren().get(1);
                explanation.requestFocus();
                assertEquals(explanation, view.get().getScene().getFocusOwner());
                assertFalse(explanation.isExpanded());
                press(explanation, KeyCode.SPACE);
                assertTrue(explanation.isExpanded());
            });
        } finally {
            runOnFx(() -> stage.get().close());
        }
    }

    private static GitWorkingTreeReader.Result cleanResult() {
        return new GitWorkingTreeReader.Result(GitWorkingTreeReader.Status.CLEAN, List.of());
    }

    private static GitWorkingTreeReader.Result dirtyResult() {
        return dirtyResult(1);
    }

    private static GitWorkingTreeReader.Result dirtyResult(int count) {
        List<GitWorkingTreeReader.FileChange> files = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new GitWorkingTreeReader.FileChange("file-" + index + ".txt", "", " M"))
                .toList();
        return new GitWorkingTreeReader.Result(GitWorkingTreeReader.Status.DIRTY, files);
    }

    private static void press(javafx.scene.Node target, KeyCode key) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
        target.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, false, false, false));
    }

    private static String countText(GitChangeCountView view) throws Exception {
        return callOnFx(() -> ((Label) view.getChildren().getFirst()).getText());
    }

    private static void awaitCount(GitChangeCountView view, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (countText(view).equals(expected)) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for count label: " + countText(view));
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for background query");
    }

    private static void runOnFx(Runnable action) throws Exception {
        callOnFx(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T callOnFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
