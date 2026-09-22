package net.modtale.launcher.news;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LauncherNewsFeedTest {
    @Test void mergesNewestFirstAndDeduplicatesUrls() {
        var old = post("old", "Hytale", 1);
        var latest = post("latest", "Modtale", 3);
        var middle = post("middle", "Hytale", 2);
        var result = LauncherNewsFeed.load(() -> List.of(latest), () -> List.of(old, middle, latest), Runnable::run).join();
        assertEquals(List.of(latest, middle, old), result.posts());
        assertTrue(result.failedSources().isEmpty());
    }

    @Test void eitherFeedCanFailWithoutLosingTheOther() {
        var article = post("story", "Hytale", 1);
        var modtaleFailure = LauncherNewsFeed.load(() -> { throw new IllegalStateException(); },
                () -> List.of(article), Runnable::run).join();
        assertEquals(List.of(article), modtaleFailure.posts());
        assertEquals(List.of("Modtale"), modtaleFailure.failedSources());
        var hytaleFailure = LauncherNewsFeed.load(() -> List.of(article),
                () -> { throw new IllegalStateException(); }, Runnable::run).join();
        assertEquals(List.of(article), hytaleFailure.posts());
        assertEquals(List.of("Hytale"), hytaleFailure.failedSources());
    }

    @Test void bothFailuresAreReportedAndRetryCanRecover() {
        var result = LauncherNewsFeed.load(() -> { throw new IllegalStateException(); },
                () -> { throw new IllegalStateException(); }, Runnable::run).join();
        assertTrue(result.posts().isEmpty());
        assertEquals(List.of("Modtale", "Hytale"), result.failedSources());
        assertTrue(LauncherNewsFeed.load(List::of, List::of, Runnable::run).join().failedSources().isEmpty());
    }

    private LauncherNewsPost post(String id, String source, long date) {
        return new LauncherNewsPost(id, "https://example.com/" + id, "", Instant.ofEpochSecond(date), source);
    }
}
