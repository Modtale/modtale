package net.modtale.service.analytics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.modtale.model.analytics.AnalyticsDataPoint;
import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.model.analytics.ProjectMonthlyStats;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectMeta;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.stereotype.Service;

@Service
public class CreatorProjectAnalyticsQueryService {

    private final ProjectRepository projectRepository;
    private final AnalyticsQuerySupportService analyticsQuerySupportService;

    public CreatorProjectAnalyticsQueryService(
            ProjectRepository projectRepository,
            AnalyticsQuerySupportService analyticsQuerySupportService
    ) {
        this.projectRepository = projectRepository;
        this.analyticsQuerySupportService = analyticsQuerySupportService;
    }

    public CreatorAnalytics getCreatorDashboard(String userId, String range, List<String> include) {
        AnalyticsQuerySupportService.DateWindow window = analyticsQuerySupportService.buildDateWindow(range);

        List<Project> ownedProjects = projectRepository.findByAuthorIdList(userId);
        List<String> ownedProjectIds = ownedProjects.stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .toList();

        List<ProjectMonthlyStats> allStats = ownedProjectIds.isEmpty()
                ? List.of()
                : analyticsQuerySupportService.getStatsForProjectsInMemory(ownedProjectIds, window.comparisonStart(), true);

        AnalyticsQuerySupportService.StatsAccumulator current = new AnalyticsQuerySupportService.StatsAccumulator();
        AnalyticsQuerySupportService.StatsAccumulator previous = new AnalyticsQuerySupportService.StatsAccumulator();

        for (ProjectMonthlyStats stat : allStats) {
            analyticsQuerySupportService.accumulate(stat, window.start(), window.end(), current);
            analyticsQuerySupportService.accumulate(stat, window.comparisonStart(), window.comparisonEnd(), previous);
        }

        AnalyticsQuerySupportService.StatsSummary allTime = ownedProjectIds.isEmpty()
                ? new AnalyticsQuerySupportService.StatsSummary()
                : analyticsQuerySupportService.getAllTimeTotalsForProjects(ownedProjectIds);

        CreatorAnalytics analytics = new CreatorAnalytics();
        analytics.setTotalDownloads(allTime.downloads);
        analytics.setTotalViews(allTime.views);
        analytics.setPeriodDownloads(current.downloads);
        analytics.setPreviousPeriodDownloads(previous.downloads);
        analytics.setPeriodViews(current.views);
        analytics.setPreviousPeriodViews(previous.views);

        Map<String, ProjectMeta> metaMap = new HashMap<>();
        Map<String, List<AnalyticsDataPoint>> projectDownloads = new HashMap<>();
        Map<String, List<AnalyticsDataPoint>> projectViews = new HashMap<>();

        ownedProjects.forEach(project -> {
            metaMap.put(project.getId(), new ProjectMeta(project.getId(), project.getTitle(), project.getDownloadCount(), project.getUpdatedAt()));
            List<ProjectMonthlyStats> projectStats = allStats.stream()
                    .filter(stat -> stat.getProjectId().equals(project.getId()))
                    .toList();
            projectDownloads.put(
                    project.getId(),
                    analyticsQuerySupportService.buildTimeSeries(projectStats, window.chartStart(), window.end(), true)
            );
            projectViews.put(
                    project.getId(),
                    analyticsQuerySupportService.buildTimeSeries(projectStats, window.chartStart(), window.end(), false)
            );
        });

        analytics.setProjectMeta(metaMap);
        analytics.setProjectDownloads(projectDownloads);
        analytics.setProjectViews(projectViews);
        return analytics;
    }

    public ProjectAnalyticsDetail getProjectAnalytics(String projectId, String userId, String range) {
        AnalyticsQuerySupportService.DateWindow window = analyticsQuerySupportService.buildDateWindow(range);

        Project project = projectRepository.findById(projectId).orElse(null);
        ProjectAnalyticsDetail detail = new ProjectAnalyticsDetail();
        detail.setProjectId(projectId);
        detail.setProjectTitle(project != null ? project.getTitle() : "Unknown");

        AnalyticsQuerySupportService.StatsSummary totals = analyticsQuerySupportService.getAllTimeTotals(projectId, true);
        detail.setTotalDownloads(totals.downloads);
        detail.setTotalViews(totals.views);
        detail.setTotalApiDownloads(totals.apiDownloads);
        detail.setTotalFrontendDownloads(totals.frontendDownloads);

        List<ProjectMonthlyStats> stats = analyticsQuerySupportService.getStatsInMemory(projectId, window.chartStart(), true, false);
        detail.setViews(analyticsQuerySupportService.buildTimeSeries(stats, window.chartStart(), window.end(), false));
        detail.setVersionDownloads(analyticsQuerySupportService.buildVersionBreakdown(stats, window.start(), window.end()));
        return detail;
    }
}
