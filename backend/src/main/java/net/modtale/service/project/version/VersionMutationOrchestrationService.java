package net.modtale.service.project.version;

import java.util.List;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.service.project.lifecycle.ProjectDeletionService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.scan.ScanService;
import net.modtale.service.security.validation.SanitizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VersionMutationOrchestrationService {

    private final ValidationService validationService;
    private final ScanService scanService;
    private final SanitizationService sanitizationService;
    private final VersionArtifactService versionArtifactService;
    private final VersionDependencyService versionDependencyService;
    private final ExternalDependencyArtifactService externalDependencyArtifactService;
    private final ProjectDeletionService projectDeletionService;

    @Autowired
    public VersionMutationOrchestrationService(
            ValidationService validationService,
            ScanService scanService,
            SanitizationService sanitizationService,
            VersionArtifactService versionArtifactService,
            VersionDependencyService versionDependencyService,
            ExternalDependencyArtifactService externalDependencyArtifactService,
            ProjectDeletionService projectDeletionService
    ) {
        this.validationService = validationService;
        this.scanService = scanService;
        this.sanitizationService = sanitizationService;
        this.versionArtifactService = versionArtifactService;
        this.versionDependencyService = versionDependencyService;
        this.externalDependencyArtifactService = externalDependencyArtifactService;
        this.projectDeletionService = projectDeletionService;
    }

    public VersionMutationOrchestrationService(
            ValidationService validationService,
            ScanService scanService,
            SanitizationService sanitizationService,
            VersionArtifactService versionArtifactService,
            VersionDependencyService versionDependencyService,
            ProjectDeletionService projectDeletionService
    ) {
        this(
                validationService,
                scanService,
                sanitizationService,
                versionArtifactService,
                versionDependencyService,
                null,
                projectDeletionService
        );
    }

    public void validateVersionNumber(String versionNumber) {
        validationService.validateVersionNumber(versionNumber);
    }

    public void validateGameVersions(List<String> gameVersions) {
        if (gameVersions == null) {
            return;
        }

        List<String> allowedGameVersions = validationService.getAllowedGameVersions();
        for (String gameVersion : gameVersions) {
            if (!allowedGameVersions.contains(gameVersion)) {
                throw new InvalidVersionRequestException("Invalid game version: " + gameVersion);
            }
        }
    }

    public String sanitizeChangelog(String changelog) {
        return sanitizationService.sanitizePlainText(changelog);
    }

    public VersionArtifactService.PreparedVersionArtifact prepareVersionArtifact(Project project, MultipartFile file) {
        return versionArtifactService.prepareVersionArtifact(project, file);
    }

    public VersionDependencyService.ResolvedDependencies resolveRequestedDependencies(
            List<DependencyReferenceRequest> dependencies,
            boolean modpack,
            boolean allowVersionlessDependencies
    ) {
        VersionDependencyService.ResolvedDependencies resolvedDependencies =
                versionDependencyService.resolveRequestedDependencies(dependencies, modpack, allowVersionlessDependencies);
        if (externalDependencyArtifactService != null) {
            externalDependencyArtifactService.prepareExternalArtifacts(resolvedDependencies.dependencies());
        }
        return resolvedDependencies;
    }

    public List<String> resolveRequestedProjectIds(List<String> projectIds, boolean allowDraftProjects) {
        return versionDependencyService.resolveRequestedProjectIds(projectIds, allowDraftProjects);
    }

    public ScanResult maybeCreateQueuedScanResult(Project project, MultipartFile file, boolean modpack) {
        if (!shouldScanImmediately(project, file, modpack)) {
            return null;
        }
        return scanService.createQueuedScanResult(1, "Initial scan queued.");
    }

    public void enqueueInitialScan(Project project, ProjectVersion version, MultipartFile file, boolean modpack, String filePath) {
        if (!shouldScanImmediately(project, file, modpack)) {
            return;
        }
        scanService.enqueueBackgroundScan(
                project.getId(),
                version.getId(),
                filePath,
                file.getOriginalFilename(),
                false,
                1
        );
    }

    public boolean queueSubmissionScanIfNeeded(Project project, ProjectVersion version) {
        if (project == null
                || version == null
                || project.getClassification() == ProjectClassification.MODPACK
                || version.getFileUrl() == null
                || version.getFileUrl().isBlank()
                || version.getScanResult() != null) {
            return false;
        }

        version.setScanResult(scanService.createQueuedScanResult(1, "Initial scan queued."));
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setScheduledPublishDate(null);
        return true;
    }

    public void enqueueSubmissionScan(Project project, ProjectVersion version) {
        if (project == null || version == null || version.getFileUrl() == null || version.getFileUrl().isBlank()) {
            return;
        }

        scanService.enqueueBackgroundScan(
                project.getId(),
                version.getId(),
                version.getFileUrl(),
                version.getFileUrl(),
                false,
                1
        );
    }

    private boolean shouldScanImmediately(Project project, MultipartFile file, boolean modpack) {
        return project != null
                && project.getStatus() != ProjectStatus.DRAFT
                && file != null
                && !modpack;
    }

    public void invalidateCachedModpackArtifact(ProjectVersion version, List<ProjectDependency> dependencies) {
        if (version == null || dependencies == null) {
            return;
        }
        if (!dependencies.equals(version.getDependencies())
                && version.getFileUrl() != null
                && version.getFileUrl().endsWith(".zip")) {
            projectDeletionService.deleteStoredFile(version.getFileUrl());
            version.setFileUrl(null);
        }
    }

    public void deleteVersionFile(ProjectVersion version) {
        projectDeletionService.deleteVersionFile(version);
    }
}
