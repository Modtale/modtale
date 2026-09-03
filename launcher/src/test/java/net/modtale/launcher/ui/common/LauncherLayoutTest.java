package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Insets;
import org.junit.jupiter.api.Test;

class LauncherLayoutTest {

    @Test
    void navbarAndWorkspaceShareHorizontalBounds() {
        assertEquals(LauncherLayout.WORKSPACE_HORIZONTAL_INSET, LauncherLayout.NAVBAR_INSETS.getLeft());
        assertEquals(LauncherLayout.WORKSPACE_HORIZONTAL_INSET, LauncherLayout.NAVBAR_INSETS.getRight());
        assertEquals(LauncherLayout.NAVBAR_INSETS.getLeft(), LauncherLayout.WORKSPACE_INSETS.getLeft());
        assertEquals(LauncherLayout.NAVBAR_INSETS.getRight(), LauncherLayout.WORKSPACE_INSETS.getRight());
    }

    @Test
    void launcherProjectPagesUseNavbarHorizontalInsets() {
        Insets pageInsets = LauncherLayout.launcherPageInsets(12, 34);
        Insets navbarInsets = LauncherLayout.navbarInsets(12, 34);

        assertEquals(navbarInsets, pageInsets);
    }
}
