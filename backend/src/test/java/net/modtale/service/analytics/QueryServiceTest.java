package net.modtale.service.analytics;

import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectMonthlyStats;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryServiceTest {

    private QueryService queryService;
    private MongoTemplate mongoTemplate;
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        projectRepository = mock(ProjectRepository.class);
        queryService = new QueryService(mongoTemplate, projectRepository);
    }

    @Test
    void creatorDashboardAggregatesOwnedProjectsEvenWhenStoredAuthorIdDoesNotMatchUserId() {
        Project alpha = project("project-1", "Alpha", 150);
        Project beta = project("project-2", "Beta", 75);
        LocalDate yesterday = LocalDate.now().minusDays(1);

        ProjectMonthlyStats alphaStats = monthlyStats("project-1", "legacy-username", yesterday, 9, 4);
        ProjectMonthlyStats betaStats = monthlyStats("project-2", "legacy-username", yesterday, 6, 2);

        when(projectRepository.findByAuthorIdList("user-1")).thenReturn(List.of(alpha, beta));
        when(mongoTemplate.find(any(), eq(ProjectMonthlyStats.class))).thenReturn(List.of(alphaStats, betaStats));
        when(mongoTemplate.aggregate(any(), eq(ProjectMonthlyStats.class), ArgumentMatchers.<Class<?>>any()))
                .thenAnswer(invocation -> {
                    Class<?> resultClass = invocation.getArgument(2);
                    var constructor = resultClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object totals = constructor.newInstance();
                    ReflectionTestUtils.setField(totals, "downloads", 15L);
                    ReflectionTestUtils.setField(totals, "views", 6L);
                    return new org.springframework.data.mongodb.core.aggregation.AggregationResults<>(List.of(totals), new Document());
                });

        CreatorAnalytics analytics = queryService.getCreatorDashboard("user-1", "7d", null);

        assertEquals(15, analytics.getTotalDownloads());
        assertEquals(6, analytics.getTotalViews());
        assertEquals(15, analytics.getPeriodDownloads());
        assertEquals(6, analytics.getPeriodViews());
        assertEquals(List.of("project-1", "project-2"), analytics.getProjectMeta().keySet().stream().sorted().toList());
        assertEquals(9, analytics.getProjectDownloads().get("project-1").getLast().getCount());
        assertEquals(2, analytics.getProjectViews().get("project-2").getLast().getCount());
    }

    @Test
    void creatorDashboardReturnsEmptyAnalyticsWhenTheUserOwnsNoProjects() {
        when(projectRepository.findByAuthorIdList("user-1")).thenReturn(List.of());

        CreatorAnalytics analytics = queryService.getCreatorDashboard("user-1", "30d", null);

        assertEquals(0, analytics.getTotalDownloads());
        assertEquals(0, analytics.getPeriodDownloads());
        assertTrue(analytics.getProjectMeta().isEmpty());
        verify(mongoTemplate, never()).find(any(), eq(ProjectMonthlyStats.class));
        verify(mongoTemplate, never()).aggregate(any(), eq(ProjectMonthlyStats.class), ArgumentMatchers.<Class<?>>any());
    }

    private static Project project(String id, String title, int downloadCount) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setDownloadCount(downloadCount);
        return project;
    }

    private static ProjectMonthlyStats monthlyStats(String projectId, String authorId, LocalDate date, int downloads, int views) {
        ProjectMonthlyStats stats = new ProjectMonthlyStats();
        stats.setProjectId(projectId);
        stats.setAuthorId(authorId);
        stats.setYear(date.getYear());
        stats.setMonth(date.getMonthValue());

        ProjectMonthlyStats.DayStats dayStats = new ProjectMonthlyStats.DayStats();
        dayStats.setD(downloads);
        dayStats.setV(views);

        HashMap<String, ProjectMonthlyStats.DayStats> days = new HashMap<>();
        days.put(String.valueOf(date.getDayOfMonth()), dayStats);
        stats.setDays(days);
        return stats;
    }

}
