package net.modtale.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LinuxCursorSettingsTest {

    @Test
    void exportsNativeXCursorResourcesForLinuxDisplay() {
        Map<String, String> settings = LinuxCursorSettings.missingCursorEnvironment(
                "Linux",
                ":0",
                Map.of(),
                Map.of("size", "48", "theme", "Adwaita")
        );

        assertEquals(Map.of("XCURSOR_SIZE", "48", "XCURSOR_THEME", "Adwaita"), settings);
    }

    @Test
    void preservesExplicitCursorOverrides() {
        Map<String, String> settings = LinuxCursorSettings.missingCursorEnvironment(
                "Linux",
                ":0",
                Map.of("XCURSOR_SIZE", "64", "XCURSOR_THEME", "Bibata"),
                Map.of("size", "48", "theme", "Adwaita")
        );

        assertTrue(settings.isEmpty());
    }

    @Test
    void ignoresInvalidOrIrrelevantCursorResources() {
        assertTrue(LinuxCursorSettings.missingCursorEnvironment(
                "Windows 11", ":0", Map.of(), Map.of("size", "48", "theme", "Adwaita")
        ).isEmpty());
        assertTrue(LinuxCursorSettings.missingCursorEnvironment(
                "Linux", "", Map.of(), Map.of("size", "48", "theme", "Adwaita")
        ).isEmpty());
        assertEquals(
                Map.of("XCURSOR_THEME", "Adwaita"),
                LinuxCursorSettings.missingCursorEnvironment(
                        "Linux", ":0", Map.of(), Map.of("size", "huge", "theme", "Adwaita")
                )
        );
    }
}
