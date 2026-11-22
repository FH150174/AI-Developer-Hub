package com.fhaicl.aidevhub.ui;

import com.fhaicl.aidevhub.storage.HistoryConflictException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/** Guidance shown in an empty Notes or TODO list. */
public final class ProjectHistoryPlaceholder extends VBox {
    private final String kind;
    private final String firstEntryHint;
    private final Label heading = new Label();
    private final Label detail = new Label();
    private final Button projects = new Button("前往 Projects");
    private final Button retry = new Button();

    public ProjectHistoryPlaceholder(String kind, String firstEntryHint, Runnable openProjects, Runnable reload) {
        super(8);
        this.kind = kind;
        this.firstEntryHint = firstEntryHint;
        getStyleClass().add("empty-state");
        setMaxWidth(480);
        setMinWidth(0);
        heading.getStyleClass().add("empty-title");
        heading.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        heading.setMinWidth(0);
        detail.setWrapText(true);
        detail.setMinWidth(0);
        projects.setOnAction(event -> openProjects.run());
        retry.setOnAction(event -> reload.run());
    }

    public void noProject() {
        show("尚未选择当前项目", "请到 Projects 选中项目，再点“设为当前项目”。", projects);
    }

    public void loading(String name) {
        show("正在读取 " + name + " 的" + kind + "…", "", null);
    }

    public void empty(String name) {
        show(name + " 还没有" + kind + "。", firstEntryHint, null);
    }

    public void failed(Throwable failure) {
        retry.setText(failure instanceof HistoryConflictException ? "处理后重新读取" : "重新读取");
        show(kind + "读取失败", readFailureMessage(failure), retry);
    }

    private void show(String title, String message, Button action) {
        heading.setText(title);
        heading.setTooltip(new Tooltip(title));
        getChildren().setAll(heading);
        if (!message.isEmpty()) {
            detail.setText(message);
            detail.setTooltip(new Tooltip(message));
            getChildren().add(detail);
        }
        if (action != null) getChildren().add(action);
    }

    private static String readFailureMessage(Throwable failure) {
        if (failure instanceof AccessDeniedException || failure instanceof SecurityException) {
            return "读取权限不足，请检查存储目录和文件的访问权限。";
        }
        String guidance = "请检查存储文件、访问权限及磁盘状态。";
        if (failure instanceof IOException && failure.getMessage() != null && !failure.getMessage().isBlank()) {
            // The stores include recovery instructions and, for ambiguous histories, both filenames.
            return failure instanceof FileSystemException ? guidance + "\n" + failure.getMessage() : failure.getMessage();
        }
        return guidance;
    }
}
