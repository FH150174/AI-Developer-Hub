package com.fhaicl.aidevhub.ui;

import com.fhaicl.aidevhub.settings.AppSettings;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

public final class Ui {
    private static final String CSS = java.util.Objects.requireNonNull(Ui.class.getResource("hub.css")).toExternalForm();
    private Ui() { }

    public static void theme(Parent root, AppSettings.Theme theme) {
        if (!root.getStylesheets().contains(CSS)) root.getStylesheets().add(CSS);
        if (!root.getStyleClass().contains("hub")) root.getStyleClass().add("hub");
        root.getStyleClass().remove("theme-dark");
        if (theme == AppSettings.Theme.DARK) root.getStyleClass().add("theme-dark");
    }

    public static void dialog(Dialog<?> dialog, Node owner) {
        if (owner != null && owner.getScene() != null) {
            if (owner.getScene().getWindow() != null) dialog.initOwner(owner.getScene().getWindow());
            theme(dialog.getDialogPane(), owner.getScene().getRoot().getStyleClass().contains("theme-dark")
                    ? AppSettings.Theme.DARK : AppSettings.Theme.LIGHT);
        } else theme(dialog.getDialogPane(), AppSettings.Theme.LIGHT);
        dialog.getDialogPane().setMinWidth(420);
    }

    public static VBox card(String heading, Node... content) {
        Label title = new Label(heading);
        title.getStyleClass().add("card-title");
        VBox card = new VBox(12, title);
        card.getChildren().addAll(content);
        card.getStyleClass().add("card");
        card.setMinWidth(0);
        return card;
    }

    public static VBox empty(String title, String hint) {
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label detail = new Label(hint);
        detail.setWrapText(true);
        VBox box = new VBox(8, icon("Projects"), heading, detail);
        box.getStyleClass().add("empty-state");
        box.setMaxWidth(360);
        return box;
    }

    public static Node icon(String name) {
        String path = switch (name) {
            case "Dashboard" -> "M3 3H10V10H3Z M14 3H21V10H14Z M3 14H10V21H3Z M14 14H21V21H14Z";
            case "Projects" -> "M3 7V4H10L12 7H21V20H3Z";
            case "Git" -> "M6 3V15Q6 19 10 19H15 M6 9H13Q18 9 18 4 M3 3A3 3 0 1 0 9 3 M15 3A3 3 0 1 0 21 3 M15 19A3 3 0 1 0 21 19";
            case "Notes" -> "M5 3H16L20 7V21H5Z M15 3V8H20 M8 12H17 M8 16H15";
            case "TODO" -> "M3 6L5 8L9 3 M12 6H21 M3 16L5 18L9 13 M12 16H21";
            case "Agents" -> "M5 7H19V20H5Z M12 3V7 M9 12V14 M15 12V14 M9 17H15 M2 11V16 M22 11V16";
            case "Environment" -> "M3 4H21V17H3Z M8 21H16 M12 17V21 M7 8L10 11L7 14 M13 14H17";
            case "Settings" -> "M4 6H20 M4 12H20 M4 18H20 M8 3V9 M16 9V15 M10 15V21";
            default -> "M5 12H19 M13 6L19 12L13 18";
        };
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("hub-icon");
        icon.setMouseTransparent(true);
        return icon;
    }

    public static void toast(Node source, String message) {
        if (source.getScene() == null) return;
        Object host = source.getScene().getRoot().getProperties().get(ToastHost.class);
        if (host instanceof ToastHost toastHost) toastHost.show(message);
    }
}
