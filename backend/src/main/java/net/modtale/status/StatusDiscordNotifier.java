package net.modtale.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.modtale.status.StatusModels.ServiceStatusView;
import net.modtale.status.StatusModels.StatusHistoryPointView;
import net.modtale.status.StatusModels.SystemStatus;
import net.modtale.status.StatusModels.SystemStatusView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StatusDiscordNotifier {

    private static final Logger logger = LoggerFactory.getLogger(StatusDiscordNotifier.class);
    private static final int HISTORY_BUCKETS = 10;
    private static final long HISTORY_WINDOW_MILLIS = Duration.ofHours(24).toMillis();
    private static final String OPERATIONAL_BLOCK = "\uD83D\uDFE9";
    private static final String DEGRADED_BLOCK = "\uD83D\uDFE8";
    private static final String OUTAGE_BLOCK = "\uD83D\uDFE5";
    private static final String NO_DATA_BLOCK = "\u00B7";

    private final StatusServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final MongoStatusStore mongoStatusStore;
    private final HttpClient httpClient;
    private String messageId;
    private boolean messageIdLoaded;
    private boolean editConfirmedLogged;

    @Autowired
    public StatusDiscordNotifier(
            StatusServiceProperties properties,
            ObjectMapper objectMapper,
            MongoStatusStore mongoStatusStore
    ) {
        this(properties, objectMapper, mongoStatusStore, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    StatusDiscordNotifier(
            StatusServiceProperties properties,
            ObjectMapper objectMapper,
            MongoStatusStore mongoStatusStore,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mongoStatusStore = mongoStatusStore;
        this.httpClient = httpClient;
    }

    public synchronized void publishStatus(SystemStatusView status) {
        if (!hasText(properties.getDiscordWebhookUrl()) || status == null) {
            return;
        }

        try {
            String currentMessageId = currentMessageId();
            if (hasText(currentMessageId) && editMessage(currentMessageId, status)) {
                return;
            }
            if (hasText(messageId)) {
                return;
            }
            createMessage(status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Detached status Discord mirror update was interrupted");
        } catch (Exception e) {
            logger.warn("Detached status Discord mirror update failed: {}", e.toString());
        }
    }

    private String currentMessageId() {
        if (!messageIdLoaded) {
            messageId = mongoStatusStore.findDiscordStatusMessageId().orElse(null);
            messageIdLoaded = true;
        }
        return messageId;
    }

    private boolean editMessage(String currentMessageId, SystemStatusView status) throws Exception {
        HttpRequest request = jsonRequest(messageUri(currentMessageId), "PATCH", editPayload(status));
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            if (!editConfirmedLogged) {
                logger.info("Persistent Discord status mirror is updating successfully");
                editConfirmedLogged = true;
            }
            return true;
        }
        if (response.statusCode() == 401 || response.statusCode() == 404) {
            logger.info("Discord status mirror message no longer exists; creating a replacement");
            messageId = null;
            return false;
        }
        logger.warn("Discord status mirror edit returned HTTP {}", response.statusCode());
        return true;
    }

    private void createMessage(SystemStatusView status) throws Exception {
        HttpRequest request = jsonRequest(executeUri(), "POST", createPayload(status));
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.warn("Discord status mirror creation returned HTTP {}", response.statusCode());
            return;
        }

        JsonNode responseBody = objectMapper.readTree(response.body());
        String createdMessageId = responseBody.path("id").asText("");
        if (!hasText(createdMessageId)) {
            logger.warn("Discord status mirror creation did not return a message ID");
            return;
        }
        messageId = createdMessageId;
        mongoStatusStore.saveDiscordStatusMessageId(createdMessageId);
        logger.info("Created persistent Discord status mirror message");
    }

    private HttpRequest jsonRequest(URI uri, String method, Map<String, Object> payload) throws Exception {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
    }

    private URI executeUri() throws URISyntaxException {
        URI webhook = URI.create(properties.getDiscordWebhookUrl().trim());
        String query = hasText(webhook.getRawQuery()) ? webhook.getRawQuery() + "&wait=true" : "wait=true";
        return rebuildUri(webhook, webhook.getPath(), query);
    }

    private URI messageUri(String currentMessageId) throws URISyntaxException {
        URI webhook = URI.create(properties.getDiscordWebhookUrl().trim());
        String path = webhook.getPath().replaceAll("/+$", "") + "/messages/" + currentMessageId;
        return rebuildUri(webhook, path, webhook.getRawQuery());
    }

    private URI rebuildUri(URI source, String path, String query) throws URISyntaxException {
        return new URI(
                source.getScheme(),
                source.getUserInfo(),
                source.getHost(),
                source.getPort(),
                path,
                query,
                null
        );
    }

    private Map<String, Object> createPayload(SystemStatusView status) {
        Map<String, Object> payload = editPayload(status);
        payload.put("username", "Modtale Status");
        payload.put("avatar_url", "https://modtale.net/assets/favicon.png");
        return payload;
    }

    private Map<String, Object> editPayload(SystemStatusView status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", null);
        payload.put("embeds", List.of(embed(status)));
        payload.put("allowed_mentions", Map.of("parse", List.of()));
        return payload;
    }

    private Map<String, Object> embed(SystemStatusView status) {
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "Modtale Status");
        embed.put("url", cleanStatusUrl());
        embed.put("color", colorFor(status.overall()));
        embed.put("description", description(status));
        embed.put("timestamp", Instant.ofEpochMilli(status.timestamp()).toString());
        embed.put("footer", Map.of("text", "24-hour availability · Updated every minute"));
        return embed;
    }

    private String description(SystemStatusView status) {
        StringBuilder description = new StringBuilder();
        for (ServiceStatusView service : status.services()) {
            if (!description.isEmpty()) {
                description.append("\n\n");
            }
            description.append("**")
                    .append(service.name())
                    .append("**\n")
                    .append(uptimeBar(status, service.id()))
                    .append(" **")
                    .append(statusLabel(service.status()))
                    .append("** · ")
                    .append(service.latency())
                    .append(" ms");
        }
        if (!status.activeIncidents().isEmpty()) {
            description.append("\n\n**")
                    .append(status.activeIncidents().size())
                    .append(status.activeIncidents().size() == 1 ? " active incident" : " active incidents")
                    .append("** · [View details](")
                    .append(cleanStatusUrl())
                    .append(')');
        }
        return description.toString();
    }

    private String uptimeBar(SystemStatusView status, String serviceId) {
        long windowEnd = status.timestamp();
        long windowStart = windowEnd - HISTORY_WINDOW_MILLIS;
        long bucketMillis = HISTORY_WINDOW_MILLIS / HISTORY_BUCKETS;
        StringBuilder bar = new StringBuilder();

        for (int index = 0; index < HISTORY_BUCKETS; index++) {
            long bucketStart = windowStart + bucketMillis * index;
            long bucketEnd = index == HISTORY_BUCKETS - 1 ? windowEnd + 1 : bucketStart + bucketMillis;
            List<SystemStatus> samples = new ArrayList<>();
            for (StatusHistoryPointView point : status.history()) {
                if (point.time() >= bucketStart && point.time() < bucketEnd) {
                    samples.add(serviceStatus(point, serviceId));
                }
            }
            bar.append(statusBlock(worstStatus(samples)));
        }
        return bar.toString();
    }

    private SystemStatus serviceStatus(StatusHistoryPointView point, String serviceId) {
        return switch (serviceId) {
            case "site" -> point.siteStatus();
            case "api" -> point.apiStatus();
            case "database" -> point.dbStatus();
            case "storage" -> point.storageStatus();
            default -> SystemStatus.DEGRADED;
        };
    }

    private SystemStatus worstStatus(List<SystemStatus> statuses) {
        if (statuses.stream().anyMatch(status -> status == SystemStatus.OUTAGE)) {
            return SystemStatus.OUTAGE;
        }
        if (statuses.stream().anyMatch(status -> status == SystemStatus.DEGRADED)) {
            return SystemStatus.DEGRADED;
        }
        if (statuses.stream().anyMatch(status -> status == SystemStatus.OPERATIONAL)) {
            return SystemStatus.OPERATIONAL;
        }
        return null;
    }

    private String statusBlock(SystemStatus status) {
        if (status == null) {
            return NO_DATA_BLOCK;
        }
        return switch (status) {
            case OPERATIONAL -> OPERATIONAL_BLOCK;
            case DEGRADED -> DEGRADED_BLOCK;
            case OUTAGE -> OUTAGE_BLOCK;
        };
    }

    private String statusLabel(SystemStatus status) {
        return switch (status) {
            case OPERATIONAL -> "Operational";
            case DEGRADED -> "Degraded";
            case OUTAGE -> "Outage";
        };
    }

    private int colorFor(SystemStatus status) {
        return switch (status) {
            case OPERATIONAL -> 0x34D399;
            case DEGRADED -> 0xF59E0B;
            case OUTAGE -> 0xEF4444;
        };
    }

    private String cleanStatusUrl() {
        String statusUrl = properties.getPublicStatusUrl();
        if (!hasText(statusUrl)) {
            return "https://status.modtale.net";
        }
        return statusUrl.endsWith("/") ? statusUrl.substring(0, statusUrl.length() - 1) : statusUrl;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
