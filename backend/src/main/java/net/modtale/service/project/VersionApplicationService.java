package net.modtale.service.project;

import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ManifestInspectionResult;
import net.modtale.model.dto.project.ProjectDependencyDTO;
import net.modtale.model.dto.request.project.CreateVersionRequest;
import net.modtale.model.dto.request.project.UpdateVersionRequest;
import net.modtale.model.dto.response.project.BundleDownloadUrlResponse;
import net.modtale.model.dto.response.project.DownloadUrlResponse;
import net.modtale.model.dto.response.project.VersionDependenciesView;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VersionApplicationService {

    private final VersionMutationApplicationService versionMutationApplicationService;
    private final VersionDownloadOrchestrationService versionDownloadOrchestrationService;
    private final ProjectVersionAccessService projectVersionAccessService;
    private final ProjectService projectService;

    public VersionApplicationService(
            VersionMutationApplicationService versionMutationApplicationService,
            VersionDownloadOrchestrationService versionDownloadOrchestrationService,
            ProjectVersionAccessService projectVersionAccessService,
            ProjectService projectService
    ) {
        this.versionMutationApplicationService = versionMutationApplicationService;
        this.versionDownloadOrchestrationService = versionDownloadOrchestrationService;
        this.projectVersionAccessService = projectVersionAccessService;
        this.projectService = projectService;
    }

    public VersionDependenciesView getDependencies(String projectId, String versionNumber, String gameVersion, User currentUser) {
        Project project = getProjectOrThrow(projectId, currentUser, "We couldn't find that project.");
        ProjectVersion version = getVersionOrThrow(project, versionNumber, gameVersion,
                "We couldn't find that version for the requested project.");
        List<ProjectDependencyDTO> dependencies = (version.getDependencies() != null ? version.getDependencies() : List.<ProjectDependency>of())
                .stream()
                .map(ProjectMapper::toDependencyDTO)
                .collect(Collectors.toList());
        return new VersionDependenciesView(dependencies);
    }

    public void addVersion(String projectId, CreateVersionRequest requestPayload, User currentUser) {
        versionMutationApplicationService.addVersion(projectId, requestPayload, currentUser);
    }

    public ManifestInspectionResult inspectManifest(String projectId, MultipartFile file, User currentUser) {
        return versionMutationApplicationService.inspectManifest(projectId, file, currentUser);
    }

    public void updateVersion(String projectId, String versionId, UpdateVersionRequest requestPayload, User currentUser) {
        versionMutationApplicationService.updateVersion(projectId, versionId, requestPayload, currentUser);
    }

    public void deleteVersion(String projectId, String versionId, User currentUser) {
        versionMutationApplicationService.deleteVersion(projectId, versionId, currentUser);
    }

    public DownloadUrlResponse createDownloadUrl(String projectId, String versionNumber, String gameVersion, User currentUser) {
        return versionDownloadOrchestrationService.createDownloadUrl(projectId, versionNumber, gameVersion, currentUser);
    }

    public BundleDownloadUrlResponse createBundleDownloadUrl(
            String projectId,
            String versionNumber,
            String gameVersion,
            List<String> dependencies,
            User currentUser
    ) {
        return versionDownloadOrchestrationService.createBundleDownloadUrl(
                projectId,
                versionNumber,
                gameVersion,
                dependencies,
                currentUser
        );
    }

    public VersionDownloadPayload downloadVersion(
            String token,
            boolean apiRole,
            String referer,
            String remoteAddress,
            String forwardedFor,
            User currentUser
    ) throws IOException {
        return versionDownloadOrchestrationService.downloadVersion(
                token,
                apiRole,
                referer,
                remoteAddress,
                forwardedFor,
                currentUser
        );
    }

    public VersionDownloadPayload downloadBundle(
            String token,
            boolean apiRole,
            String referer,
            String remoteAddress,
            String forwardedFor,
            User currentUser
    ) throws IOException {
        return versionDownloadOrchestrationService.downloadBundle(
                token,
                apiRole,
                referer,
                remoteAddress,
                forwardedFor,
                currentUser
        );
    }

    private Project getProjectOrThrow(String projectId, User currentUser, String failureMessage) {
        Project project = projectService.getProjectById(projectId, currentUser);
        if (project == null) {
            throw new ResourceNotFoundException(failureMessage);
        }
        return project;
    }

    private ProjectVersion getVersionOrThrow(Project project, String versionNumber, String gameVersion, String failureMessage) {
        return projectVersionAccessService.requireByVersionNumber(project, versionNumber, gameVersion,
                () -> new VersionNotFoundException(failureMessage));
    }

}
