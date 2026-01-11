package net.modtale.service;

import net.modtale.model.analytics.*;
import net.modtale.model.resources.Mod;
import net.modtale.model.resources.ProjectMeta;
import net.modtale.repository.analytics.ProjectMonthlyStatsRepository;
import net.modtale.repository.resources.ModRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AnalyticsService {

    private static final int CHART_BUFFER_DAYS = 14;

    @Autowired private ProjectMonthlyStatsRepository statsRepository;
    @Autowired private ModRepository modRepository;
    @Autowired private MongoTemplate mongoTemplate;

    public void logDownload(String projectId, String versionId, String authorId) {
        LocalDate now = LocalDate.now();
        int day = now.getDayOfMonth();
        int month = now.getMonthValue();
        int year = now.getYear();

        Query query = Query.query(Criteria.where("projectId").is(projectId)
                .and("year").is(year)
                .and("month").is(month));

        Update update = new Update()
                .setOnInsert("authorId", authorId)
                .inc("totalDownloads", 1)
                .inc("days." + day + ".d", 1);

        if (versionId != null) {
            update.inc("versionDownloads." + versionId + "." + day, 1);
        }

        mongoTemplate.upsert(query, update, ProjectMonthlyStats.class);
    }

    public void logView(String projectId, String authorId) {
        LocalDate now = LocalDate.now();
        int day = now.getDayOfMonth();
        int month = now.getMonthValue();
        int year = now.getYear();

        Query query = Query.query(Criteria.where("projectId").is(projectId)
                .and("year").is(year)
                .and("month").is(month));

        Update update = new Update()
                .setOnInsert("authorId", authorId)
                .inc("totalViews", 1)
                .inc("days." + day + ".v", 1);

        mongoTemplate.upsert(query, update, ProjectMonthlyStats.class);
    }

    public CreatorAnalytics getCreatorDashboard(String username, String range, List<String> include) {
        LocalDate end = LocalDate.now();
        LocalDate start = calculateStartDate(range);

        LocalDate comparisonEnd = start.minusDays(1);
        LocalDate comparisonStart = start.minusDays(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);

        StatsAccumulator currentPeriod = new StatsAccumulator();
        StatsAccumulator prevPeriod = new StatsAccumulator();

        List<ProjectMonthlyStats> allStats = getStatsInMemory(username, comparisonStart, end, false);

        for (ProjectMonthlyStats stat : allStats) {
            accumulate(stat, start, end, currentPeriod);
            accumulate(stat, comparisonStart, comparisonEnd, prevPeriod);
        }

        StatsSummary allTime = getAllTimeTotals(username, false);

        CreatorAnalytics analytics = new CreatorAnalytics();
        analytics.setTotalDownloads(allTime.downloads);
        analytics.setTotalViews(allTime.views);
        analytics.setPeriodDownloads(currentPeriod.totalDownloads);
        analytics.setPreviousPeriodDownloads(prevPeriod.totalDownloads);
        analytics.setPeriodViews(currentPeriod.totalViews);
        analytics.setPreviousPeriodViews(prevPeriod.totalViews);

        List<Mod> projects = modRepository.findByAuthor(username);
        Map<String, ProjectMeta> metaMap = new HashMap<>();
        Map<String, List<AnalyticsDataPoint>> projectDownloads = new HashMap<>();
        Map<String, List<AnalyticsDataPoint>> projectViews = new HashMap<>();

        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);

        for (Mod mod : projects) {
            metaMap.put(mod.getId(), new ProjectMeta(mod.getId(), mod.getTitle(), mod.getRating(), mod.getDownloadCount()));

            List<ProjectMonthlyStats> modStats = allStats.stream()
                    .filter(s -> s.getProjectId().equals(mod.getId()))
                    .toList();

            projectDownloads.put(mod.getId(), buildTimeSeries(modStats, chartStart, end, true));
            projectViews.put(mod.getId(), buildTimeSeries(modStats, chartStart, end, false));
        }

        analytics.setProjectMeta(metaMap);
        analytics.setProjectDownloads(projectDownloads);
        analytics.setProjectViews(projectViews);

        return analytics;
    }

    public ProjectAnalyticsDetail getProjectAnalytics(String projectId, String username, String range) {
        LocalDate end = LocalDate.now();
        LocalDate start = calculateStartDate(range);
        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);

        Mod mod = modRepository.findById(projectId).orElse(null);
        String title = (mod != null) ? mod.getTitle() : "Unknown";

        ProjectAnalyticsDetail detail = new ProjectAnalyticsDetail();
        detail.setProjectId(projectId);
        detail.setProjectTitle(title);

        StatsSummary totalStats = getAllTimeTotals(projectId, true);
        detail.setTotalDownloads(totalStats.downloads);
        detail.setTotalViews(totalStats.views);

        List<ProjectMonthlyStats> stats = getStatsInMemory(projectId, chartStart, end, true);
        detail.setViews(buildTimeSeries(stats, chartStart, end, false));
        detail.setVersionDownloads(buildVersionBreakdown(stats, start, end));
        detail.setRatingHistory(new ArrayList<>());

        return detail;
    }

    private LocalDate calculateStartDate(String range) {
        if ("7d".equals(range)) return LocalDate.now().minusDays(7);
        if ("90d".equals(range)) return LocalDate.now().minusDays(90);
        if ("1y".equals(range)) return LocalDate.now().minusYears(1);
        return LocalDate.now().minusDays(30);
    }

    private List<ProjectMonthlyStats> getStatsInMemory(String id, LocalDate start, LocalDate end, boolean isProject) {
        String field = isProject ? "projectId" : "authorId";

        int startY = start.getYear();
        int startM = start.getMonthValue();

        Criteria criteria = Criteria.where(field).is(id)
                .orOperator(
                        Criteria.where("year").gt(startY),
                        Criteria.where("year").is(startY).and("month").gte(startM)
                );

        return mongoTemplate.find(Query.query(criteria), ProjectMonthlyStats.class);
    }

    private void accumulate(ProjectMonthlyStats stat, LocalDate start, LocalDate end, StatsAccumulator acc) {
        YearMonth statMonth = YearMonth.of(stat.getYear(), stat.getMonth());

        if (stat.getDays() == null) return;

        for (Map.Entry<String, ProjectMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
            try {
                int day = Integer.parseInt(entry.getKey());
                LocalDate date = statMonth.atDay(day);
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    acc.totalDownloads += entry.getValue().getD();
                    acc.totalViews += entry.getValue().getV();
                }
            } catch (Exception ignored) {}
        }
    }

    private List<AnalyticsDataPoint> buildTimeSeries(List<ProjectMonthlyStats> stats, LocalDate start, LocalDate end, boolean downloads) {
        Map<LocalDate, Integer> map = new HashMap<>();

        for (ProjectMonthlyStats stat : stats) {
            YearMonth ym = YearMonth.of(stat.getYear(), stat.getMonth());
            if (stat.getDays() != null) {
                for (Map.Entry<String, ProjectMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
                    try {
                        int day = Integer.parseInt(entry.getKey());
                        LocalDate date = ym.atDay(day);
                        if (!date.isBefore(start) && !date.isAfter(end)) {
                            int val = downloads ? entry.getValue().getD() : entry.getValue().getV();
                            map.put(date, val);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        List<AnalyticsDataPoint> points = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate curr = start;
        while (!curr.isAfter(end)) {
            points.add(new AnalyticsDataPoint(curr.format(fmt), map.getOrDefault(curr, 0)));
            curr = curr.plusDays(1);
        }
        return points;
    }

    private Map<String, List<AnalyticsDataPoint>> buildVersionBreakdown(List<ProjectMonthlyStats> stats, LocalDate start, LocalDate end) {
        Map<String, Map<LocalDate, Integer>> versionData = new HashMap<>();

        for (ProjectMonthlyStats stat : stats) {
            YearMonth ym = YearMonth.of(stat.getYear(), stat.getMonth());
            if (stat.getVersionDownloads() != null) {
                for (Map.Entry<String, Map<String, Integer>> vEntry : stat.getVersionDownloads().entrySet()) {
                    String versionId = vEntry.getKey();
                    Map<LocalDate, Integer> dateMap = versionData.computeIfAbsent(versionId, k -> new HashMap<>());

                    for (Map.Entry<String, Integer> dayEntry : vEntry.getValue().entrySet()) {
                        try {
                            int day = Integer.parseInt(dayEntry.getKey());
                            LocalDate date = ym.atDay(day);
                            if (!date.isBefore(start) && !date.isAfter(end)) {
                                dateMap.merge(date, dayEntry.getValue(), Integer::sum);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        Map<String, Integer> totals = new HashMap<>();
        for (Map.Entry<String, Map<LocalDate, Integer>> entry : versionData.entrySet()) {
            totals.put(entry.getKey(), entry.getValue().values().stream().mapToInt(i -> i).sum());
        }

        List<String> topVersions = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        Map<String, List<AnalyticsDataPoint>> result = new HashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (String vid : topVersions) {
            List<AnalyticsDataPoint> points = new ArrayList<>();
            Map<LocalDate, Integer> vMap = versionData.get(vid);
            LocalDate curr = start;
            while (!curr.isAfter(end)) {
                points.add(new AnalyticsDataPoint(curr.format(fmt), vMap.getOrDefault(curr, 0)));
                curr = curr.plusDays(1);
            }
            result.put(vid, points);
        }

        return result;
    }

    private static class StatsAccumulator {
        long totalDownloads = 0;
        long totalViews = 0;
    }

    private static class StatsSummary {
        long downloads;
        long views;
    }

    private StatsSummary getAllTimeTotals(String id, boolean isProject) {
        String field = isProject ? "projectId" : "authorId";
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(field).is(id)),
                Aggregation.group()
                        .sum("totalDownloads").as("downloads")
                        .sum("totalViews").as("views")
        );
        AggregationResults<StatsSummary> res = mongoTemplate.aggregate(agg, ProjectMonthlyStats.class, StatsSummary.class);
        StatsSummary summary = res.getUniqueMappedResult();
        return summary != null ? summary : new StatsSummary();
    }
}