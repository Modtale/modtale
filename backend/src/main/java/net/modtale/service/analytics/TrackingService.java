package net.modtale.service.analytics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.modtale.model.analytics.PlatformMonthlyStats;
import net.modtale.model.analytics.ProjectMonthlyStats;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TrackingService {

    private static final Logger logger = LoggerFactory.getLogger(TrackingService.class);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectProjectService;

    private final ConcurrentLinkedQueue<DownloadEvent> downloadBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, AtomicInteger> viewBuffer = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<String> newProjectBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> deletedProjectBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> newUserBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> deletedUserBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> newOrgBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> deletedOrgBuffer = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap<String, Integer> pendingDownloadIncrements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> pendingVersionDownloadIncrements = new ConcurrentHashMap<>();

    private final Cache<String, Boolean> debounceCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(50000).build();

    private record DownloadEvent(String projectId, String versionId, String authorId, boolean isApi, String clientIp) {}

    public void logDownload(String projectId, String versionId, String authorId, boolean isApi, String clientIp) {
        if (!isDebounced(projectId, clientIp, "download")) {
            downloadBuffer.add(new DownloadEvent(projectId, versionId, authorId, isApi, clientIp));
            pendingDownloadIncrements.merge(projectId, 1, Integer::sum);
            if (versionId != null) pendingVersionDownloadIncrements.merge(projectId + "|||" + versionId, 1, Integer::sum);
        }
    }

    public void logView(String projectId, String authorId, String clientIp) {
        if (!isDebounced(projectId, clientIp, "view")) {
            viewBuffer.computeIfAbsent(projectId + "|" + authorId, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    public void logNewProject(String id) { newProjectBuffer.add(id); }
    public void logDeletedProject(String id) { deletedProjectBuffer.add(id); }
    public void logNewUser(String id) { newUserBuffer.add(id); }
    public void logDeletedUser(String id) { deletedUserBuffer.add(id); }
    public void logNewOrg(String id) { newOrgBuffer.add(id); }
    public void logDeletedOrg(String id) { deletedOrgBuffer.add(id); }

    public void deleteProjectAnalytics(String projectId) {
        mongoTemplate.remove(Query.query(Criteria.where("projectId").is(projectId)), ProjectMonthlyStats.class);
    }

    private boolean isDebounced(String entityId, String clientIp, String type) {
        if (clientIp == null || clientIp.isEmpty()) return false;
        String key = type + ":" + entityId + ":" + clientIp;
        if (debounceCache.getIfPresent(key) != null) return true;
        debounceCache.put(key, Boolean.TRUE);
        return false;
    }

    @Scheduled(fixedRate = 10000)
    public void flushAnalyticsBuffer() {
        flushBaseProjectMetrics();
        flushMonthlyStats();
        flushPlatformEntityStats();
    }

    private void flushBaseProjectMetrics() {
        if (pendingDownloadIncrements.isEmpty() && pendingVersionDownloadIncrements.isEmpty()) return;

        Map<String, Integer> dlsToFlush = new HashMap<>(pendingDownloadIncrements); pendingDownloadIncrements.clear();
        Map<String, Integer> vDlsToFlush = new HashMap<>(pendingVersionDownloadIncrements); pendingVersionDownloadIncrements.clear();

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
        Set<String> allIdsToEvict = new HashSet<>(dlsToFlush.keySet());

        for (Map.Entry<String, Integer> entry : dlsToFlush.entrySet()) {
            bulkOps.updateOne(new Query(Criteria.where("_id").is(entry.getKey())), new Update().inc("downloadCount", entry.getValue()));
        }

        for (Map.Entry<String, Integer> entry : vDlsToFlush.entrySet()) {
            String[] parts = entry.getKey().split("\\|\\|\\|");
            bulkOps.updateOne(new Query(Criteria.where("_id").is(parts[0]).and("versions._id").is(parts[1])), new Update().inc("versions.$.downloadCount", entry.getValue()));
            allIdsToEvict.add(parts[0]);
        }

        try {
            bulkOps.execute();
            for (String id : allIdsToEvict) {
                projectProjectService.evictProjectCache(projectRepository.findById(id).orElse(null));
            }
        } catch (Exception e) {
            logger.error("Failed to bulk flush metrics", e);
            for (Map.Entry<String, Integer> entry : dlsToFlush.entrySet()) pendingDownloadIncrements.merge(entry.getKey(), entry.getValue(), Integer::sum);
            for (Map.Entry<String, Integer> entry : vDlsToFlush.entrySet()) pendingVersionDownloadIncrements.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private void flushMonthlyStats() {
        if (downloadBuffer.isEmpty() && viewBuffer.isEmpty()) return;

        LocalDate now = LocalDate.now();
        int day = now.getDayOfMonth();
        int month = now.getMonthValue();
        int year = now.getYear();

        Map<String, ProjectAgg> projectAggs = new HashMap<>();
        PlatformAgg platformAgg = new PlatformAgg();

        DownloadEvent event;
        while ((event = downloadBuffer.poll()) != null) {
            final DownloadEvent finalEvent = event;
            ProjectAgg pAg = projectAggs.computeIfAbsent(finalEvent.projectId(), k -> new ProjectAgg(finalEvent.authorId()));
            pAg.total++;
            platformAgg.total++;

            if (finalEvent.isApi()) {
                pAg.api++;
                platformAgg.api++;
            } else {
                pAg.frontend++;
                platformAgg.frontend++;
            }

            if (finalEvent.versionId() != null) {
                pAg.versions.merge(finalEvent.versionId(), 1, Integer::sum);
            }
        }

        for (Map.Entry<String, ProjectAgg> entry : projectAggs.entrySet()) {
            String pid = entry.getKey();
            ProjectAgg agg = entry.getValue();

            Update u = new Update()
                    .setOnInsert("authorId", agg.authorId)
                    .inc("totalDownloads", agg.total)
                    .inc("apiDownloads", agg.api)
                    .inc("frontendDownloads", agg.frontend)
                    .inc("days." + day + ".d", agg.total)
                    .inc("days." + day + ".a", agg.api)
                    .inc("days." + day + ".f", agg.frontend);

            for (Map.Entry<String, Integer> versionEntry : agg.versions.entrySet()) {
                u.inc("versionDownloads." + versionEntry.getKey().replace(".", "_") + "." + day, versionEntry.getValue());
            }

            mongoTemplate.upsert(Query.query(Criteria.where("projectId").is(pid).and("year").is(year).and("month").is(month)), u, ProjectMonthlyStats.class);
        }

        if (platformAgg.total > 0) {
            Update pu = new Update()
                    .inc("totalDownloads", platformAgg.total)
                    .inc("apiDownloads", platformAgg.api)
                    .inc("frontendDownloads", platformAgg.frontend)
                    .inc("days." + day + ".d", platformAgg.total)
                    .inc("days." + day + ".a", platformAgg.api)
                    .inc("days." + day + ".f", platformAgg.frontend);

            mongoTemplate.upsert(Query.query(Criteria.where("year").is(year).and("month").is(month)), pu, PlatformMonthlyStats.class);
        }

        Map<String, Integer> currentViewBatch = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : viewBuffer.entrySet()) {
            int count = entry.getValue().getAndSet(0);
            if (count > 0) {
                currentViewBatch.put(entry.getKey(), count);
            }
        }
        viewBuffer.entrySet().removeIf(e -> e.getValue().get() == 0);

        long totalPlatformViews = 0;
        for (Map.Entry<String, Integer> entry : currentViewBatch.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            int count = entry.getValue();
            Update u = new Update()
                    .setOnInsert("authorId", parts.length > 1 ? parts[1] : null)
                    .inc("totalViews", count)
                    .inc("days." + day + ".v", count);

            mongoTemplate.upsert(Query.query(Criteria.where("projectId").is(parts[0]).and("year").is(year).and("month").is(month)), u, ProjectMonthlyStats.class);
            totalPlatformViews += count;
        }

        if (totalPlatformViews > 0) {
            mongoTemplate.upsert(Query.query(Criteria.where("year").is(year).and("month").is(month)), new Update().inc("totalViews", totalPlatformViews).inc("days." + day + ".v", totalPlatformViews), PlatformMonthlyStats.class);
        }
    }

    private void flushPlatformEntityStats() {
        int netProjects = drainBuffer(newProjectBuffer) - drainBuffer(deletedProjectBuffer);
        int netUsers = drainBuffer(newUserBuffer) - drainBuffer(deletedUserBuffer);
        int netOrgs = drainBuffer(newOrgBuffer) - drainBuffer(deletedOrgBuffer);

        LocalDate now = LocalDate.now();
        Query pq = Query.query(Criteria.where("year").is(now.getYear()).and("month").is(now.getMonthValue()));
        Update pu = new Update();
        boolean hasUpdates = false;

        if (netProjects != 0) { pu.inc("newProjects", netProjects).inc("days." + now.getDayOfMonth() + ".n", netProjects); hasUpdates = true; }
        if (netUsers != 0) { pu.inc("newUsers", netUsers).inc("days." + now.getDayOfMonth() + ".u", netUsers); hasUpdates = true; }
        if (netOrgs != 0) { pu.inc("newOrgs", netOrgs).inc("days." + now.getDayOfMonth() + ".o", netOrgs); hasUpdates = true; }

        if (hasUpdates) mongoTemplate.upsert(pq, pu, PlatformMonthlyStats.class);
    }

    private int drainBuffer(ConcurrentLinkedQueue<String> queue) {
        int count = 0;
        while(queue.poll() != null) count++;
        return count;
    }

    private static class ProjectAgg {
        String authorId;
        int total = 0, api = 0, frontend = 0;
        Map<String, Integer> versions = new HashMap<>();

        ProjectAgg(String authorId) {
            this.authorId = authorId;
        }
    }

    private static class PlatformAgg {
        int total = 0, api = 0, frontend = 0;
    }
}