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

    @Test
    void onlyAccountBackedViewsRequireAModtaleSession() {
        assertFalse(LauncherShell.requiresModtaleSession(LauncherView.DISCOVER));
        assertFalse(LauncherShell.requiresModtaleSession(LauncherView.PROJECT));
        assertFalse(LauncherShell.requiresModtaleSession(LauncherView.PLAY));
        assertFalse(LauncherShell.requiresModtaleSession(LauncherView.LIBRARY));
        assertFalse(LauncherShell.requiresModtaleSession(LauncherView.UPDATES));
        assertFalse(LauncherShell.requiresModtaleSession(LauncherView.SETTINGS));
        assertTrue(LauncherShell.requiresModtaleSession(LauncherView.NOTIFICATIONS));
    }
}
