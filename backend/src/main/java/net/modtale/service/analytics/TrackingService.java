package net.modtale.service.analytics;

import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.query.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TrackingService {

    private final TrackingBufferService trackingBufferService;
    private final TrackingFlushService trackingFlushService;

    @Autowired
    public TrackingService(
            TrackingBufferService trackingBufferService,
            TrackingFlushService trackingFlushService
    ) {
        this.trackingBufferService = trackingBufferService;
        this.trackingFlushService = trackingFlushService;
    }

    public TrackingService(
            MongoTemplate mongoTemplate,
            ProjectRepository projectRepository,
            ProjectService projectService
    ) {
        TrackingBufferService bufferService = new TrackingBufferService();
        this.trackingBufferService = bufferService;
        this.trackingFlushService = new TrackingFlushService(
                mongoTemplate,
                projectService,
                bufferService
        );
    }

    public void logDownload(String projectId, String versionId, String authorId, boolean isApi, String clientIp) {
        trackingBufferService.logDownload(projectId, versionId, authorId, isApi, clientIp);
    }

    public void logView(String projectId, String authorId, String clientIp) {
        trackingBufferService.logView(projectId, authorId, clientIp);
    }

    public void logNewProject(String id) {
        trackingBufferService.logNewProject(id);
    }

    public void logDeletedProject(String id) {
        trackingBufferService.logDeletedProject(id);
    }

    public void logNewUser(String id) {
        trackingBufferService.logNewUser(id);
    }

    public void logDeletedUser(String id) {
        trackingBufferService.logDeletedUser(id);
    }

    public void logNewOrg(String id) {
        trackingBufferService.logNewOrg(id);
    }

    public void logDeletedOrg(String id) {
        trackingBufferService.logDeletedOrg(id);
    }

    public void deleteProjectAnalytics(String projectId) {
        trackingFlushService.deleteProjectAnalytics(projectId);
    }

    @Scheduled(fixedRate = 10000)
    public void flushAnalyticsBuffer() {
        trackingFlushService.flushAnalyticsBuffer();
    }
}
