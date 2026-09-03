package net.modtale.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.modtale.status.StatusModels.IncidentBuckets;
import net.modtale.status.StatusModels.StatusHistoryEntry;
import net.modtale.status.StatusModels.SystemStatus;
import net.modtale.status.StatusModels.SystemStatusView;
import org.junit.jupiter.api.Test;

class DetachedStatusServiceTest {

    private final StatusProbeService statusProbeService = mock(StatusProbeService.class);
    private final MongoStatusStore mongoStatusStore = mock(MongoStatusStore.class);
    private final StatusSnapshotFileStore snapshotFileStore = mock(StatusSnapshotFileStore.class);
    private final StatusDiscordNotifier statusDiscordNotifier = mock(StatusDiscordNotifier.class);

    @Test
    void refreshBuildsStatusSnapshotWithoutDependingOnMainApplication() {
        StatusHistoryEntry entry = entry(Instant.now().minusSeconds(60), SystemStatus.DEGRADED);
        DetachedStatusService service = serviceWith(entry);

        service.refreshSnapshots();

        SystemStatusView status = service.getSystemStatus("24h");
        assertEquals(SystemStatus.DEGRADED, status.overall());
        assertEquals(4, status.services().size());
        assertEquals(1, status.history().size());
        assertEquals(1, status.observedSamples());
        assertEquals(1_440, status.expectedSamples());
        assertFalse(status.stale());
        assertTrue(service.isReady());
        assertEquals("site", status.services().getFirst().id());
        verify(mongoStatusStore).saveHistory(entry);
        verify(snapshotFileStore).writeHistory(List.of(entry));
    }

    @Test
    void staleHistoryCannotReportTheMonitorAsReadyOrFullyOperational() {
        StatusServiceProperties properties = new StatusServiceProperties();
        properties.setStaleAfter(java.time.Duration.ofMinutes(3));
        StatusHistoryEntry stale = entry(Instant.now().minusSeconds(10 * 60), SystemStatus.OPERATIONAL);
        when(snapshotFileStore.readHistory()).thenReturn(List.of(stale));
        when(mongoStatusStore.findHistoryAfter(any())).thenReturn(List.of());
        when(mongoStatusStore.findLatestHistory()).thenReturn(Optional.empty());
        when(mongoStatusStore.findIncidentBuckets()).thenReturn(Optional.of(IncidentBuckets.empty()));
        when(statusProbeService.performHealthCheck()).thenReturn(stale);

        DetachedStatusService service = new DetachedStatusService(
                properties,
                statusProbeService,
                mongoStatusStore,
                snapshotFileStore,
                statusDiscordNotifier
        );

        service.refreshSnapshots();

        SystemStatusView status = service.getSystemStatus("24h");
        assertTrue(status.stale());
        assertEquals(SystemStatus.DEGRADED, status.overall());
        assertFalse(service.isReady());
    }

    @Test
    void hydratePreservesExistingHistoryBeforeCurrentProbe() {
        Instant now = Instant.now();
        StatusHistoryEntry previous = entry(now.minusSeconds(60 * 60), SystemStatus.OPERATIONAL);
        StatusHistoryEntry current = entry(now.minusSeconds(60), SystemStatus.OUTAGE);
        when(snapshotFileStore.readHistory()).thenReturn(List.of(previous));
        when(mongoStatusStore.findHistoryAfter(any())).thenReturn(List.of());
        when(mongoStatusStore.findLatestHistory()).thenReturn(Optional.empty());
        when(mongoStatusStore.findIncidentBuckets()).thenReturn(Optional.of(IncidentBuckets.empty()));
        when(statusProbeService.performHealthCheck()).thenReturn(current);

        DetachedStatusService service = new DetachedStatusService(
                new StatusServiceProperties(),
                statusProbeService,
                mongoStatusStore,
                snapshotFileStore,
                statusDiscordNotifier
        );

        service.refreshSnapshots();

        SystemStatusView status = service.getSystemStatus("24h");
        assertEquals(SystemStatus.OUTAGE, status.overall());
        assertEquals(2, status.history().size());
        assertFalse(status.history().getFirst().time() > status.history().getLast().time());
    }

    private DetachedStatusService serviceWith(StatusHistoryEntry entry) {
        when(snapshotFileStore.readHistory()).thenReturn(List.of());
        when(mongoStatusStore.findHistoryAfter(any())).thenReturn(List.of());
        when(mongoStatusStore.findLatestHistory()).thenReturn(Optional.empty());
        when(mongoStatusStore.findIncidentBuckets()).thenReturn(Optional.of(IncidentBuckets.empty()));
        when(statusProbeService.performHealthCheck()).thenReturn(entry);

        return new DetachedStatusService(
                new StatusServiceProperties(),
                statusProbeService,
                mongoStatusStore,
                snapshotFileStore,
                statusDiscordNotifier
        );
    }

    private StatusHistoryEntry entry(Instant timestamp, SystemStatus overall) {
        return new StatusHistoryEntry(
                timestamp,
                25,
                40,
                35,
                55,
                overall,
                serviceStatusFor(overall),
                serviceStatusFor(overall),
                serviceStatusFor(overall),
                serviceStatusFor(overall)
        );
    }

    private SystemStatus serviceStatusFor(SystemStatus overall) {
        return overall == SystemStatus.OUTAGE ? SystemStatus.OUTAGE : SystemStatus.OPERATIONAL;
    }
}
