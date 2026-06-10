package net.modtale.controller.analytics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.analytics.AnalyticsAccessService;
import net.modtale.service.analytics.QueryService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.user.AccountService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@Validated
@RequestMapping("/api/v1")
public class AnalyticsController {

    private final AnalyticsAccessService analyticsAccessService;
    private final QueryService queryService;
    private final TrackingService trackingService;
    private final AccountService accountService;
    private final ProjectService projectService;

    public AnalyticsController(
            AnalyticsAccessService analyticsAccessService,
            QueryService queryService,
            TrackingService trackingService,
            AccountService accountService,
            ProjectService projectService
    ) {
        this.analyticsAccessService = analyticsAccessService;
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
    public ResponseEntity<CreatorAnalytics> getCreatorAnalytics(
            @RequestParam(defaultValue = "30d")
            @Pattern(regexp = "7d|30d|90d|1y", message = "Analytics ranges must be 7d, 30d, 90d, or 1y.")
            String range,
            @RequestParam(required = false) List<String> include,
            @RequestParam(required = false) String userId
    ) {
        User currentUser = accountService.requireCurrentUser("viewing creator analytics");
        String resolvedTargetId = analyticsAccessService.resolveCreatorAnalyticsTargetId(currentUser, userId);
        CreatorAnalytics data = queryService.getCreatorDashboard(resolvedTargetId, range, include);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate()).body(data);
    }

    @GetMapping("/projects/{id}/analytics")
    public ResponseEntity<ProjectAnalyticsDetail> getProjectAnalytics(
            @PathVariable String id,
            @RequestParam(defaultValue = "30d")
            @Pattern(regexp = "7d|30d|90d|1y", message = "Analytics ranges must be 7d, 30d, 90d, or 1y.")
            String range
    ) {
        User user = accountService.getCurrentUser();
        Project project = projectService.getProjectById(id, user);
        analyticsAccessService.assertProjectAnalyticsAccess(project, user);
        String projectId = (project != null) ? project.getId() : id;
        ProjectAnalyticsDetail data = queryService.getProjectAnalytics(projectId, user != null ? user.getUsername() : "anon", range);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate()).body(data);
    }

    @PostMapping({"/analytics/view/{id}", "/views/project/{id}"})
    public ResponseEntity<Void> trackView(@PathVariable String id, HttpServletRequest request) {
        Project project = projectService.getProjectById(id);
        if (project != null) {
            trackingService.logView(project.getId(), project.getAuthorId(), getClientIp(request));
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
