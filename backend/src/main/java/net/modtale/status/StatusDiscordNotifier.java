package net.modtale.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import net.modtale.status.StatusModels.ServiceStatusView;
import net.modtale.status.StatusModels.StatusHistoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StatusDiscordNotifier {

    private static final Logger logger = LoggerFactory.getLogger(StatusDiscordNotifier.class);

    private final StatusServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public StatusDiscordNotifier(StatusServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void notifyStatusChange(StatusHistoryEntry previous, StatusHistoryEntry latest) {
        if (!hasText(properties.getDiscordWebhookUrl()) || latest == null) {
            return;
        }

        if (previous != null && previous.overallStatus() == latest.overallStatus()) {
            return;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("content", buildMessage(previous, latest));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getDiscordWebhookUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> {
                        logger.warn("Detached status Discord notification failed", error);
                        return null;
                    });
        } catch (Exception e) {
            logger.warn("Could not enqueue detached status Discord notification", e);
        }
    }

    private String buildMessage(StatusHistoryEntry previous, StatusHistoryEntry latest) {
        String previousLabel = previous != null ? previous.overallStatus().name() : "UNKNOWN";
        StringBuilder message = new StringBuilder()
                .append("Modtale status changed: ")
                .append(previousLabel)
                .append(" -> ")
                .append(latest.overallStatus().name())
                .append('\n')
                .append(properties.getPublicStatusUrl());

        for (ServiceStatusView service : latest.toServices()) {
            message.append('\n')
                    .append(service.name())
                    .append(": ")
                    .append(service.status().name())
                    .append(" (")
                    .append(service.latency())
                    .append("ms)");
        }

        return message.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
