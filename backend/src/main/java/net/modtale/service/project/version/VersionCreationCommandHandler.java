package net.modtale.service.project.version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.exception.VersionStateConflictException;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.query.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VersionCreationCommandHandler {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final VersionMutationOrchestrationService versionMutationOrchestrationService;
    private final int maxVersionsPerDay;

    public VersionCreationCommandHandler(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            VersionMutationOrchestrationService versionMutationOrchestrationService,
            AppLimitProperties limitProperties
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.versionMutationOrchestrationService = versionMutationOrchestrationService;
        this.maxVersionsPerDay = limitProperties.maxVersionsPerDay();
    }

    public void addVersion(
            String projectId,
            String versionNumber,
            List<String> gameVersions,
            MultipartFile file,
            String changelog,
            List<DependencyReferenceRequest> dependencies,
            List<String> incompatibleProjectIds,
            ProjectVersion.Channel channel,
            User user
    ) {
        Project project = projectAccessService.requireVersionPermission(projectId, user, "VERSION_CREATE",
                "You do not have permission to add a version to this project.");
        projectMutationGuard.ensureEditable(project);

        ensureDraftProjectCanAcceptVersion(project);
        ensureProjectIsWithinDailyVersionLimit(project);
        versionMutationOrchestrationService.validateVersionNumber(versionNumber);
        ensureVersionNumberIsUnique(project, versionNumber, gameVersions);
        versionMutationOrchestrationService.validateGameVersions(gameVersions);

        VersionArtifactService.PreparedVersionArtifact preparedArtifact =
                versionMutationOrchestrationService.prepareVersionArtifact(project, file);
        boolean modpack = preparedArtifact.classification() == ProjectClassification.MODPACK;

        ProjectVersion version = buildVersion(project, versionNumber, gameVersions, changelog, channel, preparedArtifact, file, modpack);

        List<String> simpleProjectIds = new ArrayList<>();
        if (dependencies != null) {
            VersionDependencyService.ResolvedDependencies resolvedDependencies =
                    versionMutationOrchestrationService.resolveRequestedDependencies(dependencies, modpack, false);
            version.setDependencies(new ArrayList<>(resolvedDependencies.dependencies()));
            simpleProjectIds.addAll(resolvedDependencies.simpleProjectIds());
        }

        if (incompatibleProjectIds != null) {
            version.setIncompatibleProjectIds(new ArrayList<>(
                    versionMutationOrchestrationService.resolveRequestedProjectIds(incompatibleProjectIds, false)
            ));
        }

        if (modpack) {
            project.setChildProjectIds(simpleProjectIds);
        }

        project.getVersions().add(0, version);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        versionMutationOrchestrationService.enqueueInitialScan(project, version, file, modpack, preparedArtifact.filePath());
    }

    private ProjectVersion buildVersion(
            Project project,
            String versionNumber,
            List<String> gameVersions,
            String changelog,
            ProjectVersion.Channel channel,
            VersionArtifactService.PreparedVersionArtifact preparedArtifact,
            MultipartFile file,
            boolean modpack
    ) {
        ProjectVersion version = new ProjectVersion();
        version.setId(UUID.randomUUID().toString());
        version.setVersionNumber(versionNumber);
        version.setGameVersions(gameVersions);
        version.setFileUrl(preparedArtifact.filePath());
        version.setReleaseDate(LocalDateTime.now().toString());
        version.setDownloadCount(0);
        version.setChangelog(versionMutationOrchestrationService.sanitizeChangelog(changelog));
        version.setChannel(channel);
        version.setHash(preparedArtifact.fileHash());
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setDependencies(new ArrayList<>());
        version.setIncompatibleProjectIds(new ArrayList<>());
        version.setScanResult(versionMutationOrchestrationService.maybeCreateQueuedScanResult(project, file, modpack));
        return version;
    }

    private void ensureDraftProjectCanAcceptVersion(Project project) {
        if (project.getStatus() == net.modtale.model.project.ProjectStatus.DRAFT && !project.getVersions().isEmpty()) {
            throw new InvalidVersionRequestException("Draft projects can only have one version.");
        }
    }

    private void ensureProjectIsWithinDailyVersionLimit(Project project) {
        LocalDate today = LocalDate.now();
        long versionsToday = project.getVersions().stream()
                .filter(version -> version.getReleaseDate().startsWith(today.toString()))
                .count();
        if (versionsToday >= maxVersionsPerDay) {
            throw new VersionStateConflictException("This project has already reached its daily version upload limit.");
        }
    }

    private void ensureVersionNumberIsUnique(Project project, String versionNumber, List<String> gameVersions) {
        boolean duplicateVersionWithOverlap = project.getVersions().stream().anyMatch(version ->
                version.getVersionNumber() != null
                        && version.getVersionNumber().equalsIgnoreCase(versionNumber)
                        && gameVersionsOverlap(version.getGameVersions(), gameVersions)
        );
        if (duplicateVersionWithOverlap) {
            throw new InvalidVersionRequestException("A version with the same number already exists for this game/API target.");
        }
    }

    private boolean gameVersionsOverlap(List<String> existingGameVersions, List<String> requestedGameVersions) {
        if (existingGameVersions == null || existingGameVersions.isEmpty()
                || requestedGameVersions == null || requestedGameVersions.isEmpty()) {
            return true;
        }
        for (String existing : existingGameVersions) {
            for (String requested : requestedGameVersions) {
                if (existing != null && requested != null && existing.equalsIgnoreCase(requested)) {
                    return true;
                }
            }
        }
        return false;
    }
}
