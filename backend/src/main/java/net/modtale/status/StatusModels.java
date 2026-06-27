package net.modtale.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class StatusModels {

    private StatusModels() {
    }

    public enum SystemStatus {
        OPERATIONAL,
        DEGRADED,
        OUTAGE
    }

    public enum StatusIncidentKind {
        INCIDENT,
        MAINTENANCE
    }

    public enum StatusIncidentState {
        SCHEDULED,
        INVESTIGATING,
        IDENTIFIED,
        MONITORING,
        RESOLVED,
        CANCELED
    }

    public record ProbeResult(String id, String name, SystemStatus status, int latency) {
    }

    public record ServiceStatusView(String id, String name, SystemStatus status, int latency) {
    }

    public record StatusHistoryPointView(
            long time,
            int site,
            int api,
            int db,
            int storage,
            SystemStatus siteStatus,
            SystemStatus apiStatus,
            SystemStatus dbStatus,
            SystemStatus storageStatus
    ) {
    }

    public record StatusIncidentUpdateView(
            String id,
            StatusIncidentState state,
            SystemStatus impact,
            String message,
            LocalDateTime createdAt,
            String createdByUsername
    ) {
    }

    public record StatusIncidentView(
            String id,
            StatusIncidentKind kind,
            StatusIncidentState state,
            SystemStatus impact,
            String title,
            List<String> affectedServices,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            LocalDateTime startedAt,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String createdByUsername,
            List<StatusIncidentUpdateView> updates
    ) {
        public boolean isClosed() {
            return state == StatusIncidentState.RESOLVED || state == StatusIncidentState.CANCELED;
        }
    }

    public record SystemStatusView(
            SystemStatus overall,
            List<ServiceStatusView> services,
            long timestamp,
            List<StatusHistoryPointView> history,
            List<StatusIncidentView> activeIncidents,
            List<StatusIncidentView> scheduledMaintenances,
            List<StatusIncidentView> incidentHistory
    ) {
    }

    public record StatusHistoryEntry(
            Instant timestamp,
            int siteLatency,
            int apiLatency,
            int dbLatency,
            int storageLatency,
            SystemStatus overallStatus,
            SystemStatus siteStatus,
            SystemStatus apiStatus,
            SystemStatus dbStatus,
            SystemStatus storageStatus
    ) {
        public StatusHistoryPointView toPoint() {
            return new StatusHistoryPointView(
                    timestamp.toEpochMilli(),
                    siteLatency,
                    apiLatency,
                    dbLatency,
                    storageLatency,
                    siteStatus,
                    apiStatus,
                    dbStatus,
                    storageStatus
            );
        }

        public List<ServiceStatusView> toServices() {
            return List.of(
                    new ServiceStatusView("site", "Main Site", siteStatus, siteLatency),
                    new ServiceStatusView("api", "API Gateway", apiStatus, apiLatency),
                    new ServiceStatusView("database", "Database (Atlas)", dbStatus, dbLatency),
                    new ServiceStatusView("storage", "Storage (R2)", storageStatus, storageLatency)
            );
        }

        public LocalDateTime timestampAsLocalDateTime() {
            return LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
        }
    }

    public record IncidentBuckets(
            List<StatusIncidentView> activeIncidents,
            List<StatusIncidentView> scheduledMaintenances,
            List<StatusIncidentView> incidentHistory
    ) {
        public static IncidentBuckets empty() {
            return new IncidentBuckets(List.of(), List.of(), List.of());
        }
    }
}
