package net.modtale.launcher.ui.shell;

import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.i18n.LauncherI18n;

public final class LauncherShellTitles {

    private static final LauncherI18n I18N = LauncherI18n.get();

    private LauncherShellTitles() {
    }

    public static String titleFor(LauncherView view, ProjectBrowseController browseController) {
        return switch (view) {
            case PLAY -> I18N.text("view.play.title");
            case LIBRARY -> I18N.text("view.library.title");
            case UPDATES -> I18N.text("view.updates.title");
            case NOTIFICATIONS -> I18N.text("view.notifications.title");
            case SETTINGS -> I18N.text("view.settings.title");
            case PROJECT -> I18N.text("view.project.title");
            case DISCOVER -> browseController.title();
        };
    }

    public static String subtitleFor(LauncherView view, ProjectBrowseController browseController) {
        return switch (view) {
            case PLAY -> I18N.text("view.play.subtitle");
            case LIBRARY -> I18N.text("view.library.subtitle");
            case UPDATES -> I18N.text("view.updates.subtitle");
            case NOTIFICATIONS -> I18N.text("view.notifications.subtitle");
            case SETTINGS -> I18N.text("view.settings.subtitle");
            case PROJECT -> I18N.text("view.project.subtitle");
            case DISCOVER -> browseController.subtitle();
        };
    }
}
