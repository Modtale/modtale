package net.modtale.model.dto.response.analytics;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.modtale.model.analytics.AnalyticsDataPoint;
import net.modtale.model.analytics.ProjectAnalyticsDetail;

public record ProjectAnalyticsDetailView(
        String projectId,
        String projectTitle,
        long totalDownloads,
        long totalViews,
        long totalApiDownloads,
        long totalFrontendDownloads,
        List<AnalyticsDataPointView> views,
        Map<String, List<AnalyticsDataPointView>> versionDownloads
) {
    public static ProjectAnalyticsDetailView from(ProjectAnalyticsDetail detail) {
        if (detail == null) return null;
        return new ProjectAnalyticsDetailView(
                detail.getProjectId(),
                detail.getProjectTitle(),
                detail.getTotalDownloads(),
                detail.getTotalViews(),
                detail.getTotalApiDownloads(),
                detail.getTotalFrontendDownloads(),
                toPoints(detail.getViews()),
                toPointMap(detail.getVersionDownloads())
        );
    }

    private static List<AnalyticsDataPointView> toPoints(List<AnalyticsDataPoint> points) {
        if (points == null) return List.of();
        return points.stream().map(AnalyticsDataPointView::from).toList();
    }

    private static Map<String, List<AnalyticsDataPointView>> toPointMap(Map<String, List<AnalyticsDataPoint>> pointsByKey) {
        if (pointsByKey == null) return Map.of();
        return pointsByKey.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toPoints(entry.getValue())));
    }
}
