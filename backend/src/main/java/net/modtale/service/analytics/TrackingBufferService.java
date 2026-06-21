package net.modtale.service.analytics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class TrackingBufferService {

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

    private final Cache<String, Boolean> debounceCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(50000)
            .build();

    public void logDownload(String projectId, String versionId, String authorId, boolean isApi, String clientIp) {
        if (isDebounced(projectId, clientIp, "download")) {
            return;
        }

        downloadBuffer.add(new DownloadEvent(projectId, versionId, authorId, isApi, clientIp));
        pendingDownloadIncrements.merge(projectId, 1, Integer::sum);
        if (versionId != null) {
            pendingVersionDownloadIncrements.merge(projectId + "|||" + versionId, 1, Integer::sum);
        }
    }

    public void logView(String projectId, String authorId, String clientIp) {
        if (isDebounced(projectId, clientIp, "view")) {
            return;
        }
        viewBuffer.computeIfAbsent(projectId + "|" + authorId, ignored -> new AtomicInteger(0)).incrementAndGet();
    }

    public void logNewProject(String id) {
        newProjectBuffer.add(id);
    }

    public void logDeletedProject(String id) {
        deletedProjectBuffer.add(id);
    }

    public void logNewUser(String id) {
        newUserBuffer.add(id);
    }

    public void logDeletedUser(String id) {
        deletedUserBuffer.add(id);
    }

    public void logNewOrg(String id) {
        newOrgBuffer.add(id);
    }

    public void logDeletedOrg(String id) {
        deletedOrgBuffer.add(id);
    }

    public MetricsBatch drainMetricIncrements() {
        if (pendingDownloadIncrements.isEmpty() && pendingVersionDownloadIncrements.isEmpty()) {
            return MetricsBatch.empty();
        }

        Map<String, Integer> downloads = new HashMap<>(pendingDownloadIncrements);
        Map<String, Integer> versionDownloads = new HashMap<>(pendingVersionDownloadIncrements);
        pendingDownloadIncrements.clear();
        pendingVersionDownloadIncrements.clear();
        return new MetricsBatch(downloads, versionDownloads);
    }

    public void restoreMetricIncrements(MetricsBatch batch) {
        for (Map.Entry<String, Integer> entry : batch.downloads().entrySet()) {
            pendingDownloadIncrements.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : batch.versionDownloads().entrySet()) {
            pendingVersionDownloadIncrements.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    public MonthlyAnalyticsBatch drainMonthlyAnalytics() {
        if (downloadBuffer.isEmpty() && viewBuffer.isEmpty()) {
            return MonthlyAnalyticsBatch.empty();
        }

        List<DownloadEvent> downloads = new ArrayList<>();
        DownloadEvent event;
        while ((event = downloadBuffer.poll()) != null) {
            downloads.add(event);
        }

        Map<String, Integer> currentViewBatch = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : viewBuffer.entrySet()) {
            int count = entry.getValue().getAndSet(0);
            if (count > 0) {
                currentViewBatch.put(entry.getKey(), count);
            }
        }
        viewBuffer.entrySet().removeIf(entry -> entry.getValue().get() == 0);

        return new MonthlyAnalyticsBatch(downloads, currentViewBatch);
    }

    public PlatformEntityBatch drainPlatformEntities() {
        int netProjects = drainBuffer(newProjectBuffer) - drainBuffer(deletedProjectBuffer);
        int netUsers = drainBuffer(newUserBuffer) - drainBuffer(deletedUserBuffer);
        int netOrgs = drainBuffer(newOrgBuffer) - drainBuffer(deletedOrgBuffer);
        return new PlatformEntityBatch(netProjects, netUsers, netOrgs);
    }

    private boolean isDebounced(String entityId, String clientIp, String type) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        String key = type + ":" + entityId + ":" + clientIp;
        if (debounceCache.getIfPresent(key) != null) {
            return true;
        }
        debounceCache.put(key, Boolean.TRUE);
        return false;
    }

    private int drainBuffer(ConcurrentLinkedQueue<String> queue) {
        int count = 0;
        while (queue.poll() != null) {
            count++;
        }
        return count;
    }

    record DownloadEvent(String projectId, String versionId, String authorId, boolean isApi, String clientIp) {
    }

    record MetricsBatch(Map<String, Integer> downloads, Map<String, Integer> versionDownloads) {
        static MetricsBatch empty() {
            return new MetricsBatch(Map.of(), Map.of());
        }

        boolean isEmpty() {
            return downloads.isEmpty() && versionDownloads.isEmpty();
        }
    }

    record MonthlyAnalyticsBatch(List<DownloadEvent> downloads, Map<String, Integer> views) {
        static MonthlyAnalyticsBatch empty() {
            return new MonthlyAnalyticsBatch(List.of(), Map.of());
        }

        boolean isEmpty() {
            return downloads.isEmpty() && views.isEmpty();
        }
    }

    record PlatformEntityBatch(int netProjects, int netUsers, int netOrgs) {
        boolean hasUpdates() {
            return netProjects != 0 || netUsers != 0 || netOrgs != 0;
        }
    }
}
