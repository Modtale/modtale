package net.modtale.controller.resources;

import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.user.User;
import net.modtale.model.resources.Mod;
import net.modtale.service.AnalyticsService;
import net.modtale.service.user.UserService;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/analytics/platform")
public class PlatformAnalyticsController {

    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    @Autowired private AnalyticsService analyticsService;
    @Autowired private UserService userService;
    @Autowired private MongoTemplate mongoTemplate;

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    @GetMapping("/stats")
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

    @GetMapping("/full")
    public ResponseEntity<PlatformAnalyticsSummary> getPlatformAnalytics(@RequestParam(defaultValue = "30d") String range) {
        User user = userService.getCurrentUser();

        boolean isSuper = user != null && SUPER_ADMIN_ID.equals(user.getId());
        boolean isAdmin = user != null && user.getRoles() != null && user.getRoles().contains("ADMIN");

        if (!isSuper && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
                .body(analyticsService.getPlatformAnalytics(range));
    }
}