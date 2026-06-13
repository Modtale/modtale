package net.modtale.service.project.version;

import java.util.List;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import org.springframework.stereotype.Service;

@Service
public class VersionUpdateCommandHandler {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final ProjectVersionAccessService projectVersionAccessService;
    private final VersionMutationOrchestrationService versionMutationOrchestrationService;

    public VersionUpdateCommandHandler(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            ProjectVersionAccessService projectVersionAccessService,
            VersionMutationOrchestrationService versionMutationOrchestrationService
    ) {
        this.projectRepository = projectRepository;
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

        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new VersionNotFoundException("We couldn't find that project version."));

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
            if (modpack) {
                versionMutationOrchestrationService.invalidateCachedModpackArtifact(version, resolvedProjectDependencies);
            }
            version.setDependencies(resolvedProjectDependencies);
            if (modpack && project.getVersions().get(0).getId().equals(versionId)) {
                project.setChildProjectIds(resolvedDependencies.simpleProjectIds());
            }
        }
        if (incompatibleProjectIds != null) {
            version.setIncompatibleProjectIds(new java.util.ArrayList<>(
                    versionMutationOrchestrationService.resolveRequestedProjectIds(incompatibleProjectIds, true)
            ));
        }

        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }
}
