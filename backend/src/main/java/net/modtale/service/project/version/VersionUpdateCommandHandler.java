package net.modtale.service.project.version;

import java.util.List;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import org.springframework.stereotype.Service;

@Service
public class VersionUpdateCommandHandler {

    private final ProjectReviewPersistence reviewPersistence;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final ProjectVersionAccessService projectVersionAccessService;
    private final VersionMutationOrchestrationService versionMutationOrchestrationService;

    public VersionUpdateCommandHandler(
            ProjectReviewPersistence reviewPersistence,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            ProjectVersionAccessService projectVersionAccessService,
            VersionMutationOrchestrationService versionMutationOrchestrationService
    ) {
        this.reviewPersistence = reviewPersistence;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.projectVersionAccessService = projectVersionAccessService;
        this.versionMutationOrchestrationService = versionMutationOrchestrationService;
    }

    public void updateVersion(
            String projectId,
            String versionId,
            List<DependencyReferenceRequest> dependencies,
            List<String> incompatibleProjectIds,
            List<String> gameVersions,
            String changelog,
            ProjectVersion.Channel channel,
            User user
    ) {
        Project project = projectAccessService.requireVersionPermission(projectId, user, "VERSION_EDIT",
                "You do not have permission to update this version.");
        projectMutationGuard.ensureEditable(project);
        var snapshot = reviewPersistence.capture(projectId, ProjectReviewSnapshot.token(project));
        project = snapshot.project();

        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new VersionNotFoundException("We couldn't find that project version."));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var originalContext = mapper.valueToTree(java.util.Arrays.asList(version.getGameVersions(), version.getDependencies()));
        String originalFingerprint = net.modtale.service.security.scan.ArtifactReviewContext.fingerprint(version);
        var previousScan = version.getScanResult();
        String oldCachedArchive = null;
        boolean childIdsChanged = false;
        if (gameVersions != null) {
            versionMutationOrchestrationService.validateGameVersions(gameVersions);
            version.setGameVersions(gameVersions);
        }
        if (changelog != null) {
            version.setChangelog(versionMutationOrchestrationService.sanitizeChangelog(changelog));
        }
        if (channel != null) {
            version.setChannel(channel);
        }

        boolean modpack = project.getClassification() == ProjectClassification.MODPACK;
        if (dependencies != null) {
            VersionDependencyService.ResolvedDependencies resolvedDependencies =
                    versionMutationOrchestrationService.resolveRequestedDependencies(dependencies, modpack, true);
            List<ProjectDependency> resolvedProjectDependencies = resolvedDependencies.dependencies();
            if (modpack && version.getModpackConfigs() != null) {
                try { net.modtale.service.storage.ModpackOverrideArchive.validateOwners(version.getModpackConfigs(), resolvedProjectDependencies); }
                catch (java.io.IOException ex) { throw new net.modtale.exception.InvalidVersionRequestException("This mod has attached configs. Upload a new version to change its config ownership."); }
            }
            if (modpack) {
                if (!java.util.Objects.equals(mapper.valueToTree(version.getDependencies()), mapper.valueToTree(resolvedProjectDependencies))
                        && version.getFileUrl() != null && version.getFileUrl().endsWith(".zip")) {
                    oldCachedArchive = version.getFileUrl();
                    version.setFileUrl(null);
                }
            }
            version.setDependencies(resolvedProjectDependencies);
            if (modpack && project.getVersions().get(0).getId().equals(versionId)) {
                project.setChildProjectIds(resolvedDependencies.simpleProjectIds());
                childIdsChanged = true;
            }
        }
        if (incompatibleProjectIds != null) {
            version.setIncompatibleProjectIds(new java.util.ArrayList<>(
                    versionMutationOrchestrationService.resolveRequestedProjectIds(incompatibleProjectIds, true)
            ));
        }

        String updatedFingerprint = net.modtale.service.security.scan.ArtifactReviewContext.fingerprint(version);
        boolean contextChanged = originalFingerprint != null && updatedFingerprint != null
                ? !originalFingerprint.equals(updatedFingerprint) : !originalContext.equals(mapper.valueToTree(java.util.Arrays.asList(version.getGameVersions(), version.getDependencies())));
        if (contextChanged) {
            version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
            version.setScheduledPublishDate(null);
            version.setScanResult(null);
        }
        boolean queued = contextChanged && versionMutationOrchestrationService.prepareContextChangeScan(project, version, previousScan);
        if (!reviewPersistence.applyVersionEdit(snapshot, versionId, contextChanged, childIdsChanged)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
        if (queued) versionMutationOrchestrationService.enqueueContextChangeScan(project, version);
        if (oldCachedArchive != null) versionMutationOrchestrationService.deleteCachedArtifact(oldCachedArchive);
    }
}
