package com.fhaicl.aidevhub.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class ToastHost extends StackPane {
    private final Label notice = new Label();
    private SequentialTransition animation;
    private FadeTransition pageAnimation;

    public ToastHost() {
        notice.getStyleClass().add("toast");
        notice.setWrapText(true);
        notice.setMaxWidth(420);
        notice.setMouseTransparent(true);
        notice.setVisible(false);
        notice.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        StackPane.setAlignment(notice, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(notice, new Insets(24));
        getChildren().add(notice);
        setMinWidth(0);
    }

    public void content(Node page) {
        if (pageAnimation != null) {
            pageAnimation.stop();
            pageAnimation.getNode().setOpacity(1);
        }
        getChildren().setAll(page, notice);
        pageAnimation = new FadeTransition(Duration.millis(140), page);
        pageAnimation.setFromValue(0.65);
        pageAnimation.setToValue(1);
        pageAnimation.play();
    }

    public void show(String message) {
        if (animation != null) animation.stop();
        notice.setText(message);
        notice.setAccessibleText(message);
        notice.setVisible(true);
        FadeTransition enter = new FadeTransition(Duration.millis(120), notice);
        enter.setFromValue(0);
        enter.setToValue(1);
        FadeTransition leave = new FadeTransition(Duration.millis(180), notice);
        leave.setFromValue(1);
        leave.setToValue(0);
        animation = new SequentialTransition(enter, new PauseTransition(Duration.seconds(4)), leave);
        animation.setOnFinished(event -> notice.setVisible(false));
        animation.play();
    }
}
