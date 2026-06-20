package net.modtale.launcher.ui.browse.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.modtale.launcher.api.ProjectSearchQuery;
import net.modtale.launcher.model.project.ProjectSummary;
import org.junit.jupiter.api.Test;

class ProjectBrowseSearchStateTest {

    @Test
    void rejectsDuplicateInFlightSearches() {
        ProjectBrowseSearchState state = new ProjectBrowseSearchState();
        ProjectSearchQuery query = query(12);

        long firstRequest = state.start(query);

        assertEquals(ProjectBrowseSearchState.DUPLICATE_SEARCH, state.start(query));
        assertTrue(state.acceptCompletion(query, firstRequest));
        assertEquals(firstRequest + 1, state.start(query));
    }

    @Test
    void rejectsStaleCompletionsAfterANewerSearchStarts() {
        ProjectBrowseSearchState state = new ProjectBrowseSearchState();
        ProjectSearchQuery first = query(12);
        ProjectSearchQuery second = new ProjectSearchQuery(
                "search",
                "",
                "",
                "favorites",
                0,
                12,
                null,
                null,
                null,
                null,
                null,
                null
        );

        long firstRequest = state.start(first);
        long secondRequest = state.start(second);

        assertFalse(state.acceptCompletion(first, firstRequest));
        assertTrue(state.acceptCompletion(second, secondRequest));
    }

    @Test
    void requestsMoreDataWhenLayoutPageSizeGrowsBeyondCompletedResultSet() {
        ProjectBrowseSearchState state = new ProjectBrowseSearchState();
        ProjectSearchQuery completed = query(2);
        state.recordCompleted(completed);

        assertTrue(state.shouldSearchForLayout(query(4), List.of(project("one"), project("two"))));
        assertFalse(state.shouldSearchForLayout(query(2), List.of(project("one"), project("two"))));
    }

    @Test
    void requestsFreshPaginationWhenLayoutPageSizeShrinks() {
        ProjectBrowseSearchState state = new ProjectBrowseSearchState();
        state.recordCompleted(query(6));

        assertTrue(state.shouldSearchForLayout(query(3), List.of(project("one"), project("two"), project("three"))));
    }

    private static ProjectSearchQuery query(int size) {
        return new ProjectSearchQuery(
                "",
                "",
                "",
                "downloads",
                0,
                size,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static ProjectSummary project(String id) {
        return new ProjectSummary(id, id, id, "", "", "", "", "", "PLUGIN", 0, 0, "", List.of());
    }
}
