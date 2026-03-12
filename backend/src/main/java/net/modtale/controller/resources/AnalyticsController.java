package net.modtale.controller.resources;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.model.user.User;
import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.model.resources.Mod;
import net.modtale.service.AnalyticsService;
import net.modtale.service.user.UserService;
import net.modtale.service.resources.ModService;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    @GetMapping("/analytics/stats")
    public ResponseEntity<Map<String, Long>> getPublicStats() {
        long totalProjects = mongoTemplate.count(new Query(Criteria.where("status").is("PUBLISHED")), Mod.class);
        long totalUsers = mongoTemplate.count(new Query(), User.class);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is("PUBLISHED")),
                Aggregation.group().sum("downloadCount").as("totalDownloads")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(agg, Mod.class, Document.class);
        long totalDownloads = 0;
        Document mappedResult = results.getUniqueMappedResult();
        if (mappedResult != null && mappedResult.get("totalDownloads") != null) {
            totalDownloads = ((Number) mappedResult.get("totalDownloads")).longValue();
        }

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalProjects", totalProjects);
        stats.put("totalUsers", totalUsers);
        stats.put("totalDownloads", totalDownloads);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePublic())
                .body(stats);
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
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
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
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
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