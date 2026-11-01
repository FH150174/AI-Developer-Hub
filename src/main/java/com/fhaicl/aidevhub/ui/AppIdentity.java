package com.fhaicl.aidevhub.ui;

import java.util.List;
import java.util.Objects;
import javafx.scene.image.Image;

public final class AppIdentity {
    public static final String NAME = "AI Developer Hub";
    private AppIdentity() { }

    public static String version() {
        // jpackage supplies the actual installer version, including an explicit -AppVersion override.
        return System.getProperty("jpackage.app-version", "开发版");
    }

    public static List<Image> icons() {
        return List.of(16, 32, 48, 64, 128, 256).stream()
                .map(size -> Objects.requireNonNull(AppIdentity.class.getResource("icons/app-" + size + ".png"),
                        "Application icon resource is missing"))
                .map(url -> new Image(url.toExternalForm())).toList();
    }
}
