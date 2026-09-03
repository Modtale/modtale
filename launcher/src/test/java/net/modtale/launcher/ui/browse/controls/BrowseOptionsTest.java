package net.modtale.launcher.ui.browse.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrowseOptionsTest {

    @Test
    void listsModpacksImmediatelyAfterAllProjects() {
        assertEquals(List.of(
                BrowseOptions.ClassificationOption.ALL,
                BrowseOptions.ClassificationOption.MODPACKS,
                BrowseOptions.ClassificationOption.PLUGINS,
                BrowseOptions.ClassificationOption.WORLDS,
                BrowseOptions.ClassificationOption.ART,
                BrowseOptions.ClassificationOption.DATA
        ), BrowseOptions.PROJECT_TYPES);
    }

    @Test
    void includesPrefabAndWorldAssetTagsSupportedByTheWebEditor() {
        assertEquals(true, BrowseOptions.GLOBAL_TAGS.containsAll(List.of(
                "Prefab", "Structure", "Dungeon", "Adventure Map", "Server Hub", "Template"
        )));
    }
}
