package net.modtale.service.analytics;

import net.modtale.model.analytics.AnalyticsDataPoint;
import net.modtale.model.analytics.ProjectMonthlyStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsQuerySupportService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsQuerySupportService.class);
    private static final int CHART_BUFFER_DAYS = 14;

    private final MongoTemplate mongoTemplate;

    public AnalyticsQuerySupportService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public DateWindow buildDateWindow(String range) {
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = calculateStartDate(range, end);
        LocalDate comparisonStart = start.minusDays(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        LocalDate comparisonEnd = start.minusDays(1);
        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);
        LocalDate fetchStart = comparisonStart.isBefore(chartStart) ? comparisonStart : chartStart;
        return new DateWindow(start, end, comparisonStart, comparisonEnd, chartStart, fetchStart);
    }

    public List<ProjectMonthlyStats> getStatsInMemory(String id, LocalDate start, boolean isProject, boolean excludeVersions) {
        Query query = Query.query(Criteria.where(isProject ? "projectId" : "authorId").is(id).orOperator(
                Criteria.where("year").gt(start.getYear()),
                Criteria.where("year").is(start.getYear()).and("month").gte(start.getMonthValue())
        ));
        if (excludeVersions) {
            query.fields().exclude("versionDownloads");
        }
        return mongoTemplate.find(query, ProjectMonthlyStats.class);
    }

    public List<ProjectMonthlyStats> getStatsForProjectsInMemory(List<String> projectIds, LocalDate start, boolean excludeVersions) {
        Query query = Query.query(Criteria.where("projectId").in(projectIds).orOperator(
                Criteria.where("year").gt(start.getYear()),
                Criteria.where("year").is(start.getYear()).and("month").gte(start.getMonthValue())
        ));
        if (excludeVersions) {
            query.fields().exclude("versionDownloads");
        }
        return mongoTemplate.find(query, ProjectMonthlyStats.class);
    }

    public void accumulate(ProjectMonthlyStats stat, LocalDate start, LocalDate end, StatsAccumulator accumulator) {
        if (stat.getDays() == null) {
            return;
        }
        YearMonth yearMonth = YearMonth.of(stat.getYear(), stat.getMonth());

        for (Map.Entry<String, ProjectMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
            LocalDate date = parseStatDate(yearMonth, entry.getKey(), "project analytics totals");
            if (date != null && !date.isBefore(start) && !date.isAfter(end)) {
                accumulator.downloads += entry.getValue().getD();
                accumulator.views += entry.getValue().getV();
            }
        }
    }

    public List<AnalyticsDataPoint> buildTimeSeries(
            List<ProjectMonthlyStats> stats,
            LocalDate start,
            LocalDate end,
            boolean downloads
    ) {
        Map<LocalDate, Integer> map = new HashMap<>();
        for (ProjectMonthlyStats stat : stats) {
            if (stat.getDays() == null) {
                continue;
            }

            YearMonth yearMonth = YearMonth.of(stat.getYear(), stat.getMonth());
            for (Map.Entry<String, ProjectMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
                LocalDate date = parseStatDate(yearMonth, entry.getKey(), "project analytics series");
                if (date != null && !date.isBefore(start) && !date.isAfter(end)) {
                    map.put(date, downloads ? entry.getValue().getD() : entry.getValue().getV());
                }
            }
        }
        return fillDates(start, end, map);
    }

    public Map<String, List<AnalyticsDataPoint>> buildVersionBreakdown(
            List<ProjectMonthlyStats> stats,
            LocalDate start,
            LocalDate end
    ) {
        Map<String, Map<LocalDate, Integer>> versionData = new HashMap<>();
        for (ProjectMonthlyStats stat : stats) {
            if (stat.getVersionDownloads() == null) {
                continue;
            }

            YearMonth yearMonth = YearMonth.of(stat.getYear(), stat.getMonth());
            for (Map.Entry<String, Map<String, Integer>> versionEntry : stat.getVersionDownloads().entrySet()) {
                String versionId = versionEntry.getKey().replace("_", ".");
                Map<LocalDate, Integer> map = versionData.computeIfAbsent(versionId, ignored -> new HashMap<>());

                for (Map.Entry<String, Integer> dayEntry : versionEntry.getValue().entrySet()) {
                    LocalDate date = parseStatDate(yearMonth, dayEntry.getKey(), "version analytics series");
                    if (date != null && !date.isBefore(start) && !date.isAfter(end)) {
                        map.merge(date, dayEntry.getValue(), Integer::sum);
                    }
                }
            }
        }

        List<String> topVersionIds = versionData.entrySet().stream()
                .sorted((left, right) -> Integer.compare(
                        right.getValue().values().stream().mapToInt(Integer::intValue).sum(),
                        left.getValue().values().stream().mapToInt(Integer::intValue).sum()
                ))
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        Map<String, List<AnalyticsDataPoint>> result = new HashMap<>();
        for (String versionId : topVersionIds) {
            result.put(versionId, fillDates(start, end, versionData.get(versionId)));
        }
        return result;
    }

    public List<AnalyticsDataPoint> fillDates(LocalDate start, LocalDate end, Map<LocalDate, Integer> data) {
        List<AnalyticsDataPoint> points = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (LocalDate current = start; !current.isAfter(end); current = current.plusDays(1)) {
            points.add(new AnalyticsDataPoint(current.format(formatter), data.getOrDefault(current, 0)));
        }
        return points;
    }

    public StatsSummary getAllTimeTotals(String id, boolean isProject) {
        StatsSummary summary = mongoTemplate.aggregate(
                Aggregation.newAggregation(
                        Aggregation.match(Criteria.where(isProject ? "projectId" : "authorId").is(id)),
                        Aggregation.group()
                                .sum("totalDownloads").as("downloads")
                                .sum("totalViews").as("views")
                                .sum("apiDownloads").as("apiDownloads")
                                .sum("frontendDownloads").as("frontendDownloads")
                ),
                ProjectMonthlyStats.class,
                StatsSummary.class
        ).getUniqueMappedResult();
        return summary != null ? summary : new StatsSummary();
    }

    public StatsSummary getAllTimeTotalsForProjects(List<String> projectIds) {
        StatsSummary summary = mongoTemplate.aggregate(
                Aggregation.newAggregation(
                        Aggregation.match(Criteria.where("projectId").in(projectIds)),
                        Aggregation.group()
                                .sum("totalDownloads").as("downloads")
                                .sum("totalViews").as("views")
                                .sum("apiDownloads").as("apiDownloads")
                                .sum("frontendDownloads").as("frontendDownloads")
                ),
                ProjectMonthlyStats.class,
                StatsSummary.class
        ).getUniqueMappedResult();
        return summary != null ? summary : new StatsSummary();
    }

    public LocalDate parseStatDate(YearMonth month, String dayToken, String context) {
        try {
            return month.atDay(Integer.parseInt(dayToken));
        } catch (NumberFormatException | DateTimeException ex) {
            logger.warn("Skipping invalid {} day '{}' for {}", context, dayToken, month, ex);
            return null;
        }
    }

    private LocalDate calculateStartDate(String range, LocalDate baseDate) {
        if ("7d".equals(range)) {
            return baseDate.minusDays(7);
        }
        if ("90d".equals(range)) {
            return baseDate.minusDays(90);
        }
        if ("1y".equals(range)) {
            return baseDate.minusYears(1);
        }
        return baseDate.minusDays(30);
    }

    public record DateWindow(
            LocalDate start,
            LocalDate end,
            LocalDate comparisonStart,
            LocalDate comparisonEnd,
            LocalDate chartStart,
            LocalDate fetchStart
    ) {
    }

    public static final class StatsAccumulator {
        long downloads = 0;
        long views = 0;
    }

    public static class StatsSummary {
        long downloads;
        long views;
        long apiDownloads;
        long frontendDownloads;
    }
}
