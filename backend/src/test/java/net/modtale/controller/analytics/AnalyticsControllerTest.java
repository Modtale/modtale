package net.modtale.controller.analytics;

import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.analytics.AnalyticsAccessService;
import net.modtale.service.analytics.QueryService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.user.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {

    private AnalyticsController controller;
    private AnalyticsAccessService analyticsAccessService;
    private QueryService queryService;
    private TrackingService trackingService;
    private AccountService accountService;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        analyticsAccessService = mock(AnalyticsAccessService.class);
        queryService = mock(QueryService.class);
        trackingService = mock(TrackingService.class);
        accountService = mock(AccountService.class);
        projectService = mock(ProjectService.class);
        controller = new AnalyticsController(
                analyticsAccessService,
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
        List<String> include = List.of("downloads");

        when(accountService.requireCurrentUser("viewing creator analytics")).thenReturn(currentUser);
        when(analyticsAccessService.resolveCreatorAnalyticsTargetId(currentUser, "org-1")).thenReturn("org-1");
        when(queryService.getCreatorDashboard("org-1", "30d", include)).thenReturn(analytics);

        var response = controller.getCreatorAnalytics("30d", include, "org-1");

        assertEquals(200, response.getStatusCode().value());
        assertSame(analytics, response.getBody());
        verify(analyticsAccessService).resolveCreatorAnalyticsTargetId(currentUser, "org-1");
        verify(queryService).getCreatorDashboard("org-1", "30d", include);
    }

    @Test
    void getProjectAnalyticsUsesAnonymousViewerWhenNoUserIsSignedIn() {
        Project project = new Project();
        project.setId("project-1");
        ProjectAnalyticsDetail detail = new ProjectAnalyticsDetail();

        when(accountService.getCurrentUser((org.springframework.security.core.Authentication) null)).thenReturn(null);
        when(projectService.getProjectById("project-1", null)).thenReturn(project);
        when(queryService.getProjectAnalytics("project-1", "anon", "7d")).thenReturn(detail);

        var response = controller.getProjectAnalytics("project-1", "7d");

        assertEquals(200, response.getStatusCode().value());
        assertSame(detail, response.getBody());
        verify(analyticsAccessService).assertProjectAnalyticsAccess(project, null);
        verify(queryService).getProjectAnalytics("project-1", "anon", "7d");
    }

    @Test
    void trackViewReturnsNotFoundWhenProjectDoesNotExist() {
        when(projectService.getProjectById("missing")).thenReturn(null);

        var response = controller.trackView("missing", new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(404, response.getStatusCode().value());
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
