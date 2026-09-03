package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameVersionOrderingTest {
    @Test
    void matchesServerCatalogOrderingForSemverLegacyAndUnknownVersions() {
        assertEquals(List.of(
                "1.10.0",
                "1.2.0",
                "1.2.0-rc.2",
                "1.2.0-rc.1",
                "2026.10.2-bbbb",
                "2026.9.12-aaaa",
                "preview-z",
                "preview-a"
        ), GameVersionOrdering.descendingDistinct(List.of(
                "preview-a", "1.2.0-rc.1", "2026.9.12-aaaa", "1.10.0",
                "1.2.0", "preview-z", "2026.10.2-bbbb", "1.2.0-rc.2", "1.2.0"
        )));
    }
}
