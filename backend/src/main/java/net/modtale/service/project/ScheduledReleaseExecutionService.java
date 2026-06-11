package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.security.SecurityIssueAnalysisService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduledReleaseExecutionService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectNotificationService projectNotificationService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;

    public ScheduledReleaseExecutionService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectNotificationService projectNotificationService,
            SecurityIssueAnalysisService securityIssueAnalysisService
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectNotificationService = projectNotificationService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
    }

    public List<String> publishDueVersions(Project project, LocalDateTime publishTime) {
        List<String> releasedVersions = new ArrayList<>();
        if (project.getVersions() == null) {
            return releasedVersions;
        }

        for (ProjectVersion version : project.getVersions()) {
            if (!isDueForRelease(version, publishTime)) {
                continue;
            }

            version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
            version.setScheduledPublishDate(null);
            securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(version);
            releasedVersions.add(version.getVersionNumber());
        }

        if (releasedVersions.isEmpty()) {
            return releasedVersions;
        }

        project.setUpdatedAt(publishTime.toString());
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        releasedVersions.forEach(versionNumber -> {
            projectNotificationService.notifyUpdates(project, versionNumber);
            projectNotificationService.notifyDependents(project, versionNumber);
        });
        return releasedVersions;
    }

    private boolean isDueForRelease(ProjectVersion version, LocalDateTime publishTime) {
        if (version.getReviewStatus() != ProjectVersion.ReviewStatus.SCHEDULED
                || version.getScheduledPublishDate() == null) {
            return false;
        }
        return !LocalDateTime.parse(version.getScheduledPublishDate()).isAfter(publishTime);
    }
}
