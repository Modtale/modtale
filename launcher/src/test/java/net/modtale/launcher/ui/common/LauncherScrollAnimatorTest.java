package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LauncherScrollAnimatorTest {

    @Test
    void discreteWheelDurationUsesTheBrowserInverseDeltaRamp() {
        assertEquals(0.200, LauncherScrollAnimator.inverseDeltaDuration(1), 0.000001);
        assertEquals(0.200, LauncherScrollAnimator.inverseDeltaDuration(120), 0.000001);
        assertEquals(0.150, LauncherScrollAnimator.inverseDeltaDuration(300), 0.000001);
        assertEquals(0.100, LauncherScrollAnimator.inverseDeltaDuration(480), 0.000001);
        assertEquals(0.100, LauncherScrollAnimator.inverseDeltaDuration(2_000), 0.000001);
    }

    @Test
    void discreteWheelCurveIsSymmetricEaseInOut() {
        assertEquals(0, LauncherScrollAnimator.easeInOut(0), 0.000001);
        assertEquals(0.5, LauncherScrollAnimator.easeInOut(0.5), 0.000001);
        assertEquals(1, LauncherScrollAnimator.easeInOut(1), 0.000001);
        assertTrue(LauncherScrollAnimator.easeInOut(0.25) < 0.25);
        assertTrue(LauncherScrollAnimator.easeInOut(0.75) > 0.75);
    }

    @Test
    void programmaticDurationUsesTheBrowserDistanceCurveAndCap() {
        assertEquals(0.5, LauncherScrollAnimator.programmaticDuration(900), 0.000001);
        assertEquals(1.5, LauncherScrollAnimator.programmaticDuration(20_000), 0.000001);
    }

    @Test
    void repeatedWheelInputMatchesTheBrowserVelocityPreservingTrace() {
        assertEquals(15.499431726,
                LauncherScrollAnimator.wheelTraceOffset(120, 50, 120, 50), 0.00001);
        assertEquals(75.123269821,
                LauncherScrollAnimator.wheelTraceOffset(120, 50, 120, 100), 0.00001);
        assertEquals(173.288093842,
                LauncherScrollAnimator.wheelTraceOffset(120, 50, 120, 150), 0.00001);
        assertEquals(240,
                LauncherScrollAnimator.wheelTraceOffset(120, 50, 120, 225), 0.00001);
    }
}
