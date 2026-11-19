package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import javafx.application.Platform;
import javafx.scene.control.Label;
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

    private static GitWorkingTreeReader.Result cleanResult() {
        return new GitWorkingTreeReader.Result(GitWorkingTreeReader.Status.CLEAN, List.of());
    }

    private static GitWorkingTreeReader.Result dirtyResult() {
        return new GitWorkingTreeReader.Result(GitWorkingTreeReader.Status.DIRTY,
                List.of(new GitWorkingTreeReader.FileChange("old.txt", "", " M")));
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
