package net.modtale.service.analytics;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.modtale.model.analytics.PlatformMonthlyStats;
import net.modtale.model.analytics.ProjectMonthlyStats;
import net.modtale.model.project.Project;
import net.modtale.service.project.query.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class TrackingFlushService {

    private static final Logger logger = LoggerFactory.getLogger(TrackingFlushService.class);

    private final MongoTemplate mongoTemplate;
    private final ProjectService projectService;
    private final TrackingBufferService trackingBufferService;
    private final CacheManager cacheManager;

    @Autowired
    public TrackingFlushService(
            MongoTemplate mongoTemplate,
            ProjectService projectService,
            TrackingBufferService trackingBufferService,
            CacheManager cacheManager
    ) {
        this.mongoTemplate = mongoTemplate;
        this.projectService = projectService;
        this.trackingBufferService = trackingBufferService;
        this.cacheManager = cacheManager;
    }

    public TrackingFlushService(
            MongoTemplate mongoTemplate,
            ProjectService projectService,
            TrackingBufferService trackingBufferService
    ) {
        this(mongoTemplate, projectService, trackingBufferService, null);
    }

    public void flushAnalyticsBuffer() {
        boolean flushedBaseMetrics = flushBaseProjectMetrics();
        boolean flushedMonthlyStats = flushMonthlyStats();
        boolean flushedPlatformEntityStats = flushPlatformEntityStats();
        if (flushedBaseMetrics || flushedMonthlyStats || flushedPlatformEntityStats) {
            evictAnalyticsCaches();
        }
    }

    public void deleteProjectAnalytics(String projectId) {
        mongoTemplate.remove(Query.query(Criteria.where("projectId").is(projectId)), ProjectMonthlyStats.class);
    }

    private boolean flushBaseProjectMetrics() {
        TrackingBufferService.MetricsBatch batch = trackingBufferService.drainMetricIncrements();
        if (batch.isEmpty()) {
            return false;
        }

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
        Set<String> allIdsToEvict = new HashSet<>(batch.downloads().keySet());

        for (Map.Entry<String, Integer> entry : batch.downloads().entrySet()) {
            bulkOps.updateOne(
                    new Query(Criteria.where("_id").is(entry.getKey())),
                    new Update()
                            .inc("downloadCount", entry.getValue())
                            .set("rankingDirty", true)
            );
        }

        for (Map.Entry<String, Integer> entry : batch.versionDownloads().entrySet()) {
            String[] parts = entry.getKey().split("\\|\\|\\|");
            bulkOps.updateOne(
                    new Query(Criteria.where("_id").is(parts[0]).and("versions._id").is(parts[1])),
                    new Update().inc("versions.$.downloadCount", entry.getValue())
            );
            allIdsToEvict.add(parts[0]);
        }

        try {
            bulkOps.execute();
            evictUpdatedProjectCaches(allIdsToEvict);
            return true;
        } catch (Exception e) {
            logger.error("Failed to bulk flush metrics", e);
            trackingBufferService.restoreMetricIncrements(batch);
            return false;
        }
    }

    private void evictUpdatedProjectCaches(Set<String> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return;
        }

        Query query = Query.query(Criteria.where("_id").in(projectIds));
        query.fields()
                .include("_id")
                .include("slug")
                .include("title")
                .include("classification");

        List<Project> projects = mongoTemplate.find(query, Project.class);
        Set<String> foundIds = new HashSet<>();
        projects.stream()
                .map(Project::getId)
                .filter(id -> id != null && !id.isBlank())
                .forEach(foundIds::add);

        Set<String> missingIds = new HashSet<>(projectIds);
        missingIds.removeAll(foundIds);
        projectService.evictProjectDetailsCaches(projects, missingIds);
    }

    private boolean flushMonthlyStats() {
        TrackingBufferService.MonthlyAnalyticsBatch batch = trackingBufferService.drainMonthlyAnalytics();
        if (batch.isEmpty()) {
            return false;
        }

        LocalDate now = LocalDate.now();
        int day = now.getDayOfMonth();
        int month = now.getMonthValue();
        int year = now.getYear();

        Map<String, ProjectAgg> projectAggs = new HashMap<>();
        PlatformAgg platformAgg = new PlatformAgg();

        for (TrackingBufferService.DownloadEvent event : batch.downloads()) {
            ProjectAgg projectAgg = projectAggs.computeIfAbsent(event.projectId(), ignored -> new ProjectAgg(event.authorId()));
            projectAgg.total++;
            platformAgg.total++;

            if (event.isApi()) {
                projectAgg.api++;
                platformAgg.api++;
            } else {
                projectAgg.frontend++;
                platformAgg.frontend++;
            }

            if (event.versionId() != null) {
                projectAgg.versions.merge(event.versionId(), 1, Integer::sum);
            }
        }

        for (Map.Entry<String, ProjectAgg> entry : projectAggs.entrySet()) {
            String projectId = entry.getKey();
            ProjectAgg aggregate = entry.getValue();

            Update update = new Update()
                    .setOnInsert("authorId", aggregate.authorId)
                    .inc("totalDownloads", aggregate.total)
                    .inc("apiDownloads", aggregate.api)
                    .inc("frontendDownloads", aggregate.frontend)
                    .inc("days." + day + ".d", aggregate.total)
                    .inc("days." + day + ".a", aggregate.api)
                    .inc("days." + day + ".f", aggregate.frontend);

            for (Map.Entry<String, Integer> versionEntry : aggregate.versions.entrySet()) {
                update.inc("versionDownloads." + versionEntry.getKey().replace(".", "_") + "." + day, versionEntry.getValue());
            }

            mongoTemplate.upsert(
                    Query.query(Criteria.where("projectId").is(projectId).and("year").is(year).and("month").is(month)),
                    update,
                    ProjectMonthlyStats.class
            );
        }

        if (platformAgg.total > 0) {
            Update update = new Update()
                    .inc("totalDownloads", platformAgg.total)
                    .inc("apiDownloads", platformAgg.api)
                    .inc("frontendDownloads", platformAgg.frontend)
                    .inc("days." + day + ".d", platformAgg.total)
                    .inc("days." + day + ".a", platformAgg.api)
                    .inc("days." + day + ".f", platformAgg.frontend);

            mongoTemplate.upsert(
                    Query.query(Criteria.where("year").is(year).and("month").is(month)),
                    update,
                    PlatformMonthlyStats.class
            );
        }

        long totalPlatformViews = 0;
        for (Map.Entry<String, Integer> entry : batch.views().entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            int count = entry.getValue();
            Update update = new Update()
                    .setOnInsert("authorId", parts.length > 1 ? parts[1] : null)
                    .inc("totalViews", count)
                    .inc("days." + day + ".v", count);

            mongoTemplate.upsert(
                    Query.query(Criteria.where("projectId").is(parts[0]).and("year").is(year).and("month").is(month)),
                    update,
                    ProjectMonthlyStats.class
            );
            totalPlatformViews += count;
        }

        if (totalPlatformViews > 0) {
            mongoTemplate.upsert(
                    Query.query(Criteria.where("year").is(year).and("month").is(month)),
                    new Update().inc("totalViews", totalPlatformViews).inc("days." + day + ".v", totalPlatformViews),
                    PlatformMonthlyStats.class
            );
        }
        return true;
    }

    private boolean flushPlatformEntityStats() {
        TrackingBufferService.PlatformEntityBatch batch = trackingBufferService.drainPlatformEntities();
        if (!batch.hasUpdates()) {
            return false;
        }

        LocalDate now = LocalDate.now();
        Query query = Query.query(Criteria.where("year").is(now.getYear()).and("month").is(now.getMonthValue()));
        Update update = new Update();

        if (batch.netProjects() != 0) {
            update.inc("newProjects", batch.netProjects()).inc("days." + now.getDayOfMonth() + ".n", batch.netProjects());
        }
        if (batch.netUsers() != 0) {
            update.inc("newUsers", batch.netUsers()).inc("days." + now.getDayOfMonth() + ".u", batch.netUsers());
        }
        if (batch.netOrgs() != 0) {
            update.inc("newOrgs", batch.netOrgs()).inc("days." + now.getDayOfMonth() + ".o", batch.netOrgs());
        }

        mongoTemplate.upsert(query, update, PlatformMonthlyStats.class);
        return true;
    }

    private void evictAnalyticsCaches() {
        if (cacheManager == null) {
            return;
        }
        clearCache("platformStats");
        clearCache("platformAnalytics");
        clearCache("creatorAnalytics");
        clearCache("projectAnalytics");
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private static class ProjectAgg {
        private final String authorId;
        private int total = 0;
        private int api = 0;
        private int frontend = 0;
        private final Map<String, Integer> versions = new HashMap<>();

        private ProjectAgg(String authorId) {
            this.authorId = authorId;
        }
    }

    private static class PlatformAgg {
        private int total = 0;
        private int api = 0;
        private int frontend = 0;
    }
}
