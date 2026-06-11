package net.modtale.controller.system;

import com.mongodb.MongoException;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/status")
public class StatusController {

    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    private final MongoTemplate mongoTemplate;
    private final S3Client s3Client;
    private final StatusHistoryRepository historyRepository;

    public StatusController(MongoTemplate mongoTemplate, S3Client s3Client, StatusHistoryRepository historyRepository) {
        this.mongoTemplate = mongoTemplate;
        this.s3Client = s3Client;
        this.historyRepository = historyRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        performHealthCheck();
    }

    @Scheduled(fixedRate = 60000)
    public void performScheduledCheck() {
        performHealthCheck();
    }

    @GetMapping
    public ResponseEntity<SystemStatusView> getSystemStatus(
            @RequestParam(defaultValue = "24h")
            @Pattern(regexp = "24h|30d", message = "Status ranges must be 24h or 30d.")
            String range
    ) {
        StatusHistory latest = historyRepository.findTopByOrderByTimestampDesc();

        if (latest == null) {
            latest = performHealthCheck();
        }

        List<ServiceStatusView> services = List.of(
                new ServiceStatusView("api", "API Gateway", latest.getOverallStatus(), latest.getApiLatency()),
                new ServiceStatusView("database", "Database (Atlas)", "operational", latest.getDbLatency()),
                new ServiceStatusView("storage", "Storage (R2)", "operational", latest.getStorageLatency())
        );

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

        List<StatusHistoryPointView> historyPoints = history.stream()
                .map(h -> new StatusHistoryPointView(
                        h.getTimestamp().toEpochSecond(ZoneOffset.UTC) * 1000,
                        h.getApiLatency(),
                        h.getDbLatency(),
                        h.getStorageLatency()
                ))
                .toList();

        return ResponseEntity.ok(new SystemStatusView(
                latest.getOverallStatus(),
                services,
                latest.getTimestamp().toEpochSecond(ZoneOffset.UTC) * 1000,
                historyPoints
        ));
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
