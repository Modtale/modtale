package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class GalleryPlaybackClockTest {
    @Test
    void progressionUsesElapsedTimeAcrossUnevenFramesAndUpdates() {
        var clock = new GalleryPlaybackClock(8_000);
        clock.reset(100);
        clock.setRunning(true, 100);
        assertEquals(0.125, clock.progress(1_100));
        clock.setRunning(true, 1_300);
        assertEquals(0.1875, clock.progress(1_600));
        assertEquals(0.5, clock.progress(4_100));
        assertEquals(1, clock.progress(20_000));
    }

    @Test
    void pauseRetainsProgressAndResumeUsesOnlyRemainingTime() {
        var clock = new GalleryPlaybackClock(8_000);
        clock.setRunning(true, 0);
        clock.setRunning(false, 2_000);
        assertEquals(0.25, clock.progress(20_000));
        clock.setRunning(true, 20_000);
        assertEquals(0.25, clock.progress(20_000));
        assertEquals(0.5, clock.progress(22_000));
        assertEquals(1, clock.progress(26_000));
    }

    @Test
    void slideResetDoesNotResumeAPausedClock() {
        var clock = new GalleryPlaybackClock(8_000);
        clock.setRunning(true, 0);
        clock.setRunning(false, 2_000);
        clock.reset(3_000);
        assertEquals(0, clock.progress(40_000));
        clock.setRunning(true, 40_000);
        assertEquals(0.5, clock.progress(44_000));
    }
}
