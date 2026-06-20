package net.modtale.launcher.ui.browse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;
import org.junit.jupiter.api.Test;

class ProjectBrowserRendererTest {

    @Test
    void gridUsesTwoOrThreeColumnsAcrossBreakpoints() {
        assertEquals(2, rendererForWidth(719).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(2, rendererForWidth(1319).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(3, rendererForWidth(1320).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(12, rendererForWidth(719).pageSizeForView(ProjectCardViewStyle.GRID));
        assertEquals(12, rendererForWidth(1320).pageSizeForView(ProjectCardViewStyle.GRID));
    }

    @Test
    void compactUsesTwoOrThreeColumnsAcrossBreakpoints() {
        assertEquals(2, rendererForWidth(719).columnsForView(ProjectCardViewStyle.COMPACT));
        assertEquals(3, rendererForWidth(1120).columnsForView(ProjectCardViewStyle.COMPACT));
        assertEquals(45, rendererForWidth(719).pageSizeForView(ProjectCardViewStyle.COMPACT));
        assertEquals(45, rendererForWidth(1120).pageSizeForView(ProjectCardViewStyle.COMPACT));
    }

    @Test
    void explicitPageSizeOverridesViewDefaults() {
        assertEquals(6, rendererForWidth(719).pageSizeForView(ProjectCardViewStyle.GRID, 6));
        assertEquals(48, rendererForWidth(1320).pageSizeForView(ProjectCardViewStyle.LIST, 48));
        assertEquals(96, rendererForWidth(1120).pageSizeForView(ProjectCardViewStyle.COMPACT, 96));
    }

    @Test
    void layoutUsesConstrainedWidthWhenRenderedResultsAreStale() {
        assertEquals(2, rendererForWidths(1320, 936, 936).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(3, rendererForWidths(936, 1320, 1320).columnsForView(ProjectCardViewStyle.GRID));
    }

    private static ProjectBrowserRenderer rendererForWidth(double width) {
        return rendererForWidths(width, width, width);
    }

    private static ProjectBrowserRenderer rendererForWidths(double resultsWidth, double deckWidth, double bodyWidth) {
        StackPane results = sizedStack(resultsWidth);
        StackPane deck = sizedStack(deckWidth);
        VBox body = new VBox();
        body.resize(bodyWidth, 600);
        return new ProjectBrowserRenderer(
                results,
                deck,
                () -> body,
                null,
                id -> false,
                () -> "",
                ProjectBrowserRendererTest::noop,
                ProjectBrowserRendererTest::noop,
                ProjectBrowserRendererTest::noop,
                ProjectBrowserRendererTest::noop
        );
    }

    private static StackPane sizedStack(double width) {
        StackPane pane = new StackPane();
        pane.resize(width, 600);
        return pane;
    }

    private static void noop(ProjectSummary project) {
    }
}
