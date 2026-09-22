package net.modtale.launcher.ui.browse.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectBrowseSourceSelectorTest {

    @Test
    void switchingProviderIsDirectAndDoesNotRepeatTheCurrentSelection() {
        List<ProjectBrowseSource> selected = new ArrayList<>();
        ProjectBrowseSourceSelector selector = new ProjectBrowseSourceSelector(selected::add);

        selector.select(ProjectBrowseSource.MODTALE);
        selector.select(ProjectBrowseSource.CURSEFORGE);
        selector.select(ProjectBrowseSource.CURSEFORGE);
        selector.select(ProjectBrowseSource.MODTALE);

        assertEquals(ProjectBrowseSource.MODTALE, selector.source());
        assertEquals(List.of(ProjectBrowseSource.CURSEFORGE, ProjectBrowseSource.MODTALE), selected);
    }
}
