package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Insets;
import org.junit.jupiter.api.Test;

class LauncherLayoutTest {

    @Test
    void launcherProjectPagesUseNavbarHorizontalInsets() {
        Insets pageInsets = LauncherLayout.launcherPageInsets(12, 34);
        Insets navbarInsets = LauncherLayout.navbarInsets(12, 34);

        assertEquals(navbarInsets, pageInsets);
    }
}
