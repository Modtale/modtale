package net.modtale.service.analytics;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingBufferServiceTest {

    private TrackingBufferService service;

    @BeforeEach
    void setUp() {
        service = new TrackingBufferService();
    }

    @Test
    void logDownloadDebouncesByProjectIpAndEventType() {
        service.logDownload("project-1", "version-1", "author-1", false, "203.0.113.10");
        service.logDownload("project-1", "version-1", "author-1", false, "203.0.113.10");
        service.logDownload("project-1", "version-1", "author-1", true, "203.0.113.11");

        TrackingBufferService.MetricsBatch metrics = service.drainMetricIncrements();
        TrackingBufferService.MonthlyAnalyticsBatch monthly = service.drainMonthlyAnalytics();

        assertEquals(Map.of("project-1", 2), metrics.downloads());
        assertEquals(Map.of("project-1|||version-1", 2), metrics.versionDownloads());
        assertEquals(2, monthly.downloads().size());
        assertEquals(1, monthly.downloads().stream().filter(TrackingBufferService.DownloadEvent::isApi).count());
    }

    @Test
    void logViewDebouncesByProjectIpAndAggregatesByProjectAuthor() {
        service.logView("project-1", "author-1", "203.0.113.10");
        service.logView("project-1", "author-1", "203.0.113.10");
        service.logView("project-1", "author-1", "");
        service.logView("project-1", "author-1", null);
        service.logView("project-1", "author-2", "203.0.113.11");

        TrackingBufferService.MonthlyAnalyticsBatch monthly = service.drainMonthlyAnalytics();

        assertEquals(Map.of(
                "project-1|author-1", 3,
                "project-1|author-2", 1
        ), monthly.views());
        assertTrue(service.drainMonthlyAnalytics().isEmpty());
    }

    @Test
    void drainAndRestoreMetricIncrementsRoundTripFailedBatches() {
        service.logDownload("project-1", "version-1", "author-1", false, null);
        service.logDownload("project-2", null, "author-2", false, null);

        TrackingBufferService.MetricsBatch drained = service.drainMetricIncrements();

        assertEquals(Map.of("project-1", 1, "project-2", 1), drained.downloads());
        assertEquals(Map.of("project-1|||version-1", 1), drained.versionDownloads());
        assertTrue(service.drainMetricIncrements().isEmpty());

        service.restoreMetricIncrements(drained);

        assertEquals(drained, service.drainMetricIncrements());
    }

    @Test
    void drainPlatformEntitiesReturnsNetCountsAndThenEmptiesBuffers() {
        service.logNewProject("project-1");
        service.logNewProject("project-2");
        service.logDeletedProject("project-3");
        service.logNewUser("user-1");
        service.logDeletedUser("user-2");
        service.logDeletedUser("user-3");
        service.logNewOrg("org-1");

        TrackingBufferService.PlatformEntityBatch batch = service.drainPlatformEntities();

        assertTrue(batch.hasUpdates());
        assertEquals(1, batch.netProjects());
        assertEquals(-1, batch.netUsers());
        assertEquals(1, batch.netOrgs());

        TrackingBufferService.PlatformEntityBatch empty = service.drainPlatformEntities();
        assertFalse(empty.hasUpdates());
        assertEquals(0, empty.netProjects());
        assertEquals(0, empty.netUsers());
        assertEquals(0, empty.netOrgs());
    }

    @Test
    void emptyDrainsReturnImmutableEmptyBatches() {
        assertEquals(List.of(), service.drainMonthlyAnalytics().downloads());
        assertEquals(Map.of(), service.drainMonthlyAnalytics().views());
        assertEquals(Map.of(), service.drainMetricIncrements().downloads());
        assertEquals(Map.of(), service.drainMetricIncrements().versionDownloads());
    }
}
