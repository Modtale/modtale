package net.modtale.service.project;

import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.security.ScanService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class VersionMutationOrchestrationService {

    private final ValidationService validationService;
    private final ScanService scanService;
    private final SanitizationService sanitizationService;
    private final VersionArtifactService versionArtifactService;
    private final VersionDependencyService versionDependencyService;
    private final ProjectDeletionService projectDeletionService;

    public VersionMutationOrchestrationService(
            ValidationService validationService,
            ScanService scanService,
            SanitizationService sanitizationService,
            VersionArtifactService versionArtifactService,
            VersionDependencyService versionDependencyService,
            ProjectDeletionService projectDeletionService
    ) {
        this.validationService = validationService;
        this.scanService = scanService;
        this.sanitizationService = sanitizationService;
        this.versionArtifactService = versionArtifactService;
        this.versionDependencyService = versionDependencyService;
        this.projectDeletionService = projectDeletionService;
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
            List<String> projectIds,
            boolean modpack,
            boolean allowVersionlessDependencies
    ) {
        return versionDependencyService.resolveRequestedDependencies(projectIds, modpack, allowVersionlessDependencies);
    }

    public ScanResult maybeCreateQueuedScanResult(MultipartFile file, boolean modpack) {
        if (file == null || modpack) {
            return null;
        }
        return scanService.createQueuedScanResult(1, "Initial scan queued.");
    }

    public void enqueueInitialScan(Project project, ProjectVersion version, MultipartFile file, boolean modpack, String filePath) {
        if (file == null || modpack) {
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
}
