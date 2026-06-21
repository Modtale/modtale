package net.modtale.service.security.scan;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectNotFoundException;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScanRequestService {

    private static final Logger logger = LoggerFactory.getLogger(ScanRequestService.class);

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ScanThrottleService scanThrottleService;
    private final ScanRoutingService scanRoutingService;
    private final ProjectVersionAccessService projectVersionAccessService;
    private final ScanExecutionService scanExecutionService;

    public ScanRequestService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ScanThrottleService scanThrottleService,
            ScanRoutingService scanRoutingService,
            ProjectVersionAccessService projectVersionAccessService,
            ScanExecutionService scanExecutionService
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.scanThrottleService = scanThrottleService;
        this.scanRoutingService = scanRoutingService;
        this.projectVersionAccessService = projectVersionAccessService;
        this.scanExecutionService = scanExecutionService;
    }

    public void triggerRescan(String projectId, String versionId, User user) {
        scanThrottleService.enforceRescanLimit(user);

        Project project = projectService.getRawProjectById(projectId);
        if (project == null) {
            throw new ProjectNotFoundException("We couldn't find that project.");
        }

        ProjectVersion version = projectVersionAccessService.findById(project, versionId);
        if (version == null) {
            throw new VersionNotFoundException("We couldn't find that project version.");
        }
        if (version.getFileUrl() == null) {
            throw new InvalidProjectRequestException("This version does not have an uploaded file to scan.");
        }

        int attempt = scanRoutingService.nextScanAttempt(version.getScanResult());
        ScanResult pending = scanRoutingService.createQueuedScanResult(attempt, "Manual rescan requested.");
        version.setScanResult(pending);
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setScheduledPublishDate(null);

        projectRepository.save(project);
        projectService.evictProjectCache(project);

        String originalFilename = scanExecutionService.extractOriginalFilename(version.getFileUrl());
        logger.info("Queued manual scan retry for project={} version={} attempt={}", projectId, versionId, attempt);

        scanExecutionService.enqueueBackgroundScan(
                projectId,
                versionId,
                version.getFileUrl(),
                originalFilename,
                true,
                attempt
        );
    }

    public ScanResult createQueuedScanResult(int attempt, String note) {
        return scanRoutingService.createQueuedScanResult(attempt, note);
    }

    public int nextScanAttempt(ScanResult existing) {
        return scanRoutingService.nextScanAttempt(existing);
    }
}
