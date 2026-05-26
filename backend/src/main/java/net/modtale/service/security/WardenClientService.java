package net.modtale.service.security;

import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class WardenClientService {

    private static final Logger logger = LoggerFactory.getLogger(WardenClientService.class);
    private final WebClient webClient;

    @Value("${app.warden.enabled:true}")
    private boolean wardenEnabled;

    @Value("${app.warden.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.warden.request-timeout-seconds:75}")
    private long requestTimeoutSeconds;

    public WardenClientService(
            @Value("${app.warden.url}") String wardenUrl,
            @Value("${app.warden.api-key}") String wardenApiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(wardenUrl)
                .defaultHeader("X-Warden-Api-Key", wardenApiKey)
                .build();
    }

    public ScanResult scanFile(byte[] fileBytes, String filename) {
        if (!wardenEnabled) {
            logger.error("Warden scanner is DISABLED. Falling back to manual-review degraded result for file: {}", filename);
            return buildDegradedResult(filename, new IllegalStateException("Warden scanner disabled"));
        }

        Exception lastError = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("file", new ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                });

                ScanResult response = webClient.post()
                        .uri("/api/v1/scan")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(builder.build()))
                        .retrieve()
                        .bodyToMono(ScanResult.class)
                        .timeout(Duration.ofSeconds(Math.max(15, requestTimeoutSeconds)))
                        .block();

                if (response != null) {
                    return response;
                }
                throw new IllegalStateException("Warden returned empty response body");
            } catch (Exception e) {
                lastError = e;
                logger.warn("Warden scan attempt {} failed for {}: {}", attempt, filename, e.getMessage());
                if (attempt < attempts) {
                    backoff(attempt);
                }
            }
        }

        logger.error("Warden unavailable after retries for {}: {}", filename, lastError == null ? "unknown" : lastError.getMessage());
        return buildDegradedResult(filename, lastError);
    }

    private void backoff(int attempt) {
        long delayMs = Math.min(3000L, 400L * attempt);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private ScanResult buildDegradedResult(String filename, Exception exception) {
        ScanResult degraded = new ScanResult();
        degraded.setStatus(ScanStatus.SUSPICIOUS);
        degraded.setVerdict("REVIEW");
        degraded.setRiskLevel("HIGH");
        degraded.setScanState(wardenEnabled ? "UPSTREAM_UNAVAILABLE" : "UPSTREAM_DISABLED");
        degraded.setRiskScore(45);
        degraded.setConfidenceScore(25);
        degraded.setScanTimestamp(System.currentTimeMillis());
        degraded.setIssues(new ArrayList<>());
        degraded.setReviewerNotes(List.of(
                "Warden service was unavailable during this scan.",
                "Manual review required before publishing."
        ));

        if (exception != null) {
            ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
            issue.setSeverity("MEDIUM");
            issue.setType("WardenUnavailable");
            issue.setCategory("System");
            issue.setDescription("Scanner communication failed: " + exception.getMessage());
            issue.setFilePath(filename == null ? "uploaded-artifact" : filename);
            issue.setLineStart(-1);
            issue.setLineEnd(-1);
            issue.setScoreImpact(4);
            issue.setConfidence(35);
            issue.setReviewPriority("P1");
            degraded.setIssues(List.of(issue));
        }
        return degraded;
    }
}
