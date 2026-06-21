package net.modtale.model.dto.response.analytics;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.modtale.model.analytics.AnalyticsDataPoint;
import net.modtale.model.analytics.CreatorAnalytics;

public record CreatorAnalyticsView(
        long totalDownloads,
        long totalViews,
        long periodDownloads,
        long previousPeriodDownloads,
        long periodViews,
        long previousPeriodViews,
        Map<String, List<AnalyticsDataPointView>> projectDownloads,
        Map<String, List<AnalyticsDataPointView>> projectViews,
        Map<String, ProjectAnalyticsMetaView> projectMeta
) {
    public static CreatorAnalyticsView from(CreatorAnalytics analytics) {
        if (analytics == null) return null;
        return new CreatorAnalyticsView(
                analytics.getTotalDownloads(),
                analytics.getTotalViews(),
                analytics.getPeriodDownloads(),
                analytics.getPreviousPeriodDownloads(),
                analytics.getPeriodViews(),
                analytics.getPreviousPeriodViews(),
                toPointMap(analytics.getProjectDownloads()),
                toPointMap(analytics.getProjectViews()),
                ProjectAnalyticsMetaView.fromMap(analytics.getProjectMeta())
        );
    }

    private static Map<String, List<AnalyticsDataPointView>> toPointMap(Map<String, List<AnalyticsDataPoint>> pointsByKey) {
        if (pointsByKey == null) return Map.of();
        return pointsByKey.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null
                                ? List.of()
                                : entry.getValue().stream().map(AnalyticsDataPointView::from).toList()
                ));
    }
}
