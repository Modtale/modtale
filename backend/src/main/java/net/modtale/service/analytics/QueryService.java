package net.modtale.service.analytics;

import net.modtale.model.analytics.*;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectMeta;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class QueryService {

    private static final int CHART_BUFFER_DAYS = 14;

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ProjectRepository projectRepository;

    public PlatformAnalyticsSummary getPlatformAnalytics(String range) {
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = calculateStartDate(range);
        LocalDate comparisonStart = start.minusDays(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        LocalDate comparisonEnd = start.minusDays(1);
        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);
        LocalDate fetchStart = comparisonStart.isBefore(chartStart) ? comparisonStart : chartStart;

        List<PlatformMonthlyStats> stats = mongoTemplate.find(Query.query(new Criteria().orOperator(
                Criteria.where("year").gt(fetchStart.getYear()),
                Criteria.where("year").is(fetchStart.getYear()).and("month").gte(fetchStart.getMonthValue())
        )), PlatformMonthlyStats.class);

        PlatformAnalyticsSummary summary = new PlatformAnalyticsSummary();
        Map<LocalDate, Integer> dlSeries = new HashMap<>(); Map<LocalDate, Integer> apiSeries = new HashMap<>(); Map<LocalDate, Integer> viewSeries = new HashMap<>();
        Map<LocalDate, Integer> pSeries = new HashMap<>(); Map<LocalDate, Integer> uSeries = new HashMap<>(); Map<LocalDate, Integer> oSeries = new HashMap<>();

        long cDl = 0, pDl = 0, cV = 0, pV = 0, cApi = 0, pApi = 0, cFront = 0, pFront = 0, cP = 0, pP = 0, cU = 0, pU = 0, cO = 0, pO = 0;

        for (PlatformMonthlyStats stat : stats) {
            YearMonth ym = YearMonth.of(stat.getYear(), stat.getMonth());
            if (stat.getDays() != null) {
                for (Map.Entry<String, PlatformMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
                    try {
                        String dayStr = entry.getKey();
                        PlatformMonthlyStats.DayStats ds = entry.getValue();
                        LocalDate date = ym.atDay(Integer.parseInt(dayStr));

                        if (!date.isBefore(chartStart) && !date.isAfter(end)) {
                            dlSeries.put(date, ds.getD()); apiSeries.put(date, ds.getA()); viewSeries.put(date, ds.getV());
                            pSeries.put(date, ds.getN()); uSeries.put(date, ds.getU()); oSeries.put(date, ds.getO());
                        }

                        if (!date.isBefore(start) && !date.isAfter(end)) {
                            cDl += ds.getD(); cV += ds.getV(); cApi += ds.getA(); cFront += ds.getF(); cP += ds.getN(); cU += ds.getU(); cO += ds.getO();
                        } else if (!date.isBefore(comparisonStart) && !date.isAfter(comparisonEnd)) {
                            pDl += ds.getD(); pV += ds.getV(); pApi += ds.getA(); pFront += ds.getF(); pP += ds.getN(); pU += ds.getU(); pO += ds.getO();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        summary.setTotalDownloads(cDl); summary.setPreviousTotalDownloads(pDl); summary.setTotalViews(cV); summary.setPreviousTotalViews(pV);
        summary.setApiDownloads(cApi); summary.setPreviousApiDownloads(pApi); summary.setFrontendDownloads(cFront); summary.setPreviousFrontendDownloads(pFront);
        summary.setTotalNewProjects(cP); summary.setPreviousTotalNewProjects(pP); summary.setTotalNewUsers(cU); summary.setPreviousTotalNewUsers(pU); summary.setTotalNewOrgs(cO); summary.setPreviousTotalNewOrgs(pO);

        summary.setDownloadsChart(fillDates(chartStart, end, dlSeries)); summary.setApiDownloadsChart(fillDates(chartStart, end, apiSeries)); summary.setViewsChart(fillDates(chartStart, end, viewSeries));
        summary.setNewProjectsChart(fillDates(chartStart, end, pSeries)); summary.setNewUsersChart(fillDates(chartStart, end, uSeries)); summary.setNewOrgsChart(fillDates(chartStart, end, oSeries));
        return summary;
    }

    public CreatorAnalytics getCreatorDashboard(String userId, String range, List<String> include) {
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = calculateStartDate(range);
        LocalDate comparisonStart = start.minusDays(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        LocalDate comparisonEnd = start.minusDays(1);
        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);

        List<Project> ownedProjects = projectRepository.findByAuthorIdList(userId);
        List<String> ownedProjectIds = ownedProjects.stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .toList();

        List<ProjectMonthlyStats> allStats = ownedProjectIds.isEmpty()
                ? List.of()
                : getStatsForProjectsInMemory(ownedProjectIds, comparisonStart, end, true);
        StatsAccumulator curr = new StatsAccumulator(), prev = new StatsAccumulator();

        for (ProjectMonthlyStats stat : allStats) {
            accumulate(stat, start, end, curr);
            accumulate(stat, comparisonStart, comparisonEnd, prev);
        }

        StatsSummary allTime = ownedProjectIds.isEmpty() ? new StatsSummary() : getAllTimeTotalsForProjects(ownedProjectIds);
        CreatorAnalytics analytics = new CreatorAnalytics();
        analytics.setTotalDownloads(allTime.downloads); analytics.setTotalViews(allTime.views);
        analytics.setPeriodDownloads(curr.dls); analytics.setPreviousPeriodDownloads(prev.dls);
        analytics.setPeriodViews(curr.views); analytics.setPreviousPeriodViews(prev.views);

        Map<String, ProjectMeta> metaMap = new HashMap<>(); Map<String, List<AnalyticsDataPoint>> pDls = new HashMap<>(); Map<String, List<AnalyticsDataPoint>> pViews = new HashMap<>();
        ownedProjects.forEach(p -> {
            metaMap.put(p.getId(), new ProjectMeta(p.getId(), p.getTitle(), p.getDownloadCount()));
            List<ProjectMonthlyStats> modStats = allStats.stream().filter(s -> s.getProjectId().equals(p.getId())).toList();
            pDls.put(p.getId(), buildTimeSeries(modStats, chartStart, end, true));
            pViews.put(p.getId(), buildTimeSeries(modStats, chartStart, end, false));
        });

        analytics.setProjectMeta(metaMap); analytics.setProjectDownloads(pDls); analytics.setProjectViews(pViews);
        return analytics;
    }

    public ProjectAnalyticsDetail getProjectAnalytics(String projectId, String userId, String range) {
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = calculateStartDate(range);
        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);

        Project project = projectRepository.findById(projectId).orElse(null);
        ProjectAnalyticsDetail detail = new ProjectAnalyticsDetail();
        detail.setProjectId(projectId); detail.setProjectTitle(project != null ? project.getTitle() : "Unknown");

        StatsSummary totalStats = getAllTimeTotals(projectId, true);
        detail.setTotalDownloads(totalStats.downloads); detail.setTotalViews(totalStats.views);
        detail.setTotalApiDownloads(totalStats.apiDownloads); detail.setTotalFrontendDownloads(totalStats.frontendDownloads);

        List<ProjectMonthlyStats> stats = getStatsInMemory(projectId, chartStart, end, true, false);
        detail.setViews(buildTimeSeries(stats, chartStart, end, false));
        detail.setVersionDownloads(buildVersionBreakdown(stats, start, end));

        return detail;
    }

    private LocalDate calculateStartDate(String range) {
        LocalDate baseDate = LocalDate.now().minusDays(1);
        if ("7d".equals(range)) return baseDate.minusDays(7);
        if ("90d".equals(range)) return baseDate.minusDays(90);
        if ("1y".equals(range)) return baseDate.minusYears(1);
        return baseDate.minusDays(30);
    }

    private List<ProjectMonthlyStats> getStatsInMemory(String id, LocalDate start, LocalDate end, boolean isProject, boolean excludeVersions) {
        Query query = Query.query(Criteria.where(isProject ? "projectId" : "authorId").is(id).orOperator(
                Criteria.where("year").gt(start.getYear()), Criteria.where("year").is(start.getYear()).and("month").gte(start.getMonthValue())
        ));
        if (excludeVersions) query.fields().exclude("versionDownloads");
        return mongoTemplate.find(query, ProjectMonthlyStats.class);
    }

    private List<ProjectMonthlyStats> getStatsForProjectsInMemory(List<String> projectIds, LocalDate start, LocalDate end, boolean excludeVersions) {
        Query query = Query.query(Criteria.where("projectId").in(projectIds).orOperator(
                Criteria.where("year").gt(start.getYear()),
                Criteria.where("year").is(start.getYear()).and("month").gte(start.getMonthValue())
        ));
        if (excludeVersions) query.fields().exclude("versionDownloads");
        return mongoTemplate.find(query, ProjectMonthlyStats.class);
    }

    private void accumulate(ProjectMonthlyStats stat, LocalDate start, LocalDate end, StatsAccumulator acc) {
        if (stat.getDays() == null) return;
        YearMonth ym = YearMonth.of(stat.getYear(), stat.getMonth());

        for (Map.Entry<String, ProjectMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
            try {
                LocalDate date = ym.atDay(Integer.parseInt(entry.getKey()));
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    acc.dls += entry.getValue().getD();
                    acc.views += entry.getValue().getV();
                }
            } catch (Exception ignored) {}
        }
    }

    private List<AnalyticsDataPoint> buildTimeSeries(List<ProjectMonthlyStats> stats, LocalDate start, LocalDate end, boolean downloads) {
        Map<LocalDate, Integer> map = new HashMap<>();
        for (ProjectMonthlyStats stat : stats) {
            if (stat.getDays() != null) {
                YearMonth ym = YearMonth.of(stat.getYear(), stat.getMonth());
                for (Map.Entry<String, ProjectMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
                    try {
                        LocalDate date = ym.atDay(Integer.parseInt(entry.getKey()));
                        if (!date.isBefore(start) && !date.isAfter(end)) {
                            map.put(date, downloads ? entry.getValue().getD() : entry.getValue().getV());
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return fillDates(start, end, map);
    }

    private Map<String, List<AnalyticsDataPoint>> buildVersionBreakdown(List<ProjectMonthlyStats> stats, LocalDate start, LocalDate end) {
        Map<String, Map<LocalDate, Integer>> versionData = new HashMap<>();
        for (ProjectMonthlyStats stat : stats) {
            if (stat.getVersionDownloads() != null) {
                YearMonth ym = YearMonth.of(stat.getYear(), stat.getMonth());
                for (Map.Entry<String, Map<String, Integer>> vEntry : stat.getVersionDownloads().entrySet()) {
                    String vid = vEntry.getKey().replace("_", ".");
                    Map<LocalDate, Integer> map = versionData.computeIfAbsent(vid, k -> new HashMap<>());

                    for (Map.Entry<String, Integer> dayEntry : vEntry.getValue().entrySet()) {
                        try {
                            LocalDate date = ym.atDay(Integer.parseInt(dayEntry.getKey()));
                            if (!date.isBefore(start) && !date.isAfter(end)) {
                                map.merge(date, dayEntry.getValue(), Integer::sum);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        List<String> topVids = versionData.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(
                        e2.getValue().values().stream().mapToInt(i -> i).sum(),
                        e1.getValue().values().stream().mapToInt(i -> i).sum()
                ))
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        Map<String, List<AnalyticsDataPoint>> result = new HashMap<>();
        for (String vid : topVids) {
            result.put(vid, fillDates(start, end, versionData.get(vid)));
        }
        return result;
    }

    private List<AnalyticsDataPoint> fillDates(LocalDate start, LocalDate end, Map<LocalDate, Integer> data) {
        List<AnalyticsDataPoint> points = new ArrayList<>(); DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (LocalDate curr = start; !curr.isAfter(end); curr = curr.plusDays(1)) points.add(new AnalyticsDataPoint(curr.format(fmt), data.getOrDefault(curr, 0)));
        return points;
    }

    private StatsSummary getAllTimeTotals(String id, boolean isProject) {
        StatsSummary s = mongoTemplate.aggregate(Aggregation.newAggregation(Aggregation.match(Criteria.where(isProject ? "projectId" : "authorId").is(id)), Aggregation.group().sum("totalDownloads").as("downloads").sum("totalViews").as("views").sum("apiDownloads").as("apiDownloads").sum("frontendDownloads").as("frontendDownloads")), ProjectMonthlyStats.class, StatsSummary.class).getUniqueMappedResult();
        return s != null ? s : new StatsSummary();
    }

    private StatsSummary getAllTimeTotalsForProjects(List<String> projectIds) {
        StatsSummary s = mongoTemplate.aggregate(
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
        return s != null ? s : new StatsSummary();
    }

    private static class StatsAccumulator { long dls = 0; long views = 0; }
    private static class StatsSummary { long downloads; long views; long apiDownloads; long frontendDownloads; }
}
