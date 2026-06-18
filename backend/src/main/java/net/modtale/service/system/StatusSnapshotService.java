package net.modtale.service.system;

import com.mongodb.MongoException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import net.modtale.model.dto.response.system.ServiceStatusView;
import net.modtale.model.dto.response.system.StatusHistoryPointView;
import net.modtale.model.dto.response.system.SystemStatusView;
import net.modtale.model.system.StatusHistory;
import net.modtale.model.system.SystemStatus;
import net.modtale.repository.system.StatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

@Service
public class StatusSnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(StatusSnapshotService.class);

    private final MongoTemplate mongoTemplate;
    private final S3Client s3Client;
    private final StatusHistoryRepository historyRepository;
    private final StatusDiscordNotifierService statusDiscordNotifierService;
    private final StatusIncidentService statusIncidentService;

    private volatile SystemStatusView cached24HourStatus;
    private volatile SystemStatusView cached30DayStatus;

    public StatusSnapshotService(
            MongoTemplate mongoTemplate,
            S3Client s3Client,
            StatusHistoryRepository historyRepository,
            StatusDiscordNotifierService statusDiscordNotifierService,
            StatusIncidentService statusIncidentService
    ) {
        this.mongoTemplate = mongoTemplate;
        this.s3Client = s3Client;
        this.historyRepository = historyRepository;
        this.statusDiscordNotifierService = statusDiscordNotifierService;
        this.statusIncidentService = statusIncidentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refreshSnapshots();
    }

    @Scheduled(fixedRate = 60000)
    public synchronized void refreshSnapshots() {
        StatusHistory previous = findLatestHistorySafely();
        StatusHistory latest = performHealthCheck();
        rebuildSnapshots(latest);
        notifyDiscordIfStatusChanged(previous, latest);
    }

    public SystemStatusView getSystemStatus(String range) {
        SystemStatusView cached = cachedStatus(range);
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            cached = cachedStatus(range);
            if (cached != null) {
                return cached;
            }

            StatusHistory latest = findLatestHistorySafely();
            if (latest == null) {
                latest = performHealthCheck();
            }
            rebuildSnapshots(latest);
            return cachedStatus(range);
        }
    }

    private SystemStatusView cachedStatus(String range) {
        return "30d".equals(range) ? cached30DayStatus : cached24HourStatus;
    }

    private void rebuildSnapshots(StatusHistory latest) {
        cached24HourStatus = buildStatusView(latest, "24h");
        cached30DayStatus = buildStatusView(latest, "30d");
    }

    private SystemStatusView buildStatusView(StatusHistory latest, String range) {
        List<ServiceStatusView> services = List.of(
                new ServiceStatusView(
                        "api",
                        "API Gateway",
                        resolveServiceStatus(latest.getApiStatus(), latest.getApiLatency(), latest.getOverallStatus()),
                        latest.getApiLatency()
                ),
                new ServiceStatusView(
                        "database",
                        "Database (Atlas)",
                        resolveServiceStatus(latest.getDbStatus(), latest.getDbLatency(), latest.getOverallStatus()),
                        latest.getDbLatency()
                ),
                new ServiceStatusView(
                        "storage",
                        "Storage (R2)",
                        resolveServiceStatus(latest.getStorageStatus(), latest.getStorageLatency(), latest.getOverallStatus()),
                        latest.getStorageLatency()
                )
        );

        LocalDateTime since = "30d".equals(range)
                ? LocalDateTime.now().minusDays(30)
                : LocalDateTime.now().minusHours(24);

        List<StatusHistory> history = findHistorySafely(since);

        if ("30d".equals(range) && history.size() > 500) {
            int step = history.size() / 100;
            List<StatusHistory> downsampled = new ArrayList<>();
            for (int i = 0; i < history.size(); i += step) {
                downsampled.add(history.get(i));
            }
            history = downsampled;
        }

        List<StatusHistoryPointView> historyPoints = history.stream()
                .map(h -> new StatusHistoryPointView(
                        h.getTimestamp().toEpochSecond(ZoneOffset.UTC) * 1000,
                        h.getApiLatency(),
                        h.getDbLatency(),
                        h.getStorageLatency()
                ))
                .toList();

        return new SystemStatusView(
                latest.getOverallStatus(),
                services,
                latest.getTimestamp().toEpochSecond(ZoneOffset.UTC) * 1000,
                historyPoints,
                statusIncidentService.getActiveIncidents(),
                statusIncidentService.getScheduledMaintenances(),
                statusIncidentService.getIncidentHistory()
        );
    }

    private StatusHistory performHealthCheck() {
        long totalStart = System.currentTimeMillis();
        boolean allOperational = true;

        long dbStart = System.currentTimeMillis();
        SystemStatus dbStatus = SystemStatus.OPERATIONAL;
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
        } catch (MongoException e) {
            logger.error("Health Check: Database failed", e);
            allOperational = false;
            dbStatus = SystemStatus.OUTAGE;
        }
        int dbLatency = (int) (System.currentTimeMillis() - dbStart);

        long storageStart = System.currentTimeMillis();
        SystemStatus storageStatus = SystemStatus.OPERATIONAL;
        try {
            s3Client.listBuckets();
        } catch (SdkException e) {
            logger.error("Health Check: Storage failed", e);
            allOperational = false;
            storageStatus = SystemStatus.OUTAGE;
        }
        int storageLatency = (int) (System.currentTimeMillis() - storageStart);

        int apiLatency = (int) (System.currentTimeMillis() - totalStart);

        SystemStatus overall = allOperational ? SystemStatus.OPERATIONAL : SystemStatus.DEGRADED;
        SystemStatus apiStatus = allOperational ? SystemStatus.OPERATIONAL : SystemStatus.DEGRADED;

        StatusHistory entry = new StatusHistory(
                apiLatency,
                dbLatency,
                storageLatency,
                overall,
                apiStatus,
                dbStatus,
                storageStatus
        );

        try {
            return historyRepository.save(entry);
        } catch (RuntimeException e) {
            logger.error("Health Check: Failed to persist status history", e);
            return entry;
        }
    }

    private StatusHistory findLatestHistorySafely() {
        try {
            return historyRepository.findTopByOrderByTimestampDesc();
        } catch (RuntimeException e) {
            logger.warn("Health Check: Failed to read latest status history", e);
            return null;
        }
    }

    private List<StatusHistory> findHistorySafely(LocalDateTime since) {
        try {
            return historyRepository.findByTimestampAfterOrderByTimestampAsc(since);
        } catch (RuntimeException e) {
            logger.warn("Health Check: Failed to read status history", e);
            return List.of();
        }
    }

    private SystemStatus resolveServiceStatus(SystemStatus storedStatus, int latency, SystemStatus overallStatus) {
        if (storedStatus != null) {
            return storedStatus;
        }

        if (latency <= 0 || latency >= 5000) {
            return SystemStatus.OUTAGE;
        }

        if (overallStatus == SystemStatus.OPERATIONAL) {
            return SystemStatus.OPERATIONAL;
        }

        return SystemStatus.DEGRADED;
    }

    private void notifyDiscordIfStatusChanged(StatusHistory previous, StatusHistory latest) {
        SystemStatus previousStatus = previous != null ? previous.getOverallStatus() : null;
        SystemStatus latestStatus = latest.getOverallStatus();

        if (latestStatus == null) {
            return;
        }

        boolean firstSnapshotNeedsAttention = previousStatus == null && latestStatus != SystemStatus.OPERATIONAL;
        boolean changedStatus = previousStatus != null && previousStatus != latestStatus;

        if (firstSnapshotNeedsAttention || changedStatus) {
            statusDiscordNotifierService.notifyStatusChange(previous, latest);
        }
    }
}
