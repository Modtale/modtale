package net.modtale.service;

import net.modtale.model.analytics.*;
import net.modtale.model.resources.Mod;
import net.modtale.model.resources.ProjectMeta;
import net.modtale.repository.analytics.PlatformMonthlyStatsRepository;
import net.modtale.repository.analytics.ProjectMonthlyStatsRepository;
import net.modtale.repository.resources.ModRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int CHART_BUFFER_DAYS = 14;

    @Autowired private ProjectMonthlyStatsRepository statsRepository;
    @Autowired private PlatformMonthlyStatsRepository platformStatsRepository;
    @Autowired private ModRepository modRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private CacheManager cacheManager;

    private final ConcurrentLinkedQueue<DownloadEvent> downloadBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, AtomicInteger> viewBuffer = new ConcurrentHashMap<>();

    private record DownloadEvent(String projectId, String versionId, String authorId, boolean isApi, String clientIp) {}

    private boolean isDebounced(String projectId, String clientIp, String type) {
        if (clientIp == null || clientIp.isEmpty()) return false;
        Cache cache = cacheManager.getCache("analyticsDebounce");
        if (cache != null) {
            String key = type + ":" + projectId + ":" + clientIp;
            if (cache.get(key) != null) {
                return true;
            }
            cache.put(key, Boolean.TRUE);
        }
        return false;
    }

    public void logDownload(String projectId, String versionId, String authorId, boolean isApi, String clientIp) {
        if (isDebounced(projectId, clientIp, "download")) return;
        downloadBuffer.add(new DownloadEvent(projectId, versionId, authorId, isApi, clientIp));
    }

    public void logView(String projectId, String authorId, String clientIp) {
        if (isDebounced(projectId, clientIp, "view")) return;
        viewBuffer.computeIfAbsent(projectId + "|" + authorId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Scheduled(fixedRate = 10000)
    public void flushAnalyticsBuffer() {
        if (downloadBuffer.isEmpty() && viewBuffer.isEmpty()) return;

        LocalDate now = LocalDate.now();
        int day = now.getDayOfMonth();
        int month = now.getMonthValue();
        int year = now.getYear();

        Map<String, ProjectDownloadAggregate> projectAggregates = new HashMap<>();
        PlatformDownloadAggregate platformAggregate = new PlatformDownloadAggregate();

        DownloadEvent event;
        while ((event = downloadBuffer.poll()) != null) {
            DownloadEvent finalEvent = event;
            ProjectDownloadAggregate pAg = projectAggregates.computeIfAbsent(
                    event.projectId(),
                    k -> new ProjectDownloadAggregate(finalEvent.authorId())
            );
            pAg.total++;
            if (event.isApi()) pAg.api++; else pAg.frontend++;
            if (event.versionId() != null) {
                pAg.versions.merge(event.versionId(), 1, Integer::sum);
            }

            platformAggregate.total++;
            if (event.isApi()) platformAggregate.api++; else platformAggregate.frontend++;
        }

        for (Map.Entry<String, ProjectDownloadAggregate> entry : projectAggregates.entrySet()) {
            String pid = entry.getKey();
            ProjectDownloadAggregate agg = entry.getValue();

            Query q = Query.query(Criteria.where("projectId").is(pid)
                    .and("year").is(year)
                    .and("month").is(month));

            Update u = new Update()
                    .setOnInsert("authorId", agg.authorId)
                    .inc("totalDownloads", agg.total)
                    .inc("apiDownloads", agg.api)
                    .inc("frontendDownloads", agg.frontend)
                    .inc("days." + day + ".d", agg.total);

            for (Map.Entry<String, Integer> vEntry : agg.versions.entrySet()) {
                String safeVersion = vEntry.getKey().replace(".", "_");
                u.inc("versionDownloads." + safeVersion + "." + day, vEntry.getValue());
            }

            try {
                mongoTemplate.upsert(q, u, ProjectMonthlyStats.class);
            } catch (Exception e) {
                logger.error("Failed to flush download stats for project " + pid, e);
            }
        }

        if (platformAggregate.total > 0) {
            try {
                Query pq = Query.query(Criteria.where("year").is(year).and("month").is(month));
                Update pu = new Update()
                        .inc("totalDownloads", platformAggregate.total)
                        .inc("apiDownloads", platformAggregate.api)
                        .inc("frontendDownloads", platformAggregate.frontend)
                        .inc("days." + day + ".d", platformAggregate.total);
                mongoTemplate.upsert(pq, pu, PlatformMonthlyStats.class);
            } catch (Exception e) {
                logger.error("Failed to flush platform download stats", e);
            }
        }

        if (!viewBuffer.isEmpty()) {
            Map<String, Integer> currentBatch = new HashMap<>();

            viewBuffer.forEach((key, atomicCount) -> {
                int count = atomicCount.getAndSet(0);
                if (count > 0) currentBatch.put(key, count);
            });
            viewBuffer.entrySet().removeIf(e -> e.getValue().get() == 0);

            long totalPlatformViews = 0;

            for (Map.Entry<String, Integer> entry : currentBatch.entrySet()) {
                String[] parts = entry.getKey().split("\\|");
                String pid = parts[0];
                String aid = parts.length > 1 ? parts[1] : null;
                int count = entry.getValue();

                Query q = Query.query(Criteria.where("projectId").is(pid)
                        .and("year").is(year)
                        .and("month").is(month));

                Update u = new Update()
                        .setOnInsert("authorId", aid)
                        .inc("totalViews", count)
                        .inc("days." + day + ".v", count);

                try {
                    mongoTemplate.upsert(q, u, ProjectMonthlyStats.class);
                    totalPlatformViews += count;
                } catch (Exception e) {
                    logger.error("Failed to flush view stats for project " + pid, e);
                }
            }

            if (totalPlatformViews > 0) {
                try {
                    Query pq = Query.query(Criteria.where("year").is(year).and("month").is(month));
                    Update pu = new Update()
                            .inc("totalViews", totalPlatformViews)
                            .inc("days." + day + ".v", totalPlatformViews);
                    mongoTemplate.upsert(pq, pu, PlatformMonthlyStats.class);
                } catch (Exception e) {
                    logger.error("Failed to flush platform view stats", e);
                }
            }
        }
    }

    private static class ProjectDownloadAggregate {
        String authorId;
        int total = 0;
        int api = 0;
        int frontend = 0;
        Map<String, Integer> versions = new HashMap<>();

        ProjectDownloadAggregate(String authorId) { this.authorId = authorId; }
    }

    private static class PlatformDownloadAggregate {
        int total = 0;
        int api = 0;
        int frontend = 0;
    }

    public void deleteProjectAnalytics(String projectId) {
        Query query = Query.query(Criteria.where("projectId").is(projectId));
        mongoTemplate.remove(query, ProjectMonthlyStats.class);
    }

    public PlatformAnalyticsSummary getPlatformAnalytics(String range) {
        LocalDate end = LocalDate.now();
        LocalDate start = calculateStartDate(range);

        int startY = start.getYear();
        int startM = start.getMonthValue();

        Criteria criteria = new Criteria().orOperator(
                Criteria.where("year").gt(startY),
                Criteria.where("year").is(startY).and("month").gte(startM)
        );

        List<PlatformMonthlyStats> stats = mongoTemplate.find(Query.query(criteria), PlatformMonthlyStats.class);

        PlatformAnalyticsSummary summary = new PlatformAnalyticsSummary();

        long totalDownloads = 0;
        long totalViews = 0;
        long totalApi = 0;
        long totalFrontend = 0;

        Map<LocalDate, Integer> downloadSeries = new HashMap<>();
        Map<LocalDate, Integer> viewSeries = new HashMap<>();

        for (PlatformMonthlyStats stat : stats) {
            YearMonth ym = YearMonth.of(stat.getYear(), stat.getMonth());

            if (stat.getDays() != null) {
                for (Map.Entry<String, PlatformMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
                    try {
                        int day = Integer.parseInt(entry.getKey());
                        LocalDate date = ym.atDay(day);
                        if (!date.isBefore(start) && !date.isAfter(end)) {
                            downloadSeries.put(date, entry.getValue().getD());
                            viewSeries.put(date, entry.getValue().getV());

                            totalDownloads += entry.getValue().getD();
                            totalViews += entry.getValue().getV();
                        }
                    } catch (Exception ignored) {}
                }
            }

            totalApi += stat.getApiDownloads();
            totalFrontend += stat.getFrontendDownloads();
        }

        summary.setTotalDownloads(totalDownloads);
        summary.setTotalViews(totalViews);
        summary.setApiDownloads(totalApi);
        summary.setFrontendDownloads(totalFrontend);

        summary.setDownloadsChart(fillDates(start, end, downloadSeries));
        summary.setViewsChart(fillDates(start, end, viewSeries));

        return summary;
    }

    public CreatorAnalytics getCreatorDashboard(String username, String range, List<String> include) {
        LocalDate end = LocalDate.now();
        LocalDate start = calculateStartDate(range);

        LocalDate comparisonEnd = start.minusDays(1);
        LocalDate comparisonStart = start.minusDays(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);

        StatsAccumulator currentPeriod = new StatsAccumulator();
        StatsAccumulator prevPeriod = new StatsAccumulator();

        List<ProjectMonthlyStats> allStats = getStatsInMemory(username, comparisonStart, end, false, true);

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

        List<Mod> projects = modRepository.findMetaByAuthor(username);
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
        detail.setTotalApiDownloads(totalStats.apiDownloads);
        detail.setTotalFrontendDownloads(totalStats.frontendDownloads);

        List<ProjectMonthlyStats> stats = getStatsInMemory(projectId, chartStart, end, true, false);
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

    private List<ProjectMonthlyStats> getStatsInMemory(String id, LocalDate start, LocalDate end, boolean isProject, boolean excludeVersions) {
        String field = isProject ? "projectId" : "authorId";

        int startY = start.getYear();
        int startM = start.getMonthValue();

        Criteria criteria = Criteria.where(field).is(id)
                .orOperator(
                        Criteria.where("year").gt(startY),
                        Criteria.where("year").is(startY).and("month").gte(startM)
                );

        Query query = Query.query(criteria);

        if (excludeVersions) {
            query.fields().exclude("versionDownloads");
        }

        return mongoTemplate.find(query, ProjectMonthlyStats.class);
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

        return fillDates(start, end, map);
    }

    private List<AnalyticsDataPoint> fillDates(LocalDate start, LocalDate end, Map<LocalDate, Integer> data) {
        List<AnalyticsDataPoint> points = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate curr = start;
        while (!curr.isAfter(end)) {
            points.add(new AnalyticsDataPoint(curr.format(fmt), data.getOrDefault(curr, 0)));
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
                    String versionId = vEntry.getKey().replace("_", ".");
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

        for (String vid : topVersions) {
            result.put(vid, fillDates(start, end, versionData.get(vid)));
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
        long apiDownloads;
        long frontendDownloads;
    }

    private StatsSummary getAllTimeTotals(String id, boolean isProject) {
        String field = isProject ? "projectId" : "authorId";
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(field).is(id)),
                Aggregation.group()
                        .sum("totalDownloads").as("downloads")
                        .sum("totalViews").as("views")
                        .sum("apiDownloads").as("apiDownloads")
                        .sum("frontendDownloads").as("frontendDownloads")
        );
        AggregationResults<StatsSummary> res = mongoTemplate.aggregate(agg, ProjectMonthlyStats.class, StatsSummary.class);
        StatsSummary summary = res.getUniqueMappedResult();
        return summary != null ? summary : new StatsSummary();
    }
}