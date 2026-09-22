package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ManifestVersionLabelTest {
    static String[][] cases() { return new String[][] {
        {"*", "All versions"}, {"X", "All versions"}, {"0.6", "0.6.x"}, {"0.6.*", "0.6.x"}, {"0.6.X", "0.6.x"},
        {"v0.6.0", "0.6.0"}, {"0.6.0+build.12", "0.6.0+build.12"}, {"0.6.0-rc.1", "0.6.0-rc.1"},
        {">=0.5.0 <0.6.0", "0.5.x"}, {">=0.5.0 <0.7.0", "0.5.x & 0.6.x"},
        {"<0.7.0 >=0.6.0", "0.6.x"}, {">=0.5.1 <=0.5.3", "0.5.1 – 0.5.3"},
        {">=0.5.1 <0.5.4", "0.5.1 – 0.5.3"}, {"0.5.1 - 0.5.3", "0.5.1 – 0.5.3"},
        {">= 0.6.0", "0.6.0 and newer"}, {"<=0.6.0", "0.6.0 or older"}, {">0.6.0", "After 0.6.0"},
        {"<0.6.0", "Before 0.6.0"}, {"=0.6.0", "0.6.0"},
        {"^0.6.0", "0.6.x"}, {"~0.6.0", "0.6.x"}, {"~0.6", "0.6.x"},
        {"^0.6.1", "0.6.1 – 0.6.x"}, {"^0.0.3", "0.0.3"}, {"^1.0.0", "1.x"}, {"^0", "0.x"}, {"^0.0", "0.0.x"},
        {"0.5.x || 0.6.x", "0.5.x & 0.6.x"}, {"0.5.1, 0.5.3", "0.5.1 & 0.5.3"},
        {">=0.6.0-pre.0 <0.7.0", "0.6.0-pre.0 – before 0.7.0"},
        {"2026.03.26-89796e57b", "2026.03.26-89796e57b"}, {"Early Access", "Early Access"},
        {"future-format:stable", "future-format:stable"}, {">=0.5.0 ||", ">=0.5.0 ||"},
        {">=0.5.0 nonsense <0.6.0", ">=0.5.0 nonsense <0.6.0"},
        {">=0.5.0, <0.6.0", "0.5.x"}, {">=0.5 <0.7", "0.5.x & 0.6.x"}, {"<=0.5", "Before 0.6.0"}, {">0.5", "0.6.0 and newer"}, {">=2 <3", "2.x"}, {"0.5.* || 0.5.x", "0.5.x"}
    }; }
    @org.junit.jupiter.api.Test void resolvesEndpointsFromCurrentCatalog() {
        var versions = java.util.List.of("0.5.1", "0.5.5", "0.6.0", "2026.03.26-89796e57b");
        assertEquals("0.5.4 – 0.5.5", ManifestVersionLabel.format(">=0.5.4 <0.6.0", versions));
        assertEquals("0.6.x", ManifestVersionLabel.format(">=0.6.0-pre.0 <0.7.0", versions));
        assertEquals("0.6.x", ManifestVersionLabel.format(">=0.6.0-pre.0 <0.7.0", java.util.List.of("0.7.0")));
    }
    @ParameterizedTest @MethodSource("cases")
    void formatsWithoutWideningCompatibility(String input, String expected) {
        assertEquals(expected, ManifestVersionLabel.format(input));
    }
}
