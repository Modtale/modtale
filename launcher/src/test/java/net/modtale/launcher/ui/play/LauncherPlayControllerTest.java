package net.modtale.launcher.ui.play;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LauncherPlayControllerTest {

    @Test
    void catalogEdgeFadeOnlyAppearsWhileMoreCardsRemain() {
        assertEquals(0, LauncherPlayController.catalogEdgeFadeOpacity(0, 0, 0));
        assertEquals(1, LauncherPlayController.catalogEdgeFadeOpacity(0, 0, 1));
        assertEquals(0.5, LauncherPlayController.catalogEdgeFadeOpacity(0.96, 0, 1), 0.0001);
        assertEquals(0, LauncherPlayController.catalogEdgeFadeOpacity(1, 0, 1));
    }
}
