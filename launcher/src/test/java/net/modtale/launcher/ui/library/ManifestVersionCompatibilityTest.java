package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ManifestVersionCompatibilityTest {
    @Test void comparesBoundsAndAlternatives() {
        assertTrue(ManifestVersionCompatibility.incompatible(">=0.5.0 <0.6.0", "0.6.7"));
        assertFalse(ManifestVersionCompatibility.incompatible(">=0.6.0 <0.7.0", "0.6.7"));
        assertTrue(ManifestVersionCompatibility.incompatible(">=0.5.0 <0.6.0", "0.6.0"));
        assertFalse(ManifestVersionCompatibility.incompatible(">=0.5.0 <=0.6.0", "0.6.0"));
        assertFalse(ManifestVersionCompatibility.incompatible("0.5.x || >=0.6.0 <0.7.0", "0.6.7"));
        assertFalse(ManifestVersionCompatibility.incompatible(">=0.6.0-pre.0 <0.7.0", "0.6.0-pre.2"));
        assertTrue(ManifestVersionCompatibility.incompatible(">=0.6.0 <0.7.0", "0.6.0-pre.2"));
    }

    @Test void unknownInputsDoNotWarn() {
        for (String requirement : new String[]{"", "*", "unsupported", ">=0.5.0 ||", "^0.6.0"}) {
            assertFalse(ManifestVersionCompatibility.incompatible(requirement, "0.6.7"));
        }
        assertFalse(ManifestVersionCompatibility.incompatible("<0.6.0", ""));
        assertFalse(ManifestVersionCompatibility.incompatible("<0.6.0", "2026.01.01-abcdef"));
        assertFalse(ManifestVersionCompatibility.incompatible("<999999999999.0.0", "0.6.7"));
    }
}
