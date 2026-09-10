package net.modtale.launcher.ui.project;

final class GalleryPlaybackClock {
    private final long durationNanos;
    private long elapsedNanos;
    private long startedAt;
    private boolean running;

    GalleryPlaybackClock(long durationNanos) {
        if (durationNanos <= 0) throw new IllegalArgumentException("Duration must be positive");
        this.durationNanos = durationNanos;
    }

    void reset(long now) {
        elapsedNanos = 0;
        startedAt = now;
    }

    void setRunning(boolean nextRunning, long now) {
        if (running == nextRunning) return;
        elapsedNanos = elapsed(now);
        startedAt = now;
        running = nextRunning;
    }

    double progress(long now) {
        return (double) elapsed(now) / durationNanos;
    }

    private long elapsed(long now) {
        return Math.min(durationNanos, elapsedNanos + (running ? Math.max(0, now - startedAt) : 0));
    }
}
