package net.modtale.model.dto.response.analytics;

import java.util.List;
import net.modtale.model.analytics.AnalyticsDataPoint;
import net.modtale.model.analytics.PlatformAnalyticsSummary;

public record PlatformAnalyticsSummaryView(
        long totalDownloads,
        long previousTotalDownloads,
        long totalViews,
        long previousTotalViews,
        long apiDownloads,
        long previousApiDownloads,
        long frontendDownloads,
        long previousFrontendDownloads,
        long totalNewProjects,
        long previousTotalNewProjects,
        long totalNewUsers,
        long previousTotalNewUsers,
        long totalNewOrgs,
        long previousTotalNewOrgs,
        List<AnalyticsDataPointView> downloadsChart,
        List<AnalyticsDataPointView> viewsChart,
        List<AnalyticsDataPointView> newProjectsChart,
        List<AnalyticsDataPointView> newUsersChart,
        List<AnalyticsDataPointView> newOrgsChart,
        List<AnalyticsDataPointView> apiDownloadsChart
) {
    public static PlatformAnalyticsSummaryView from(PlatformAnalyticsSummary summary) {
        if (summary == null) return null;
        return new PlatformAnalyticsSummaryView(
                summary.getTotalDownloads(),
                summary.getPreviousTotalDownloads(),
                summary.getTotalViews(),
                summary.getPreviousTotalViews(),
                summary.getApiDownloads(),
                summary.getPreviousApiDownloads(),
                summary.getFrontendDownloads(),
                summary.getPreviousFrontendDownloads(),
                summary.getTotalNewProjects(),
                summary.getPreviousTotalNewProjects(),
                summary.getTotalNewUsers(),
                summary.getPreviousTotalNewUsers(),
                summary.getTotalNewOrgs(),
                summary.getPreviousTotalNewOrgs(),
                toPoints(summary.getDownloadsChart()),
                toPoints(summary.getViewsChart()),
                toPoints(summary.getNewProjectsChart()),
                toPoints(summary.getNewUsersChart()),
                toPoints(summary.getNewOrgsChart()),
                toPoints(summary.getApiDownloadsChart())
        );
    }

    private static List<AnalyticsDataPointView> toPoints(List<AnalyticsDataPoint> points) {
        if (points == null) return List.of();
        return points.stream().map(AnalyticsDataPointView::from).toList();
    }
}
