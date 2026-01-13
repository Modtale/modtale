package net.modtale.controller;

import net.modtale.model.analytics.StatusHistory;
import net.modtale.repository.analytics.StatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private S3Client s3Client;
    @Autowired private StatusHistoryRepository historyRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        performHealthCheck();
    }

    @Scheduled(fixedRate = 60000)
    public void performScheduledCheck() {
        performHealthCheck();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSystemStatus(@RequestParam(defaultValue = "24h") String range) {
        StatusHistory latest = historyRepository.findTopByOrderByTimestampDesc();

        if (latest == null) {
            latest = performHealthCheck();
        }

        List<Map<String, Object>> services = new ArrayList<>();
        services.add(Map.of("id", "api", "name", "API Gateway", "status", latest.getOverallStatus(), "latency", latest.getApiLatency()));
        services.add(Map.of("id", "database", "name", "Database (Atlas)", "status", "operational", "latency", latest.getDbLatency()));
        services.add(Map.of("id", "storage", "name", "Storage (R2)", "status", "operational", "latency", latest.getStorageLatency()));

        LocalDateTime since;
        if ("30d".equals(range)) {
            since = LocalDateTime.now().minusDays(30);
        } else {
            since = LocalDateTime.now().minusHours(24);
        }

        List<StatusHistory> history = historyRepository.findByTimestampAfterOrderByTimestampAsc(since);

        if ("30d".equals(range) && history.size() > 500) {
            int step = history.size() / 100;
            List<StatusHistory> downsampled = new ArrayList<>();
            for (int i = 0; i < history.size(); i += step) {
                downsampled.add(history.get(i));
            }
            history = downsampled;
        }

        return ResponseEntity.ok(Map.of(
                "overall", latest.getOverallStatus(),
                "services", services,
                "timestamp", latest.getTimestamp().toEpochSecond(ZoneOffset.UTC) * 1000,
                "history", history.stream().map(h -> Map.of(
                        "time", h.getTimestamp().toEpochSecond(ZoneOffset.UTC) * 1000,
                        "api", h.getApiLatency(),
                        "db", h.getDbLatency(),
                        "storage", h.getStorageLatency()
                )).collect(Collectors.toList())
        ));
    }

    private StatusHistory performHealthCheck() {
        long totalStart = System.currentTimeMillis();
        boolean allOperational = true;

        long dbStart = System.currentTimeMillis();
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
        } catch (Exception e) {
            logger.error("Health Check: Database failed", e);
            allOperational = false;
        }
        int dbLatency = (int) (System.currentTimeMillis() - dbStart);

        long storageStart = System.currentTimeMillis();
        try {
            s3Client.listBuckets();
        } catch (Exception e) {
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