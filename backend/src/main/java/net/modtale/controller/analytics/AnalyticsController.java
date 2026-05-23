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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    @Autowired private QueryService queryService;
    @Autowired private TrackingService trackingService;
    @Autowired private AccountService accountService;
    @Autowired private ProjectService projectService;
    @Autowired private AccessControlService accessControlService;
    @Autowired private MongoTemplate mongoTemplate;

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
            @RequestParam(required = false) String username
    ) {
        User currentUser = accountService.getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String resolvedTargetUsername = currentUser.getUsername();

        if (username != null && !username.isEmpty() && !username.equalsIgnoreCase(currentUser.getUsername())) {
            User target = mongoTemplate.findOne(new Query(Criteria.where("username").regex("^" + Pattern.quote(username) + "$", "i")), User.class);
            if (target == null) return ResponseEntity.notFound().build();

            if (target.getAccountType() == User.AccountType.ORGANIZATION) {
                if (target.getOrganizationMembers().stream().noneMatch(m -> m.getUserId().equals(currentUser.getId()) && "ADMIN".equals(m.getRole()))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Permission denied.");
                }
            } else return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            resolvedTargetUsername = target.getUsername();
        }

        CreatorAnalytics data = queryService.getCreatorDashboard(resolvedTargetUsername, range, include);
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

    @PostMapping("/analytics/view/{id}")
    public ResponseEntity<Void> trackView(@PathVariable String id, HttpServletRequest request) {
        Project project = projectService.getProjectById(id);
        if (project != null) {
            trackingService.logView(project.getId(), project.getAuthor(), getClientIp(request));
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}