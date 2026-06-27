package net.modtale.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.modtale.status.StatusModels.IncidentBuckets;
import net.modtale.status.StatusModels.StatusHistoryEntry;
import net.modtale.status.StatusModels.StatusHistoryPointView;
import net.modtale.status.StatusModels.SystemStatusView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DetachedStatusService {

    private static final Logger logger = LoggerFactory.getLogger(DetachedStatusService.class);

    private final StatusServiceProperties properties;
    private final StatusProbeService statusProbeService;
    private final MongoStatusStore mongoStatusStore;
    private final StatusSnapshotFileStore snapshotFileStore;
    private final StatusDiscordNotifier statusDiscordNotifier;

    private final List<StatusHistoryEntry> history = new ArrayList<>();
    private volatile SystemStatusView cached24HourStatus;
    private volatile SystemStatusView cached30DayStatus;
    private volatile IncidentBuckets lastKnownIncidents = IncidentBuckets.empty();
    private volatile boolean hydrated;

    public DetachedStatusService(
            StatusServiceProperties properties,
            StatusProbeService statusProbeService,
            MongoStatusStore mongoStatusStore,
            StatusSnapshotFileStore snapshotFileStore,
            StatusDiscordNotifier statusDiscordNotifier
    ) {
        this.properties = properties;
        this.statusProbeService = statusProbeService;
        this.mongoStatusStore = mongoStatusStore;
        this.snapshotFileStore = snapshotFileStore;
        this.statusDiscordNotifier = statusDiscordNotifier;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        hydrate();
        refreshSnapshots();
    }

    @Scheduled(
            initialDelayString = "${status.refresh-interval-ms:60000}",
            fixedDelayString = "${status.refresh-interval-ms:60000}"
    )
    public synchronized void refreshSnapshots() {
        hydrate();
        StatusHistoryEntry previous = latestHistory();
        StatusHistoryEntry latest = statusProbeService.performHealthCheck();
        addHistory(latest);
        mongoStatusStore.saveHistory(latest);
        refreshIncidents();
        rebuildSnapshots();
        snapshotFileStore.writeHistory(List.copyOf(history));
        statusDiscordNotifier.notifyStatusChange(previous, latest);
    }

    public SystemStatusView getSystemStatus(String range) {
        SystemStatusView cached = "30d".equals(range) ? cached30DayStatus : cached24HourStatus;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            cached = "30d".equals(range) ? cached30DayStatus : cached24HourStatus;
            if (cached != null) {
                return cached;
            }

            refreshSnapshots();
            return "30d".equals(range) ? cached30DayStatus : cached24HourStatus;
        }
    }

    private void hydrate() {
        if (hydrated) {
            return;
        }

        synchronized (this) {
            if (hydrated) {
                return;
            }

            addHistory(snapshotFileStore.readHistory());
            Instant since = Instant.now().minus(properties.getHistoryRetention());
            addHistory(mongoStatusStore.findHistoryAfter(since));
            mongoStatusStore.findLatestHistory().ifPresent(this::addHistory);
            pruneHistory();
            refreshIncidents();
            rebuildSnapshots();
            hydrated = true;
        }
    }

    private void refreshIncidents() {
        IncidentBuckets buckets = mongoStatusStore.findIncidentBuckets();
        if (!buckets.activeIncidents().isEmpty()
                || !buckets.scheduledMaintenances().isEmpty()
                || !buckets.incidentHistory().isEmpty()) {
            lastKnownIncidents = buckets;
        }
    }

    private void rebuildSnapshots() {
        StatusHistoryEntry latest = latestHistory();
        if (latest == null) {
            return;
        }
        cached24HourStatus = buildStatusView(latest, "24h");
        cached30DayStatus = buildStatusView(latest, "30d");
    }

    private SystemStatusView buildStatusView(StatusHistoryEntry latest, String range) {
        Instant since = "30d".equals(range)
                ? Instant.now().minus(properties.getHistoryRetention())
                : Instant.now().minusSeconds(24 * 60 * 60);

        List<StatusHistoryEntry> entries = history.stream()
                .filter(entry -> !entry.timestamp().isBefore(since))
                .sorted(Comparator.comparing(StatusHistoryEntry::timestamp))
                .toList();

        if ("30d".equals(range) && entries.size() > 500) {
            entries = downsample(entries, 250);
        }

        List<StatusHistoryPointView> points = entries.stream()
                .map(StatusHistoryEntry::toPoint)
                .toList();

        return new SystemStatusView(
                latest.overallStatus(),
                latest.toServices(),
                latest.timestamp().toEpochMilli(),
                points,
                lastKnownIncidents.activeIncidents(),
                lastKnownIncidents.scheduledMaintenances(),
                lastKnownIncidents.incidentHistory()
        );
    }

    private void addHistory(StatusHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        addHistory(List.of(entry));
    }

    private void addHistory(List<StatusHistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        Map<Long, StatusHistoryEntry> byTimestamp = new LinkedHashMap<>();
        for (StatusHistoryEntry existing : history) {
            byTimestamp.put(existing.timestamp().toEpochMilli(), existing);
        }
        for (StatusHistoryEntry entry : entries) {
            if (entry != null && entry.timestamp() != null) {
                byTimestamp.put(entry.timestamp().toEpochMilli(), entry);
            }
        }

        history.clear();
        history.addAll(byTimestamp.values().stream()
                .sorted(Comparator.comparing(StatusHistoryEntry::timestamp))
                .toList());
        pruneHistory();
    }

    private void pruneHistory() {
        Instant oldest = Instant.now().minus(properties.getHistoryRetention());
        history.removeIf(entry -> entry.timestamp().isBefore(oldest));
    }

    private StatusHistoryEntry latestHistory() {
        if (history.isEmpty()) {
            return null;
        }
        return history.stream()
                .max(Comparator.comparing(StatusHistoryEntry::timestamp))
                .orElse(null);
    }

    private List<StatusHistoryEntry> downsample(List<StatusHistoryEntry> entries, int targetSize) {
        if (entries.size() <= targetSize) {
            return entries;
        }

        int step = Math.max(1, entries.size() / targetSize);
        List<StatusHistoryEntry> sampled = new ArrayList<>();
        for (int i = 0; i < entries.size(); i += step) {
            sampled.add(entries.get(i));
        }
        StatusHistoryEntry last = entries.get(entries.size() - 1);
        if (!sampled.get(sampled.size() - 1).timestamp().equals(last.timestamp())) {
            sampled.add(last);
        }
        logger.debug("Downsampled detached status history from {} to {} points", entries.size(), sampled.size());
        return sampled;
    }
}
