package net.modtale.launcher.ui.shell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.modtale.launcher.ui.common.LauncherView;
import org.junit.jupiter.api.Test;

class LauncherShellTest {

    @Test
    void longFormViewsUseDocumentHeightSoContentCanScroll() {
        assertTrue(LauncherShell.usesDocumentHeight(LauncherView.DISCOVER));
        assertTrue(LauncherShell.usesDocumentHeight(LauncherView.PROJECT));
        assertTrue(LauncherShell.usesDocumentHeight(LauncherView.LIBRARY));
        assertTrue(LauncherShell.usesDocumentHeight(LauncherView.UPDATES));
        assertFalse(LauncherShell.usesDocumentHeight(LauncherView.PLAY));
    }
}
