package net.modtale.launcher.ui.shell;

import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.common.LauncherView;

public final class LauncherShellTitles {

    private LauncherShellTitles() {
    }

    public static String titleFor(LauncherView view, ProjectBrowseController browseController) {
        return switch (view) {
            case PLAY -> "Play Hytale";
            case LIBRARY -> "World Library";
            case UPDATES -> "Updates";
            case NOTIFICATIONS -> "Notifications";
            case SETTINGS -> "Settings";
            case PROJECT -> "Project Page";
            case DISCOVER -> browseController.title();
        };
    }

    public static String subtitleFor(LauncherView view, ProjectBrowseController browseController) {
        return switch (view) {
            case PLAY -> "Launch with official Hytale auth and Modtale-managed mods.";
            case LIBRARY -> "World saves with per-world mod and modpack controls.";
            case UPDATES -> "Compare installed projects against the newest compatible releases.";
            case NOTIFICATIONS -> "Notification preferences and recent Modtale activity.";
            case SETTINGS -> "Local folders, game version targeting, and dependency preferences.";
            case PROJECT -> "Native Modtale project details with launcher install actions.";
            case DISCOVER -> browseController.subtitle();
        };
    }
}
