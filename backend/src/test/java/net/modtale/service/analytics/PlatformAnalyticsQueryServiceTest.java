package net.modtale.service.analytics;

import java.time.LocalDate;
import java.util.List;
import net.modtale.model.analytics.PlatformMonthlyStats;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAnalyticsQueryServiceTest {
    @Test
    void splitsDownloadSourcesAndFillsMissingLauncherDays() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        AnalyticsQuerySupportService support = new AnalyticsQuerySupportService(mongo);
        var window = support.buildDateWindow("7d");
        var current = stats(window.end(), 12, 3, 4);
        var previous = stats(window.comparisonEnd(), 8, 2, 1);
        when(mongo.find(any(Query.class), eq(PlatformMonthlyStats.class))).thenReturn(List.of(current, previous));

        var summary = new PlatformAnalyticsQueryService(mongo, support).getPlatformAnalytics("7d");

        assertEquals(12, summary.getTotalDownloads());
        assertEquals(8, summary.getPreviousTotalDownloads());
        assertEquals(4, summary.getLauncherDownloads());
        assertEquals(1, summary.getPreviousLauncherDownloads());
        assertEquals(5, summary.getFrontendDownloads());
        assertEquals(3, summary.getApiDownloads());
        assertEquals(5, summary.getDownloadsChart().getLast().getCount());
        assertEquals(3, summary.getApiDownloadsChart().getLast().getCount());
        assertEquals(4, summary.getLauncherDownloadsChart().getLast().getCount());
        assertEquals(0, summary.getLauncherDownloadsChart().getFirst().getCount());
        assertEquals(summary.getDownloadsChart().size(), summary.getLauncherDownloadsChart().size());
    }

    @Test
    void legacyDaysHaveNoLauncherDownloads() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var stat = stats(LocalDate.now().minusDays(1), 9, 2, 0);
        when(mongo.find(any(Query.class), eq(PlatformMonthlyStats.class))).thenReturn(List.of(stat));
        var summary = new PlatformAnalyticsQueryService(mongo, new AnalyticsQuerySupportService(mongo)).getPlatformAnalytics("7d");
        assertEquals(0, summary.getLauncherDownloads());
        assertEquals(7, summary.getDownloadsChart().getLast().getCount());
    }

    private PlatformMonthlyStats stats(LocalDate date, int total, int api, int launcher) {
        var stats = new PlatformMonthlyStats();
        stats.setYear(date.getYear());
        stats.setMonth(date.getMonthValue());
        var day = new PlatformMonthlyStats.DayStats();
        day.setD(total);
        day.setA(api);
        day.setL(launcher);
        day.setF(total - api - launcher);
        stats.getDays().put(Integer.toString(date.getDayOfMonth()), day);
        return stats;
    }
}
