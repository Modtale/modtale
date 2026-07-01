package net.modtale.launcher.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LauncherVersionTest {

    @Test
    void finalReleaseIsNewerThanSnapshotWithSameNumbers() {
        assertTrue(LauncherVersion.isNewer("v0.1.0", "0.1.0-SNAPSHOT"));
    }

    @Test
    void launcherTagPrefixDoesNotAffectComparison() {
        assertTrue(LauncherVersion.isNewer("launcher-v0.2.0", "0.1.9"));
    }

    @Test
    void prereleaseIsOlderThanFinalRelease() {
        assertFalse(LauncherVersion.isNewer("1.0.0-beta.1", "1.0.0"));
    }

    @Test
    void higherPrereleaseNumberIsNewer() {
        assertTrue(LauncherVersion.isNewer("1.0.0-beta.2", "1.0.0-beta.1"));
    }
}
