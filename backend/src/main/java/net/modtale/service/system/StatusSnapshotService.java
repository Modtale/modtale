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

    private volatile SystemStatusView cached24HourStatus;
    private volatile SystemStatusView cached30DayStatus;

    public StatusSnapshotService(MongoTemplate mongoTemplate, S3Client s3Client, StatusHistoryRepository historyRepository) {
        this.mongoTemplate = mongoTemplate;
        this.s3Client = s3Client;
        this.historyRepository = historyRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refreshSnapshots();
    }

    @Scheduled(fixedRate = 60000)
    public synchronized void refreshSnapshots() {
        StatusHistory latest = performHealthCheck();
        rebuildSnapshots(latest);
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

            StatusHistory latest = historyRepository.findTopByOrderByTimestampDesc();
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
                new ServiceStatusView("api", "API Gateway", latest.getOverallStatus(), latest.getApiLatency()),
                new ServiceStatusView("database", "Database (Atlas)", "operational", latest.getDbLatency()),
                new ServiceStatusView("storage", "Storage (R2)", "operational", latest.getStorageLatency())
        );

        LocalDateTime since = "30d".equals(range)
                ? LocalDateTime.now().minusDays(30)
                : LocalDateTime.now().minusHours(24);

        List<StatusHistory> history = historyRepository.findByTimestampAfterOrderByTimestampAsc(since);

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
                historyPoints
        );
    }

    private StatusHistory performHealthCheck() {
        long totalStart = System.currentTimeMillis();
        boolean allOperational = true;

        long dbStart = System.currentTimeMillis();
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
        } catch (MongoException e) {
            logger.error("Health Check: Database failed", e);
            allOperational = false;
        }
        int dbLatency = (int) (System.currentTimeMillis() - dbStart);

        long storageStart = System.currentTimeMillis();
        try {
            s3Client.listBuckets();
        } catch (SdkException e) {
            logger.error("Health Check: Storage failed", e);
            allOperational = false;
        }
        int storageLatency = (int) (System.currentTimeMillis() - storageStart);

        int apiLatency = (int) (System.currentTimeMillis() - totalStart);

        String overall = allOperational ? "operational" : "degraded";

        StatusHistory entry = new StatusHistory(apiLatency, dbLatency, storageLatency, overall);
        return historyRepository.save(entry);
    }
}
