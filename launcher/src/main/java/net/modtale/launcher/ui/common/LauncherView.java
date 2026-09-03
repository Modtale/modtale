package net.modtale.launcher.ui.common;

import java.util.Arrays;

public enum LauncherView {
    DISCOVER("discover"),
    PLAY("play"),
    LIBRARY("library"),
    UPDATES("updates"),
    NOTIFICATIONS("notifications"),
    SETTINGS("settings"),
    PROJECT("project");

    private final String id;

    LauncherView(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static LauncherView defaultView() {
        return PLAY;
    }

    public static LauncherView fromId(String id) {
        if (id == null || id.isBlank()) {
            return defaultView();
        }
        return Arrays.stream(values())
                .filter(view -> view.id.equalsIgnoreCase(id.trim()))
                .findFirst()
                .orElse(defaultView());
    }
}
