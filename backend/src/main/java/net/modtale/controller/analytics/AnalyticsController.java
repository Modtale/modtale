package net.modtale.controller.analytics;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.service.analytics.QueryService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    @Autowired private QueryService queryService;
    @Autowired private TrackingService trackingService;
    @Autowired private AccountService accountService;
    @Autowired private ProjectService projectService;
    @Autowired private AccessControlService accessControlService;

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0];
    }

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
    }

    @GetMapping("/user/analytics")
    public ResponseEntity<?> getCreatorAnalytics(
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) List<String> include,
            @RequestParam(required = false) String userId
    ) {
        User currentUser = accountService.getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String resolvedTargetId = currentUser.getId();

        if (userId != null && !userId.isEmpty() && !userId.equals(currentUser.getId())) {
            User target = accountService.getPublicProfile(userId);
            if (target == null) return ResponseEntity.notFound().build();

            if (target.getAccountType() == User.AccountType.ORGANIZATION) {
                if (!accessControlService.hasOrgPerm(target.getId(), "PROJECT_EDIT_METADATA", null)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Permission denied.");
                }
            } else return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            resolvedTargetId = target.getId();
        }

        CreatorAnalytics data = queryService.getCreatorDashboard(resolvedTargetId, range, include);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate()).body(data);
    }

    @GetMapping("/projects/{id}/analytics")
    public ResponseEntity<?> getProjectAnalytics(@PathVariable String id, @RequestParam(defaultValue = "30d") String range) {
        User user = accountService.getCurrentUser();
        Project project = projectService.getProjectById(id);

        if (project != null && project.getStatus() == ProjectStatus.DRAFT) {
            if (user == null || !accessControlService.hasEditPermission(project, user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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
