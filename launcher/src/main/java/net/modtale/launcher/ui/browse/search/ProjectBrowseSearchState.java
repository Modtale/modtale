package net.modtale.launcher.ui.browse.search;

import java.util.List;
import java.util.Objects;
import net.modtale.launcher.api.ProjectSearchQuery;
import net.modtale.launcher.model.project.ProjectSummary;

public final class ProjectBrowseSearchState {

    public static final long DUPLICATE_SEARCH = -1;

    private long sequence;
    private ProjectSearchQuery inFlightSearch;
    private ProjectSearchQuery lastCompletedSearch;

    public long start(ProjectSearchQuery query) {
        if (query.equals(inFlightSearch)) {
            return DUPLICATE_SEARCH;
        }
        inFlightSearch = query;
        return ++sequence;
    }

    public boolean acceptCompletion(ProjectSearchQuery query, long requestId) {
        if (query.equals(inFlightSearch)) {
            inFlightSearch = null;
        }
        return requestId == sequence;
    }

    public void recordCompleted(ProjectSearchQuery query) {
        lastCompletedSearch = query;
    }

    public boolean shouldSearchForLayout(ProjectSearchQuery nextQuery, List<ProjectSummary> currentProjects) {
        if (lastCompletedSearch == null) {
            return true;
        }
        if (!sameSearchWithoutSize(nextQuery, lastCompletedSearch)) {
            return true;
        }
        if (nextQuery.size() < lastCompletedSearch.size()) {
            return true;
        }
        return nextQuery.size() > lastCompletedSearch.size()
                && currentProjects.size() >= lastCompletedSearch.size();
    }

    private static boolean sameSearchWithoutSize(ProjectSearchQuery left, ProjectSearchQuery right) {
        return Objects.equals(left.search(), right.search())
                && Objects.equals(left.classification(), right.classification())
                && Objects.equals(left.gameVersion(), right.gameVersion())
                && Objects.equals(left.sort(), right.sort())
                && left.page() == right.page()
                && Objects.equals(left.tags(), right.tags())
                && Objects.equals(left.minDownloads(), right.minDownloads())
                && Objects.equals(left.minFavorites(), right.minFavorites())
                && Objects.equals(left.category(), right.category())
                && Objects.equals(left.dateRange(), right.dateRange())
                && Objects.equals(left.openSource(), right.openSource());
    }
}
