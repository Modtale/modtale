package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.input.ScrollEvent;
import org.junit.jupiter.api.Test;

class PlatformScrollInputTest {
    @Test
    void windowsWheelUsesSystemLineAndCharacterCountsAtEveryScale() {
        for (double scale : new double[]{1, 1.25, 1.5, 2}) {
            for (int lines : new int[]{1, 3, 6, 12}) {
                for (double ticks : new double[]{-2, -1, -.25, .25, 1, 2}) {
                    ScrollEvent event = textEvent(ticks * 40 / scale, ticks * lines / scale,
                            ScrollEvent.VerticalTextScrollUnits.LINES);
                    assertEquals(ticks * lines * (100.0 / 3) / scale,
                            LauncherScrollSupport.browserAxisDelta(event, false, scale), 1e-9);
                    assertEquals(ticks * lines * (100.0 / 3) / scale,
                            LauncherScrollSupport.browserAxisDelta(event, true, scale), 1e-9);
                    assertFalse(LauncherScrollSupport.isPreciseScroll(event, scale),
                            "Fractional Windows wheel messages retain wheel acceleration");
                }
            }
        }
    }

    @Test
    void pageScrollingUsesChromiumsViewportFraction() {
        assertEquals(700, LauncherScrollSupport.pageStep(800));
        assertEquals(437, LauncherScrollSupport.pageStep(500));
        assertEquals(1, LauncherScrollSupport.pageStep(0));
    }

    @Test
    void cocoaCoarseWheelUsesFortyPointsPerTickWithoutRetinaRescaling() {
        var sample = MacScrollInput.sample(false, -.5, -3, 1, 1_004_000_000);
        assertEquals(20, sample.x());
        assertEquals(120, sample.y());
        assertFalse(sample.precise());
        assertEquals(4_000_000, sample.delayNanos());
    }

    @Test
    void cocoaPreciseAndMomentumDeltasAreNeverMultipliedOrSmoothedAgain() {
        for (double delta : new double[]{.125, 1, 10, 40, 120, -2.5}) {
            var sample = MacScrollInput.sample(true, delta, -delta, 1, 1_000_000_000);
            assertEquals(-delta, sample.x());
            assertEquals(delta, sample.y());
            assertTrue(sample.precise(), "Even a 40-point precise delta is not a wheel notch");
        }
    }

    @Test
    void staleCocoaClockMetadataCannotShortenTheScrollAnimation() {
        assertEquals(0, MacScrollInput.sample(false, 0, 1, 0, 9_000_000_000L).delayNanos());
        assertEquals(0, MacScrollInput.sample(false, 0, 1, 2, 1_000_000_000).delayNanos());
    }

    private static ScrollEvent textEvent(double pixels, double text, ScrollEvent.VerticalTextScrollUnits units) {
        return new ScrollEvent(ScrollEvent.SCROLL, 0, 0, 0, 0,
                false, false, false, false, false, false, pixels, pixels, pixels, pixels,
                ScrollEvent.HorizontalTextScrollUnits.CHARACTERS, text, units, text, 0, null);
    }
}
