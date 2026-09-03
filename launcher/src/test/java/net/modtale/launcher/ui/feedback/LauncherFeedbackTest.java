package net.modtale.launcher.ui.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LauncherFeedbackTest {

    @Test
    void shortToastUsesCompactWidth() {
        assertEquals(224, LauncherFeedback.preferredToastWidth("Saved", "Settings saved."));
    }

    @Test
    void mediumToastGrowsToFitItsCopy() {
        double width = LauncherFeedback.preferredToastWidth(
                "Action failed",
                "Could not open Hytale sign-in in your browser."
        );
        assertTrue(width > 300 && width < 392);
    }

    @Test
    void longToastStopsAtReadableMaximum() {
        assertEquals(392, LauncherFeedback.preferredToastWidth(
                "Action failed",
                "A very long diagnostic message that should wrap instead of making the popup span the launcher window."
        ));
    }
}
