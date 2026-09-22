package net.modtale.launcher.ui.browse.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Insets;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;
import org.junit.jupiter.api.Test;

class ProjectBrowserRendererTest {

    @Test
    void gridUsesTheWebColumnBreakpoints() {
        assertEquals(1, rendererForWidth(543).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(2, rendererForWidth(544).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(2, rendererForWidth(831).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(3, rendererForWidth(832).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(12, rendererForWidth(543).pageSizeForView(ProjectCardViewStyle.GRID));
        assertEquals(12, rendererForWidth(832).pageSizeForView(ProjectCardViewStyle.GRID));
    }

    @Test
    void compactUsesTheSameColumnBreakpoints() {
        assertEquals(1, rendererForWidth(543).columnsForView(ProjectCardViewStyle.COMPACT));
        assertEquals(2, rendererForWidth(544).columnsForView(ProjectCardViewStyle.COMPACT));
        assertEquals(3, rendererForWidth(832).columnsForView(ProjectCardViewStyle.COMPACT));
        assertEquals(45, rendererForWidth(543).pageSizeForView(ProjectCardViewStyle.COMPACT));
        assertEquals(45, rendererForWidth(832).pageSizeForView(ProjectCardViewStyle.COMPACT));
    }

    @Test
    void explicitPageSizeOverridesViewDefaults() {
        assertEquals(6, rendererForWidth(719).pageSizeForView(ProjectCardViewStyle.GRID, 6));
        assertEquals(48, rendererForWidth(1320).pageSizeForView(ProjectCardViewStyle.LIST, 48));
        assertEquals(96, rendererForWidth(1120).pageSizeForView(ProjectCardViewStyle.COMPACT, 96));
    }

    @Test
    void layoutUsesConstrainedWidthWhenRenderedResultsAreStale() {
        assertEquals(2, rendererForWidths(832, 719, 719).columnsForView(ProjectCardViewStyle.GRID));
        assertEquals(3, rendererForWidths(719, 832, 832).columnsForView(ProjectCardViewStyle.GRID));
    }

    @Test
    void viewportLayoutReservesTheContentGutterForNavbarAlignment() {
        assertEquals(1208, ProjectBrowserRenderer.contentWidthInside(1320, new Insets(0, 112, 0, 0)));
        assertEquals(1096, ProjectBrowserRenderer.contentWidthInside(1320, new Insets(0, 112, 0, 112)));
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
