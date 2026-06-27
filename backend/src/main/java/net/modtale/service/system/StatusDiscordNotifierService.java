package net.modtale.service.system;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    private static final String STATUS_WEBHOOK_USERNAME = "Modtale Status Monitor";
    private static final int UPTIME_WINDOW_DAYS = 30;
    private static final int UPTIME_BUCKET_COUNT = 10;
    private static final String GREEN_CIRCLE = "\uD83D\uDFE2";
    private static final String YELLOW_CIRCLE = "\uD83D\uDFE1";
    private static final String RED_CIRCLE = "\uD83D\uDD34";
    private static final String GREEN_SQUARE = "\uD83D\uDFE9";
    private static final String YELLOW_SQUARE = "\uD83D\uDFE8";
    private static final String RED_SQUARE = "\uD83D\uDFE5";
    private static final String NO_DATA_SQUARE = "\u2B1B";
    private static final String GLOBE_ICON = "\uD83C\uDF10";
    private static final String DATABASE_ICON = "\uD83D\uDCC1";
    private static final String PACKAGE_ICON = "\uD83D\uDCE6";

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
        notifyStatusChange(previous, latest, latest == null ? List.of() : List.of(latest));
    }

    public void notifyStatusChange(StatusHistory previous, StatusHistory latest, List<StatusHistory> history) {
        if (latest == null || webhookUrl == null || webhookUrl.isBlank() || !isProductionStatusDeployment()) {
            return;
        }

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", statusIcon(latest.getOverallStatus()) + " Modtale Status Monitor");
        embed.put("url", cleanFrontendUrl() + "/status");
        embed.put("color", colorFor(latest.getOverallStatus()));
        embed.put("description", descriptionFor(latest, history));
        embed.put("timestamp", timestampFor(latest).toInstant(ZoneOffset.UTC).toString());

        Map<String, Object> footer = new HashMap<>();
        footer.put("text", "Live Modtale health checks");
        embed.put("footer", footer);

        Map<String, Object> body = new HashMap<>();
        body.put("username", STATUS_WEBHOOK_USERNAME);
        body.put("avatar_url", DiscordWebhookIdentity.adminBotAvatarUrl(cleanFrontendUrl()));
        body.put("embeds", List.of(embed));

        webhookDeliveryService.deliverAsync(
                new WebhookDispatchRequest(webhookUrl, body),
                "Failed to trigger Status Discord webhook"
        );
    }

    private String descriptionFor(StatusHistory latest, List<StatusHistory> history) {
        StringBuilder description = new StringBuilder();
        description.append("**Real-time service status and uptime**\n\n");
        description.append("Live monitoring of all Modtale services (last 30 days)");

        for (MonitoredService service : MonitoredService.values()) {
            SystemStatus currentStatus = service.resolveStatus(latest);
            description.append("\n\n")
                    .append(service.icon())
                    .append(" **")
                    .append(service.displayName())
                    .append("**\n")
                    .append(uptimeBar(history, latest, service))
                    .append(" **")
                    .append(labelFor(currentStatus))
                    .append("**");
        }

        return description.toString();
    }

    private int colorFor(SystemStatus status) {
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> 0x10B981;
            case OUTAGE -> 0xEF4444;
            default -> 0xF59E0B;
        };
    }

    private String labelFor(SystemStatus status) {
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> "Operational";
            case OUTAGE -> "Outage";
            default -> "Degraded";
        };
    }

    private String statusIcon(SystemStatus status) {
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> GREEN_CIRCLE;
            case OUTAGE -> RED_CIRCLE;
            default -> YELLOW_CIRCLE;
        };
    }

    private String uptimeBar(List<StatusHistory> history, StatusHistory latest, MonitoredService service) {
        List<StatusHistory> snapshots = history == null
                ? List.of()
                : history.stream()
                .filter(snapshot -> snapshot != null && snapshot.getTimestamp() != null)
                .sorted(Comparator.comparing(StatusHistory::getTimestamp))
                .toList();

        if (snapshots.isEmpty()) {
            return blockFor(service.resolveStatus(latest)).repeat(UPTIME_BUCKET_COUNT);
        }

        LocalDateTime windowEnd = timestampFor(latest);
        LocalDateTime windowStart = windowEnd.minusDays(UPTIME_WINDOW_DAYS);
        long bucketSeconds = Math.max(1, Duration.between(windowStart, windowEnd).getSeconds() / UPTIME_BUCKET_COUNT);

        StringBuilder bar = new StringBuilder();
        for (int index = 0; index < UPTIME_BUCKET_COUNT; index++) {
            LocalDateTime bucketStart = windowStart.plusSeconds(bucketSeconds * index);
            LocalDateTime bucketEnd = index == UPTIME_BUCKET_COUNT - 1
                    ? windowEnd.plusNanos(1)
                    : windowStart.plusSeconds(bucketSeconds * (index + 1));
            bar.append(blockFor(bucketStatus(snapshots, service, bucketStart, bucketEnd)));
        }
        return bar.toString();
    }

    private SystemStatus bucketStatus(
            List<StatusHistory> snapshots,
            MonitoredService service,
            LocalDateTime bucketStart,
            LocalDateTime bucketEnd
    ) {
        boolean operational = false;
        boolean degraded = false;
        boolean outage = false;

        for (StatusHistory snapshot : snapshots) {
            LocalDateTime timestamp = snapshot.getTimestamp();
            if (timestamp.isBefore(bucketStart) || !timestamp.isBefore(bucketEnd)) {
                continue;
            }

            switch (service.resolveStatus(snapshot)) {
                case OPERATIONAL -> operational = true;
                case OUTAGE -> outage = true;
                default -> degraded = true;
            }
        }

        if (outage) {
            return SystemStatus.OUTAGE;
        }
        if (degraded) {
            return SystemStatus.DEGRADED;
        }
        if (operational) {
            return SystemStatus.OPERATIONAL;
        }
        return null;
    }

    private String blockFor(SystemStatus status) {
        if (status == null) {
            return NO_DATA_SQUARE;
        }
        return switch (resolveStatus(status)) {
            case OPERATIONAL -> GREEN_SQUARE;
            case OUTAGE -> RED_SQUARE;
            default -> YELLOW_SQUARE;
        };
    }

    private LocalDateTime timestampFor(StatusHistory latest) {
        return latest.getTimestamp() != null ? latest.getTimestamp() : LocalDateTime.now();
    }

    private SystemStatus resolveStatus(SystemStatus status) {
        return status != null ? status : SystemStatus.DEGRADED;
    }

    private boolean isProductionStatusDeployment() {
        String host = extractHost(frontendUrl);
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals("modtale.net") || normalizedHost.equals("www.modtale.net");
    }

    private String extractHost(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String candidate = url.trim();
        if (!candidate.contains("://")) {
            candidate = "https://" + candidate;
        }

        try {
            return URI.create(candidate).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String cleanFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "https://modtale.net";
        }

        String cleanUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return cleanUrl.contains("://") ? cleanUrl : "https://" + cleanUrl;
    }

    private enum MonitoredService {
        API("API Gateway", GLOBE_ICON) {
            @Override
            SystemStatus storedStatus(StatusHistory history) {
                return history.getApiStatus();
            }
        },
        DATABASE("Database (Atlas)", DATABASE_ICON) {
            @Override
            SystemStatus storedStatus(StatusHistory history) {
                return history.getDbStatus();
            }
        },
        STORAGE("Storage (R2)", PACKAGE_ICON) {
            @Override
            SystemStatus storedStatus(StatusHistory history) {
                return history.getStorageStatus();
            }
        };

        private final String displayName;
        private final String icon;

        MonitoredService(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        abstract SystemStatus storedStatus(StatusHistory history);

        String displayName() {
            return displayName;
        }

        String icon() {
            return icon;
        }

        SystemStatus resolveStatus(StatusHistory history) {
            if (history == null) {
                return SystemStatus.DEGRADED;
            }
            SystemStatus status = storedStatus(history);
            return status != null
                    ? status
                    : (history.getOverallStatus() != null ? history.getOverallStatus() : SystemStatus.DEGRADED);
        }
    }
}
