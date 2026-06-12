package net.modtale.model.dto.response.analytics;

import net.modtale.model.analytics.AnalyticsDataPoint;

public record AnalyticsDataPointView(
        String date,
        int count,
        double value
) {
    public static AnalyticsDataPointView from(AnalyticsDataPoint point) {
        if (point == null) return null;
        return new AnalyticsDataPointView(point.getDate(), point.getCount(), point.getValue());
    }
}
