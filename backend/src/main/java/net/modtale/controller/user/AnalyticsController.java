package net.modtale.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.model.user.User;
import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.model.resources.Mod;
import net.modtale.service.AnalyticsService;
import net.modtale.service.user.UserService;
import net.modtale.service.resources.ModService;
import net.modtale.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    @Autowired private AnalyticsService analyticsService;
    @Autowired private UserService userService;
    @Autowired private ModService modService;
    @Autowired private UserRepository userRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    @GetMapping("/user/analytics")
    public ResponseEntity<?> getCreatorAnalytics(
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) List<String> include,
            @RequestParam(required = false) String username
    ) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String resolvedTargetUsername = currentUser.getUsername();

        if (username != null && !username.isEmpty() && !username.equalsIgnoreCase(currentUser.getUsername())) {
            Query query = new Query(Criteria.where("username").regex("^" + Pattern.quote(username) + "$", "i"));
            User target = mongoTemplate.findOne(query, User.class);

            if (target == null) return ResponseEntity.notFound().build();

            if (target.getAccountType() == User.AccountType.ORGANIZATION) {
                boolean isAdmin = target.getOrganizationMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(currentUser.getId()) && "ADMIN".equals(m.getRole()));

                if (!isAdmin) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to view analytics for this organization.");
                }
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            resolvedTargetUsername = target.getUsername();
        }

        CreatorAnalytics data = analyticsService.getCreatorDashboard(resolvedTargetUsername, range, include);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(data);
    }

    @GetMapping("/projects/{id}/analytics")
    public ResponseEntity<?> getProjectAnalytics(
            @PathVariable String id,
            @RequestParam(defaultValue = "30d") String range
    ) {
        User user = userService.getCurrentUser();
        Mod mod = modService.getModById(id);

        if (mod != null && "DRAFT".equals(mod.getStatus())) {
            if (user == null || !modService.hasEditPermission(mod, user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        String projectId = (mod != null) ? mod.getId() : id;
        ProjectAnalyticsDetail data = analyticsService.getProjectAnalytics(projectId, user != null ? user.getUsername() : "anon", range);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(data);
    }

    @PostMapping("/analytics/view/{id}")
    public ResponseEntity<Void> trackView(@PathVariable String id, HttpServletRequest request) {
        Mod mod = modService.getModById(id);

        if (mod != null) {
            analyticsService.logView(mod.getId(), mod.getAuthor(), getClientIp(request));
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.notFound().build();
    }
}