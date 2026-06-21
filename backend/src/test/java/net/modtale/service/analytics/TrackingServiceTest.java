package net.modtale.service.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TrackingServiceTest {

    private TrackingBufferService trackingBufferService;
    private TrackingFlushService trackingFlushService;
    private TrackingService service;

    @BeforeEach
    void setUp() {
        trackingBufferService = mock(TrackingBufferService.class);
        trackingFlushService = mock(TrackingFlushService.class);
        service = new TrackingService(trackingBufferService, trackingFlushService);
    }

    @Test
    void delegatesEventLoggingToBufferService() {
        service.logDownload("project-1", "version-1", "author-1", true, "203.0.113.10");
        service.logView("project-1", "author-1", "203.0.113.10");
        service.logNewProject("project-1");
        service.logDeletedProject("project-2");
        service.logNewUser("user-1");
        service.logDeletedUser("user-2");
        service.logNewOrg("org-1");
        service.logDeletedOrg("org-2");

        verify(trackingBufferService).logDownload("project-1", "version-1", "author-1", true, "203.0.113.10");
        verify(trackingBufferService).logView("project-1", "author-1", "203.0.113.10");
        verify(trackingBufferService).logNewProject("project-1");
        verify(trackingBufferService).logDeletedProject("project-2");
        verify(trackingBufferService).logNewUser("user-1");
        verify(trackingBufferService).logDeletedUser("user-2");
        verify(trackingBufferService).logNewOrg("org-1");
        verify(trackingBufferService).logDeletedOrg("org-2");
    }

    @Test
    void delegatesFlushAndDeletionToFlushService() {
        service.flushAnalyticsBuffer();
        service.deleteProjectAnalytics("project-1");

        verify(trackingFlushService).flushAnalyticsBuffer();
        verify(trackingFlushService).deleteProjectAnalytics("project-1");
    }
}
