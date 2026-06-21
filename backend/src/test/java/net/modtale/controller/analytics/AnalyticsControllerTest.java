package net.modtale.controller.analytics;

import java.util.List;
import java.util.Map;
import net.modtale.model.analytics.AnalyticsDataPoint;
import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectMeta;
import net.modtale.model.user.User;
import net.modtale.service.analytics.AnalyticsAccessService;
import net.modtale.service.analytics.AnalyticsEligibilityService;
import net.modtale.service.analytics.QueryService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {

    private AnalyticsController controller;
    private AnalyticsAccessService analyticsAccessService;
    private AnalyticsEligibilityService analyticsEligibilityService;
    private QueryService queryService;
    private TrackingService trackingService;
    private AccountService accountService;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        analyticsAccessService = mock(AnalyticsAccessService.class);
        analyticsEligibilityService = mock(AnalyticsEligibilityService.class);
        queryService = mock(QueryService.class);
        trackingService = mock(TrackingService.class);
        accountService = mock(AccountService.class);
        projectService = mock(ProjectService.class);
        controller = new AnalyticsController(
                analyticsAccessService,
                analyticsEligibilityService,
                queryService,
                trackingService,
                accountService,
                projectService
        );
    }

    @Test
    void getCreatorAnalyticsUsesResolvedTargetId() {
        User currentUser = user("user-1");
        CreatorAnalytics analytics = new CreatorAnalytics();
        analytics.setTotalDownloads(12);
        analytics.setProjectMeta(Map.of("project-1", new ProjectMeta("project-1", "Sky Tools", 12, "2026-06-01")));
        analytics.setProjectDownloads(Map.of("project-1", List.of(new AnalyticsDataPoint("2026-06-01", 3))));
        List<String> include = List.of("downloads");

        when(accountService.requireCurrentUser("viewing creator analytics")).thenReturn(currentUser);
        when(analyticsAccessService.resolveCreatorAnalyticsTargetId(currentUser, "org-1")).thenReturn("org-1");
        when(queryService.getCreatorDashboard("org-1", "30d", include)).thenReturn(analytics);

        var response = controller.getCreatorAnalytics("30d", include, "org-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(12, response.getBody().totalDownloads());
        assertEquals("Sky Tools", response.getBody().projectMeta().get("project-1").title());
        assertEquals(3, response.getBody().projectDownloads().get("project-1").getFirst().count());
        verify(analyticsAccessService).resolveCreatorAnalyticsTargetId(currentUser, "org-1");
        verify(queryService).getCreatorDashboard("org-1", "30d", include);
    }

    @Test
    void getProjectAnalyticsUsesAnonymousViewerWhenNoUserIsSignedIn() {
        Project project = new Project();
        project.setId("project-1");
        ProjectAnalyticsDetail detail = new ProjectAnalyticsDetail();
        detail.setProjectId("project-1");
        detail.setProjectTitle("Sky Tools");
        detail.setViews(List.of(new AnalyticsDataPoint("2026-06-01", 5)));

        when(accountService.getCurrentUser((org.springframework.security.core.Authentication) null)).thenReturn(null);
        when(projectService.getProjectById("project-1", null)).thenReturn(project);
        when(queryService.getProjectAnalytics("project-1", "anon", "7d")).thenReturn(detail);

        var response = controller.getProjectAnalytics("project-1", "7d", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("project-1", response.getBody().projectId());
        assertEquals("Sky Tools", response.getBody().projectTitle());
        assertEquals(5, response.getBody().views().getFirst().count());
        verify(analyticsAccessService).assertProjectAnalyticsAccess(project, null);
        verify(queryService).getProjectAnalytics("project-1", "anon", "7d");
    }

    @Test
    void trackViewReturnsNotFoundWhenProjectDoesNotExist() {
        when(projectService.getProjectById("missing")).thenReturn(null);

        var response = controller.trackView("missing", null, new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void trackViewSkipsAnalyticsForAffiliatedUsers() {
        Project project = new Project();
        project.setId("project-1");
        User currentUser = user("user-1");

        when(projectService.getProjectById("project-1")).thenReturn(project);
        when(accountService.getCurrentUser((org.springframework.security.core.Authentication) null)).thenReturn(currentUser);
        when(analyticsEligibilityService.shouldCountProjectEngagement(project, currentUser)).thenReturn(false);

        var response = controller.trackView("project-1", null, new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void trackViewLogsAnalyticsForEligibleUsers() {
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("author-1");
        User currentUser = user("user-2");
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");

        when(projectService.getProjectById("project-1")).thenReturn(project);
        when(accountService.getCurrentUser((org.springframework.security.core.Authentication) null)).thenReturn(currentUser);
        when(analyticsEligibilityService.shouldCountProjectEngagement(project, currentUser)).thenReturn(true);

        var response = controller.trackView("project-1", null, request);

        assertEquals(200, response.getStatusCode().value());
        verify(trackingService).logView("project-1", "author-1", "203.0.113.9");
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
