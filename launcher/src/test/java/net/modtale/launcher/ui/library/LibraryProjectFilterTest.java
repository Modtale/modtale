package net.modtale.launcher.ui.library;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryProjectFilterTest {
    private LibraryWorldProjectModel project(String name, String author, String version) {
        var display = new LibraryWorldProjectDisplay(name, author, "PLUGIN", "", version, "", false, false, false);
        return new LibraryWorldProjectModel(null, null, null, null, false, List.of("mod"), 1, 1, List.of(), display);
    }

    @Test void matchesWordsAcrossNameAuthorAndVersionWithoutCaseSensitivity() {
        var hexcode = project("Hexcode", "Riprod", "0.9.0-Patch-1");
        var other = project("LevelingCore", "AzureDoom", "1.1.3");
        assertEquals(List.of(hexcode), LibraryProjectFilter.matching(List.of(other, hexcode), " RIPROD  patch "));
        assertTrue(LibraryProjectFilter.matching(List.of(hexcode), "Hexcode AzureDoom").isEmpty());
    }

    @Test void clearingSearchRestoresOriginalOrder() {
        var projects = List.of(project("Zeta", "Dev", "1"), project("Alpha", "Dev", "2"));
        assertEquals(projects, LibraryProjectFilter.matching(projects, "  "));
        assertEquals(projects, LibraryProjectFilter.matching(projects, null));
    }
}
