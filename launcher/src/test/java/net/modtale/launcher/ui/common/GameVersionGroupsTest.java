package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameVersionGroupsTest {

    @Test
    void groupsPatchVersionsByMinorLine() {
        List<GameVersionGroups.Group> groups = GameVersionGroups.build(List.of(
                "0.6.0",
                "0.5.4",
                "0.5.3",
                "0.5.2-pre.1",
                "preview"
        ));

        assertEquals("0.6.x", groups.get(0).label());
        assertEquals(false, groups.get(0).grouped());
        assertEquals("0.5.x", groups.get(1).label());
        assertEquals(List.of("0.5.4", "0.5.3", "0.5.2-pre.1"), groups.get(1).versions());
        assertEquals(true, groups.get(1).grouped());
        assertEquals("preview", groups.get(2).label());
    }

    @Test
    void selectionQueryKeepsCatalogOrder() {
        List<String> ordered = List.of("0.6.0", "0.5.4", "0.5.3");

        assertEquals("0.5.4,0.5.3",
                GameVersionGroups.selectionQuery(List.of("0.5.3", "0.5.4"), ordered));
    }

    @Test
    void displayLabelUsesMinorGroupWhenFullySelected() {
        List<String> ordered = List.of("0.6.0", "0.5.4", "0.5.3");

        assertEquals("0.5.x",
                GameVersionGroups.displayLabel(List.of("0.5.4", "0.5.3"), ordered, "Any"));
        assertEquals("2 versions",
                GameVersionGroups.displayLabel(List.of("0.6.0", "0.5.3"), ordered, "Any"));
    }
}
