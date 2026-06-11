package net.modtale.controller.analytics;

import jakarta.validation.constraints.Pattern;
import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.dto.response.analytics.PlatformStatsView;
import net.modtale.model.user.User;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.service.analytics.QueryService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@RestController
@Validated
@RequestMapping("/api/v1/analytics/platform")
public class PlatformAnalyticsController {

    private final QueryService queryService;
    private final MongoTemplate mongoTemplate;

    public PlatformAnalyticsController(QueryService queryService, MongoTemplate mongoTemplate) {
        this.queryService = queryService;
        this.mongoTemplate = mongoTemplate;
    }

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsView> getPublicStats() {
        long totalProjects = mongoTemplate.count(new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED)), Project.class);
        long totalUsers = mongoTemplate.count(new Query(), User.class);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(ProjectStatus.PUBLISHED)),
                Aggregation.group().sum("downloadCount").as("totalDownloads")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(agg, Project.class, Document.class);
        long totalDownloads = 0;
        Document mappedResult = results.getUniqueMappedResult();
        if (mappedResult != null && mappedResult.get("totalDownloads") != null) {
            totalDownloads = ((Number) mappedResult.get("totalDownloads")).longValue();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePublic())
                .body(new PlatformStatsView(totalProjects, totalUsers, totalDownloads));
    }

    @GetMapping("/full")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<PlatformAnalyticsSummary> getPlatformAnalytics(
            @RequestParam(defaultValue = "30d")
            @Pattern(regexp = "7d|30d|90d|1y", message = "Analytics ranges must be 7d, 30d, 90d, or 1y.")
            String range
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
                .body(queryService.getPlatformAnalytics(range));
    }
}
