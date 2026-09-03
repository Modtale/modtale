package net.modtale.launcher.ui.browse.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.modtale.launcher.model.project.GameVersionCatalog;
import org.junit.jupiter.api.Test;

class GameVersionFilterCatalogTest {

    @Test
    void hidesPreReleasesUntilRequested() {
        GameVersionCatalog catalog = new GameVersionCatalog(
                List.of("1.0.0"),
                List.of("1.1.0-beta"),
                List.of("1.1.0-beta", "1.0.0"),
                List.of(
                        new GameVersionCatalog.GameVersionEntry("1.1.0-beta", true, null),
                        new GameVersionCatalog.GameVersionEntry("1.0.0", false, null)
                )
        );

        GameVersionFilterCatalog filterCatalog = GameVersionFilterCatalog.from(catalog);

        assertEquals(List.of("1.0.0"), filterCatalog.visibleVersions(false));
        assertEquals(List.of("1.1.0-beta", "1.0.0"), filterCatalog.visibleVersions(true));
    }

    @Test
    void fallsBackToReleaseVersionsWhenCatalogHasNoFullOrdering() {
        GameVersionCatalog catalog = new GameVersionCatalog(
                List.of("2.0.0", "1.0.0"),
                List.of(),
                List.of(),
                List.of()
        );

        assertEquals(List.of("2.0.0", "1.0.0"),
                GameVersionFilterCatalog.from(catalog).visibleVersions(false));
    }
}
