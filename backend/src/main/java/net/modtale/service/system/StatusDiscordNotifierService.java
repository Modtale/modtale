package net.modtale.service.system;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.AppStatusDiscordWebhookProperties;
import net.modtale.model.system.StatusHistory;
import net.modtale.model.system.SystemStatus;
import net.modtale.service.communication.DiscordWebhookIdentity;
import net.modtale.service.communication.WebhookDeliveryService;
import net.modtale.service.communication.WebhookDispatchRequest;
import org.springframework.stereotype.Service;

@Service
public class StatusDiscordNotifierService {

    private static final String STATUS_WEBHOOK_USERNAME = "Modtale Status";

    private final String webhookUrl;
    private final String frontendUrl;
    private final WebhookDeliveryService webhookDeliveryService;

    public StatusDiscordNotifierService(
            AppStatusDiscordWebhookProperties properties,
            AppFrontendProperties frontendProperties,
            WebhookDeliveryService webhookDeliveryService
    ) {
        this.webhookUrl = properties.url();
        this.frontendUrl = frontendProperties.url();
        this.webhookDeliveryService = webhookDeliveryService;
    }

    public void notifyStatusChange(StatusHistory previous, StatusHistory latest) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", titleFor(latest.getOverallStatus()));
        embed.put("url", cleanFrontendUrl() + "/status");
        embed.put("color", colorFor(latest.getOverallStatus()));
        embed.put("description", descriptionFor(previous, latest));
        embed.put("fields", List.of(
                field("API Gateway", statusLine(latest.getApiStatus(), latest.getApiLatency()), true),
                field("Database", statusLine(latest.getDbStatus(), latest.getDbLatency()), true),
                field("Storage", statusLine(latest.getStorageStatus(), latest.getStorageLatency()), true)
        ));
        embed.put("timestamp", latest.getTimestamp().toInstant(ZoneOffset.UTC).toString());

        Map<String, Object> footer = new HashMap<>();
        footer.put("text", "Modtale Status");
        embed.put("footer", footer);

        Map<String, Object> body = new HashMap<>();
        body.put("username", STATUS_WEBHOOK_USERNAME);
        body.put("avatar_url", DiscordWebhookIdentity.adminBotAvatarUrl(frontendUrl));
        body.put("content", contentFor(latest.getOverallStatus()));
        body.put("embeds", List.of(embed));

        webhookDeliveryService.deliverAsync(
                new WebhookDispatchRequest(webhookUrl, body),
                "Failed to trigger Status Discord webhook"
        );
    }

    private String descriptionFor(StatusHistory previous, StatusHistory latest) {
        String previousLabel = previous == null ? "No previous snapshot" : labelFor(previous.getOverallStatus());
        return "**Previous:** " + previousLabel
                + "\n**Current:** " + labelFor(latest.getOverallStatus())
                + "\n\nStatus changes are mirrored from the live Modtale health checks.";
    }

    private String contentFor(SystemStatus status) {
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> "**Resolved:** Modtale is operational.";
            case OUTAGE -> "**Status update:** Modtale is experiencing an outage.";
            default -> "**Status update:** Modtale is degraded.";
        };
    }

    private String titleFor(SystemStatus status) {
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> "All systems operational";
            case OUTAGE -> "Major outage detected";
            default -> "Partial system degradation";
        };
    }

    private int colorFor(SystemStatus status) {
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> 0x10B981;
            case OUTAGE -> 0xEF4444;
            default -> 0xF59E0B;
        };
    }

    private Map<String, Object> field(String name, String value, boolean inline) {
        Map<String, Object> field = new HashMap<>();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
        return field;
    }

    private String statusLine(SystemStatus status, int latency) {
        return labelFor(status) + "\n" + Math.max(latency, 0) + " ms";
    }

    private String labelFor(SystemStatus status) {
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> "Operational";
            case OUTAGE -> "Outage";
            default -> "Degraded";
        };
    }

    private SystemStatus resolveStatus(SystemStatus status) {
        return status != null ? status : SystemStatus.DEGRADED;
    }

    private String cleanFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "https://modtale.net";
        }

        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
