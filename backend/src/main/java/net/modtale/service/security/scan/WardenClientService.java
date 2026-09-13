package net.modtale.service.security.scan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.modtale.config.properties.AppWardenProperties;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class WardenClientService {

    private static final Logger logger = LoggerFactory.getLogger(WardenClientService.class);
    private final WebClient webClient;
    private final AppWardenProperties wardenProperties;

    public WardenClientService(
            AppWardenProperties wardenProperties) {
        this.wardenProperties = wardenProperties;
        this.webClient = WebClient.builder()
                .baseUrl(wardenProperties.url())
                .codecs(config -> config.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .defaultHeader("X-Warden-Api-Key", wardenProperties.apiKey())
                .build();
    }

    public record PolicyResponse(String policyVersion) {}

    public String currentPolicyVersion() {
        if (!wardenProperties.enabled()) return null;
        try {
            var response = webClient.get().uri("/api/v1/policy").retrieve()
                    .bodyToMono(PolicyResponse.class).timeout(Duration.ofSeconds(10)).block();
            String policy = response == null ? null : response.policyVersion();
            return policy != null && policy.matches("warden-3\\.0\\.0:[0-9a-f]{64}") ? policy : null;
        } catch (RuntimeException unavailable) {
            logger.warn("Current scanner policy is unavailable; automatic publication is deferred");
            return null;
        }
    }

    public ScanResult scanFile(byte[] fileBytes, String filename) {
        if (!wardenProperties.enabled()) {
            logger.error("Warden scanner is DISABLED. Falling back to manual-review degraded result for file: {}", filename);
            return buildDegradedResult(filename, new IllegalStateException("Warden scanner disabled"));
        }

        Exception lastError = null;
        int attempts = Math.max(1, wardenProperties.maxAttempts());
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
                        .timeout(Duration.ofSeconds(Math.max(15, wardenProperties.requestTimeoutSeconds())))
                        .block();

                if (response != null) {
                    var evidence = response.getSecurityEvidence();
                    String expectedDigest = java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(fileBytes));
                    response.setArtifactVerified(evidence != null && evidence.complete()
                            && expectedDigest.equals(evidence.artifactSha256()));
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

    public record InspectionResponse(String artifactSha256, List<String> paths, String content, String format, java.util.Map<String, String> entryHashes, String policyVersion) {}

    public InspectionResponse inspectFile(byte[] bytes, String filename, String path) {
        if (!wardenProperties.enabled()) throw new IllegalStateException("Artifact inspection is unavailable");
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        if (path != null) builder.part("path", path);
        return webClient.post().uri("/api/v1/inspect").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build())).retrieve()
                .bodyToMono(InspectionResponse.class).timeout(Duration.ofSeconds(90)).block();
    }

    public record InspectionWindow(String artifactSha256, String path, String entrySha256, String policyVersion,
            String representationSha256, String format, int start, int end, int totalCharacters, int firstLine,
            boolean lineMatched, boolean representationComplete, String content, List<String> gaps) {}
    public InspectionWindow inspectWindow(byte[] bytes, String path, int offset, int characters, int sourceLine) {
        if (!wardenProperties.enabled()) throw new IllegalStateException("Artifact inspection is unavailable");
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) { @Override public String getFilename() { return "artifact.zip"; } });
        builder.part("path", path); builder.part("offset", Integer.toString(offset));
        builder.part("characters", Integer.toString(characters)); builder.part("sourceLine", Integer.toString(sourceLine));
        return webClient.post().uri("/api/v1/inspect-window").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build())).retrieve()
                .bodyToMono(InspectionWindow.class).timeout(Duration.ofSeconds(90)).block();
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
        degraded.setScanState(wardenProperties.enabled() ? "UPSTREAM_UNAVAILABLE" : "UPSTREAM_DISABLED");
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
