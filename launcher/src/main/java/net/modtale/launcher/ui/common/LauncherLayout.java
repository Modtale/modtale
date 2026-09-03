package net.modtale.launcher.ui.common;

import javafx.geometry.Insets;

public final class LauncherLayout {

    public static final double WORKSPACE_HORIZONTAL_INSET = 112;
    public static final double NAVBAR_RIGHT_EXTRA_INSET = 16;
    public static final Insets WORKSPACE_INSETS = new Insets(
            18,
            WORKSPACE_HORIZONTAL_INSET,
            28,
            WORKSPACE_HORIZONTAL_INSET
    );
    public static final Insets LAUNCHER_WORKSPACE_INSETS = new Insets(18, 40, 28, 40);
    public static final Insets NAVBAR_INSETS = navbarInsets(0, 0);

    private LauncherLayout() {
    }

    public static double navbarLeftInset() {
        return WORKSPACE_HORIZONTAL_INSET;
    }

    public static double navbarRightInset() {
        return WORKSPACE_HORIZONTAL_INSET + NAVBAR_RIGHT_EXTRA_INSET;
    }

    public static Insets navbarInsets(double top, double bottom) {
        return new Insets(top, navbarRightInset(), bottom, navbarLeftInset());
    }

    public static Insets launcherPageInsets(double top, double bottom) {
        return navbarInsets(top, bottom);
    }
}
