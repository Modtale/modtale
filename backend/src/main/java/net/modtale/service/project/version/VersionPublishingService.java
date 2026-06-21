package net.modtale.service.project.version;

import java.time.LocalDateTime;
import net.modtale.service.project.lifecycle.ScheduledReleaseExecutionService;
import net.modtale.service.project.lifecycle.ScheduledReleaseQueryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class VersionPublishingService {

    private final ScheduledReleaseQueryService scheduledReleaseQueryService;
    private final ScheduledReleaseExecutionService scheduledReleaseExecutionService;

    public VersionPublishingService(
            ScheduledReleaseQueryService scheduledReleaseQueryService,
            ScheduledReleaseExecutionService scheduledReleaseExecutionService
    ) {
        this.scheduledReleaseQueryService = scheduledReleaseQueryService;
        this.scheduledReleaseExecutionService = scheduledReleaseExecutionService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.release-check:900000}")
    public void processScheduledReleases() {
        LocalDateTime publishTime = LocalDateTime.now();
        scheduledReleaseQueryService.findProjectsWithReleasesDueAt(publishTime)
                .forEach(project -> scheduledReleaseExecutionService.publishDueVersions(project, publishTime));
    }
}
