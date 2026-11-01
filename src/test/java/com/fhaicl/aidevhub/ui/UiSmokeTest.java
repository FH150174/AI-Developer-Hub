package com.fhaicl.aidevhub.ui;

import com.fhaicl.aidevhub.navigation.MainView;
import com.fhaicl.aidevhub.notes.MarkdownPreview;
import com.fhaicl.aidevhub.settings.AppSettings;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class UiSmokeTest {
    @TempDir Path directory;

    @Test void rendersBothThemesPagesMarkdownDialogsAndToast() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        FutureTask<Void> check = new FutureTask<>(() -> {
            MainView root = new MainView(directory);
            new Scene(root, 1100, 800);
            root.resize(1100, 800);
            for (AppSettings.Theme theme : AppSettings.Theme.values()) {
                Ui.theme(root, theme);
                root.applyCss();
                root.layout();
                Label title = (Label) root.lookup(".page-title");
                Color text = (Color) title.getTextFill();
                assertEquals(theme == AppSettings.Theme.DARK, text.getBrightness() > 0.8);
                assertFalse(root.lookupAll(".card").isEmpty());
                assertFalse(root.lookupAll(".hub-icon").isEmpty());
                ToastHost host = (ToastHost) root.getProperties().get(ToastHost.class);
                host.getChildren().getFirst().setOpacity(1);
                snapshot(root, theme.name().toLowerCase() + "-dashboard");
                Ui.toast(root, "设置已保存");
                assertTrue(root.lookup(".toast").isVisible());
                Ui.toast(root, "应用设置已保存");
                Label toast = (Label) root.lookup(".toast");
                assertEquals("应用设置已保存", toast.getText());
                toast.setOpacity(1);
                snapshot(root, theme.name().toLowerCase() + "-toast");
                ListView<?> navigation = (ListView<?>) root.lookup(".navigation");
                for (int index : new int[]{1, 3, 4, 5, 7}) {
                    navigation.getSelectionModel().select(index);
                    root.applyCss();
                    root.layout();
                    assertNotNull(root.lookup(".page-title"));
                    host.getChildren().getFirst().setOpacity(1);
                    snapshot(root, theme.name().toLowerCase() + "-page-" + index);
                }
                Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, "确认删除所选项目？");
                Ui.dialog(dialog, root);
                assertEquals(theme == AppSettings.Theme.DARK, dialog.getDialogPane().getStyleClass().contains("theme-dark"));
                assertFalse(dialog.getDialogPane().getStylesheets().isEmpty());
                dialog.getDialogPane().resize(520, 260);
                dialog.getDialogPane().applyCss();
                dialog.getDialogPane().layout();
                snapshot(dialog.getDialogPane(), theme.name().toLowerCase() + "-dialog");
                // Return to Dashboard before the next theme pass.
                navigation.getSelectionModel().select(0);
            }
            MarkdownPreview markdown = new MarkdownPreview();
            javafx.scene.layout.VBox previewRoot = new javafx.scene.layout.VBox(markdown);
            new Scene(previewRoot, 500, 250);
            Ui.theme(previewRoot, AppSettings.Theme.DARK);
            markdown.showMarkdown("# 标题\n\n正文 **强调**\n\n> 引用\n\n```\ncode\n```");
            previewRoot.applyCss();
            previewRoot.layout();
            assertFalse(markdown.lookupAll("Text").isEmpty());
            for (var node : markdown.lookupAll("Text")) {
                assertTrue(((Color) ((Text) node).getFill()).getBrightness() > 0.8);
            }
            assertFalse(Files.exists(directory.resolve("settings.xml")));
            return null;
        });
        Platform.runLater(check);
        check.get(45, TimeUnit.SECONDS);
    }

    private static void snapshot(javafx.scene.Parent root, String name) throws Exception {
        WritableImage image = root.snapshot(null, null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Path target = Path.of("target", "task054-previews");
        Files.createDirectories(target);
        assertTrue(ImageIO.write(output, "png", target.resolve(name + ".png").toFile()));
    }
}
