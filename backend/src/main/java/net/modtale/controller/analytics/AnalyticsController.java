package net.modtale.controller.analytics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.modtale.model.dto.response.analytics.CreatorAnalyticsView;
import net.modtale.model.dto.response.analytics.ProjectAnalyticsDetailView;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.analytics.AnalyticsAccessService;
import net.modtale.service.analytics.AnalyticsEligibilityService;
import net.modtale.service.analytics.QueryService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1")
public class AnalyticsController {

    private final AnalyticsAccessService analyticsAccessService;
    private final AnalyticsEligibilityService analyticsEligibilityService;
    private final QueryService queryService;
    private final TrackingService trackingService;
    private final AccountService accountService;
    private final ProjectService projectService;

    public AnalyticsController(
            AnalyticsAccessService analyticsAccessService,
            AnalyticsEligibilityService analyticsEligibilityService,
            QueryService queryService,
            TrackingService trackingService,
            AccountService accountService,
            ProjectService projectService
    ) {
        this.analyticsAccessService = analyticsAccessService;
        this.analyticsEligibilityService = analyticsEligibilityService;
        this.queryService = queryService;
        this.trackingService = trackingService;
        this.accountService = accountService;
        this.projectService = projectService;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0];
    }

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
    }

    @GetMapping("/user/analytics")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<CreatorAnalyticsView> getCreatorAnalytics(
            @RequestParam(defaultValue = "30d")
            @Pattern(regexp = "7d|30d|90d|1y", message = "Analytics ranges must be 7d, 30d, 90d, or 1y.")
            String range,
            @RequestParam(required = false) List<String> include,
            @RequestParam(required = false) String userId
    ) {
        User currentUser = accountService.requireCurrentUser("viewing creator analytics");
        String resolvedTargetId = analyticsAccessService.resolveCreatorAnalyticsTargetId(currentUser, userId);
        var data = queryService.getCreatorDashboard(resolvedTargetId, range, include);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
                .body(CreatorAnalyticsView.from(data));
    }

    @GetMapping("/projects/{id}/analytics")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectAnalyticsDetailView> getProjectAnalytics(
            @PathVariable String id,
            @RequestParam(defaultValue = "30d")
            @Pattern(regexp = "7d|30d|90d|1y", message = "Analytics ranges must be 7d, 30d, 90d, or 1y.")
            String range,
            Authentication authentication
    ) {
        User user = accountService.getCurrentUser(authentication);
        Project project = projectService.getProjectById(id, user);
        analyticsAccessService.assertProjectAnalyticsAccess(project, user);
        String projectId = (project != null) ? project.getId() : id;
        var data = queryService.getProjectAnalytics(projectId, user != null ? user.getUsername() : "anon", range);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
                .body(ProjectAnalyticsDetailView.from(data));
    }

    @PostMapping({"/analytics/view/{id}", "/views/project/{id}"})
    public ResponseEntity<Void> trackView(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        Project project = projectService.getProjectById(id);
        if (project != null) {
            User currentUser = accountService.getCurrentUser(authentication);
            if (analyticsEligibilityService.shouldCountProjectEngagement(project, currentUser)) {
                trackingService.logView(project.getId(), project.getAuthorId(), getClientIp(request));
            }
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
