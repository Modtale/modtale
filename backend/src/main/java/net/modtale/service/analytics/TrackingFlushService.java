package net.modtale.service.analytics;

import net.modtale.model.analytics.PlatformMonthlyStats;
import net.modtale.model.analytics.ProjectMonthlyStats;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class TrackingFlushService {

    private static final Logger logger = LoggerFactory.getLogger(TrackingFlushService.class);

    private final MongoTemplate mongoTemplate;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final TrackingBufferService trackingBufferService;

    public TrackingFlushService(
            MongoTemplate mongoTemplate,
            ProjectRepository projectRepository,
            ProjectService projectService,
            TrackingBufferService trackingBufferService
    ) {
        this.mongoTemplate = mongoTemplate;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.trackingBufferService = trackingBufferService;
    }

    public void flushAnalyticsBuffer() {
        flushBaseProjectMetrics();
        flushMonthlyStats();
        flushPlatformEntityStats();
    }

    public void deleteProjectAnalytics(String projectId) {
        mongoTemplate.remove(Query.query(Criteria.where("projectId").is(projectId)), ProjectMonthlyStats.class);
    }

    private void flushBaseProjectMetrics() {
        TrackingBufferService.MetricsBatch batch = trackingBufferService.drainMetricIncrements();
        if (batch.isEmpty()) {
            return;
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
            for (String id : allIdsToEvict) {
                projectService.evictProjectCache(projectRepository.findById(id).orElse(null));
            }
        } catch (Exception e) {
            logger.error("Failed to bulk flush metrics", e);
            trackingBufferService.restoreMetricIncrements(batch);
        }
    }

    private void flushMonthlyStats() {
        TrackingBufferService.MonthlyAnalyticsBatch batch = trackingBufferService.drainMonthlyAnalytics();
        if (batch.isEmpty()) {
            return;
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
    }

    private void flushPlatformEntityStats() {
        TrackingBufferService.PlatformEntityBatch batch = trackingBufferService.drainPlatformEntities();
        if (!batch.hasUpdates()) {
            return;
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
