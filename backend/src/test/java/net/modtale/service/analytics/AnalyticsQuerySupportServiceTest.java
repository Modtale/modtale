package net.modtale.service.analytics;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.modtale.model.analytics.AnalyticsDataPoint;
import net.modtale.model.analytics.ProjectMonthlyStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsQuerySupportServiceTest {

    private MongoTemplate mongoTemplate;
    private AnalyticsQuerySupportService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        service = new AnalyticsQuerySupportService(mongoTemplate);
    }

    @Test
    void buildDateWindowCreatesComparisonChartAndFetchBounds() {
        AnalyticsQuerySupportService.DateWindow window = service.buildDateWindow("7d");
        LocalDate expectedEnd = LocalDate.now().minusDays(1);
        LocalDate expectedStart = expectedEnd.minusDays(7);

        assertEquals(expectedStart, window.start());
        assertEquals(expectedEnd, window.end());
        assertEquals(expectedStart.minusDays(8), window.comparisonStart());
        assertEquals(expectedStart.minusDays(1), window.comparisonEnd());
        assertEquals(expectedStart.minusDays(14), window.chartStart());
        assertEquals(window.chartStart(), window.fetchStart());
    }

    @Test
    void buildDateWindowDefaultsUnknownRangesToThirtyDays() {
        AnalyticsQuerySupportService.DateWindow window = service.buildDateWindow("forever");

        assertEquals(LocalDate.now().minusDays(31), window.start());
    }

    @Test
    void getStatsInMemoryUsesProjectOrAuthorCriteriaAndCanExcludeVersionDownloads() {
        when(mongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(ProjectMonthlyStats.class)))
                .thenReturn(List.of());

        service.getStatsInMemory("project-1", LocalDate.of(2026, 5, 10), true, true);
        service.getStatsInMemory("author-1", LocalDate.of(2026, 5, 10), false, false);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, org.mockito.Mockito.times(2)).find(queryCaptor.capture(), eq(ProjectMonthlyStats.class));

        String projectQuery = queryCaptor.getAllValues().get(0).getQueryObject().toJson();
        String authorQuery = queryCaptor.getAllValues().get(1).getQueryObject().toJson();
        assertTrue(projectQuery.contains("\"projectId\": \"project-1\""));
        assertTrue(projectQuery.contains("\"year\""));
        assertEquals(0, queryCaptor.getAllValues().get(0).getFieldsObject().get("versionDownloads"));
        assertTrue(authorQuery.contains("\"authorId\": \"author-1\""));
    }

    @Test
    void getStatsForProjectsInMemoryQueriesAllProjectIds() {
        when(mongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(ProjectMonthlyStats.class)))
                .thenReturn(List.of());

        service.getStatsForProjectsInMemory(List.of("project-1", "project-2"), LocalDate.of(2026, 1, 1), true);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(ProjectMonthlyStats.class));
        String query = queryCaptor.getValue().getQueryObject().toJson();
        assertTrue(query.contains("project-1"));
        assertTrue(query.contains("project-2"));
        assertEquals(0, queryCaptor.getValue().getFieldsObject().get("versionDownloads"));
    }

    @Test
    void accumulateIgnoresInvalidAndOutOfRangeDays() {
        ProjectMonthlyStats stats = monthlyStats(2026, 6, Map.of(
                "1", day(10, 2),
                "31", day(99, 99),
                "bad", day(99, 99)
        ));
        AnalyticsQuerySupportService.StatsAccumulator accumulator = new AnalyticsQuerySupportService.StatsAccumulator();

        service.accumulate(stats, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), accumulator);

        assertEquals(10, accumulator.downloads);
        assertEquals(2, accumulator.views);
    }

    @Test
    void buildTimeSeriesFillsSparseDatesWithZeros() {
        ProjectMonthlyStats stats = monthlyStats(2026, 6, Map.of(
                "1", day(10, 2),
                "3", day(30, 6)
        ));

        List<AnalyticsDataPoint> series = service.buildTimeSeries(
                List.of(stats),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                true
        );

        assertEquals(List.of("2026-06-01", "2026-06-02", "2026-06-03"), dates(series));
        assertEquals(List.of(10, 0, 30), counts(series));
    }

    @Test
    void buildVersionBreakdownRestoresDottedIdsMergesDaysAndLimitsToTopTenVersions() {
        ProjectMonthlyStats stats = new ProjectMonthlyStats();
        stats.setYear(2026);
        stats.setMonth(6);
        Map<String, Map<String, Integer>> versionDownloads = new LinkedHashMap<>();
        for (int index = 1; index <= 12; index++) {
            versionDownloads.put("version_" + index, new HashMap<>(Map.of(
                    "1", index,
                    "2", index
            )));
        }
        versionDownloads.put("release_1_0", new HashMap<>(Map.of("1", 100)));
        stats.setVersionDownloads(versionDownloads);

        Map<String, List<AnalyticsDataPoint>> breakdown = service.buildVersionBreakdown(
                List.of(stats),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2)
        );

        assertEquals(10, breakdown.size());
        assertTrue(breakdown.containsKey("release.1.0"));
        assertEquals(List.of(100, 0), counts(breakdown.get("release.1.0")));
        assertTrue(breakdown.containsKey("version.12"));
        assertTrue(breakdown.containsKey("version.4"));
        assertTrue(!breakdown.containsKey("version.3"));
    }

    @Test
    void parseStatDateReturnsNullForInvalidTokens() {
        assertNull(service.parseStatDate(YearMonth.of(2026, 2), "31", "test"));
        assertNull(service.parseStatDate(YearMonth.of(2026, 2), "wat", "test"));
        assertEquals(LocalDate.of(2026, 2, 28), service.parseStatDate(YearMonth.of(2026, 2), "28", "test"));
    }

    private static ProjectMonthlyStats monthlyStats(
            int year,
            int month,
            Map<String, ProjectMonthlyStats.DayStats> days
    ) {
        ProjectMonthlyStats stats = new ProjectMonthlyStats();
        stats.setYear(year);
        stats.setMonth(month);
        stats.setDays(days);
        return stats;
    }

    private static ProjectMonthlyStats.DayStats day(int downloads, int views) {
        ProjectMonthlyStats.DayStats day = new ProjectMonthlyStats.DayStats();
        day.setD(downloads);
        day.setV(views);
        return day;
    }

    private static List<String> dates(List<AnalyticsDataPoint> points) {
        return points.stream().map(AnalyticsDataPoint::getDate).toList();
    }

    private static List<Integer> counts(List<AnalyticsDataPoint> points) {
        return points.stream().map(AnalyticsDataPoint::getCount).toList();
    }
}
