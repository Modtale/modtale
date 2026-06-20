package net.modtale.service.project.version;

import java.io.IOException;
import java.util.List;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.exception.InvalidDownloadTokenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.dto.response.project.BundleDownloadUrlResponse;
import net.modtale.model.dto.response.project.DownloadUrlResponse;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.analytics.AnalyticsEligibilityService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.DownloadTokenService;
import net.modtale.service.storage.StorageService;
import org.springframework.stereotype.Service;

@Service
public class VersionDownloadOrchestrationService {

    private final ProjectVersionAccessService projectVersionAccessService;
    private final ProjectService projectService;
    private final DownloadService downloadService;
    private final DownloadTokenService downloadTokenService;
    private final AnalyticsEligibilityService analyticsEligibilityService;
    private final TrackingService trackingService;
    private final StorageService storageService;
    private final AccessControlService accessControlService;
    private final String frontendUrl;

    public VersionDownloadOrchestrationService(
            ProjectVersionAccessService projectVersionAccessService,
            ProjectService projectService,
            DownloadService downloadService,
            DownloadTokenService downloadTokenService,
            AnalyticsEligibilityService analyticsEligibilityService,
            TrackingService trackingService,
            StorageService storageService,
            AccessControlService accessControlService,
            AppFrontendProperties frontendProperties
    ) {
        this.projectVersionAccessService = projectVersionAccessService;
        this.projectService = projectService;
        this.downloadService = downloadService;
        this.downloadTokenService = downloadTokenService;
        this.analyticsEligibilityService = analyticsEligibilityService;
        this.trackingService = trackingService;
        this.storageService = storageService;
        this.accessControlService = accessControlService;
        this.frontendUrl = frontendProperties.url();
    }

    public DownloadUrlResponse createDownloadUrl(String projectId, String versionNumber, String gameVersion, User currentUser) {
        Project project = getProjectOrThrow(projectId, currentUser,
                "We couldn't find that project, so no download link could be generated.");
        getVersionOrThrow(project, versionNumber, gameVersion,
                "We couldn't find the requested version for that project.");
        String token = downloadTokenService.generateToken(projectId, versionNumber, gameVersion, null, currentUserId(currentUser));
        return new DownloadUrlResponse("/download/" + token, downloadTokenService.getTokenValiditySeconds());
    }

    public BundleDownloadUrlResponse createBundleDownloadUrl(
            String projectId,
            String versionNumber,
            String gameVersion,
            List<String> dependencies,
            User currentUser
    ) {
        Project project = getProjectOrThrow(projectId, currentUser,
                "We couldn't find that project, so no bundle download link could be generated.");
        getVersionOrThrow(project, versionNumber, gameVersion,
                "We couldn't find the requested version for that bundle download.");
        String token = downloadTokenService.generateToken(projectId, versionNumber, gameVersion, dependencies, currentUserId(currentUser));
        return new BundleDownloadUrlResponse("/download-bundle/" + token, downloadTokenService.getTokenValiditySeconds());
    }

    public VersionDownloadPayload downloadVersion(
            String token,
            boolean apiRole,
            String referer,
            String remoteAddress,
            String forwardedFor,
            User currentUser
    ) throws IOException {
        DownloadTokenService.DownloadToken downloadToken = validateToken(token,
                "This download link is invalid, expired, or has already been used.");
        DownloadContext context = resolveDownloadContext(downloadToken, apiRole, referer, remoteAddress, forwardedFor, currentUser);
        Project project = getRawProjectOrThrow(downloadToken.getProjectId(),
                "We couldn't find the project for this download link.");
        ensureReadable(project, context.currentUser());
        ProjectVersion targetVersion = getVersionOrThrow(project, downloadToken.getVersion(), downloadToken.getGameVersion(),
                "We couldn't find the version requested by this download link.");

        trackDownload(project, targetVersion.getId(), context);

        if (project.getClassification() == ProjectClassification.MODPACK) {
            if (targetVersion.getDependencies() != null) {
                targetVersion.getDependencies().forEach(dep -> trackDependencyDownload(dep, context));
            }
            byte[] zipData = downloadService.generateModpackZip(project, targetVersion, context.currentUser());
            return new VersionDownloadPayload(buildModpackFilename(project, targetVersion), zipData);
        }

        byte[] data = storageService.download(targetVersion.getFileUrl());
        return new VersionDownloadPayload(extractFilename(targetVersion.getFileUrl()), data);
    }

    public VersionDownloadPayload downloadBundle(
            String token,
            boolean apiRole,
            String referer,
            String remoteAddress,
            String forwardedFor,
            User currentUser
    ) throws IOException {
        DownloadTokenService.DownloadToken downloadToken = validateToken(token,
                "This bundle download link is invalid, expired, or has already been used.");
        DownloadContext context = resolveDownloadContext(downloadToken, apiRole, referer, remoteAddress, forwardedFor, currentUser);
        Project project = getRawProjectOrThrow(downloadToken.getProjectId(),
                "We couldn't find the project for this bundle download link.");
        ensureReadable(project, context.currentUser());
        ProjectVersion targetVersion = getVersionOrThrow(project, downloadToken.getVersion(), downloadToken.getGameVersion(),
                "We couldn't find the version requested by this bundle download link.");

        trackDownload(project, targetVersion.getId(), context);

        List<String> selectedDependencies = downloadToken.getSelectedDependencies();
        if (targetVersion.getDependencies() != null) {
            targetVersion.getDependencies().forEach(dep -> {
                if (dep.isExternal()) {
                    return;
                }
                if (dep.isEmbedded()) {
                    return;
                }
                if (selectedDependencies == null || selectedDependencies.contains(dep.getProjectId())) {
                    trackDependencyDownload(dep, context);
                }
            });
        }

        byte[] zipData = downloadService.generateBundleZip(project, targetVersion, selectedDependencies, context.currentUser());
        return new VersionDownloadPayload(sanitizeProjectName(project.getTitle()) + "-UNZIP-ME.zip", zipData);
    }

    private DownloadContext resolveDownloadContext(
            DownloadTokenService.DownloadToken downloadToken,
            boolean apiRole,
            String referer,
            String remoteAddress,
            String forwardedFor,
            User currentUser
    ) {
        User effectiveUser = requireTokenUser(downloadToken, currentUser);
        boolean apiRequest = apiRole || referer == null || !referer.startsWith(frontendUrl);
        String clientIp = forwardedFor == null ? remoteAddress : forwardedFor.split(",")[0].trim();
        return new DownloadContext(apiRequest, clientIp, effectiveUser);
    }

    private DownloadTokenService.DownloadToken validateToken(String token, String failureMessage) {
        DownloadTokenService.DownloadToken downloadToken = downloadTokenService.validateAndConsume(token);
        if (downloadToken == null) {
            throw new InvalidDownloadTokenException(failureMessage);
        }
        return downloadToken;
    }

    private User requireTokenUser(DownloadTokenService.DownloadToken downloadToken, User currentUser) {
        String tokenUserId = downloadToken.getUserId();
        if (tokenUserId == null || tokenUserId.isBlank()) {
            return currentUser;
        }

        if (currentUser == null || currentUser.getId() == null || !tokenUserId.equals(currentUser.getId())) {
            throw new UnauthorizedException("Sign in with the account that created this download link before using it.");
        }
        return currentUser;
    }

    private String currentUserId(User currentUser) {
        return currentUser == null ? null : currentUser.getId();
    }

    private Project getProjectOrThrow(String projectId, User currentUser, String failureMessage) {
        Project project = projectService.getProjectById(projectId, currentUser);
        if (project == null) {
            throw new ResourceNotFoundException(failureMessage);
        }
        return project;
    }

    private Project getRawProjectOrThrow(String projectId, String failureMessage) {
        Project project = projectService.getRawProjectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException(failureMessage);
        }
        return project;
    }

    private ProjectVersion getVersionOrThrow(Project project, String versionNumber, String gameVersion, String failureMessage) {
        return projectVersionAccessService.requireByVersionNumber(project, versionNumber, gameVersion,
                () -> new VersionNotFoundException(failureMessage));
    }

    private void ensureReadable(Project project, User currentUser) {
        if (!accessControlService.canReadProject(project, currentUser)) {
            throw new ResourceNotFoundException("We couldn't find the project for this download link.");
        }
    }

    private void trackDownload(Project project, String versionId, DownloadContext context) {
        if (analyticsEligibilityService.shouldCountProjectEngagement(project, context.currentUser())) {
            trackingService.logDownload(project.getId(), versionId, project.getAuthor(), context.apiRequest(), context.clientIp());
        }
    }

    private void trackDependencyDownload(ProjectDependency dependency, DownloadContext context) {
        if (dependency.isExternal()) {
            return;
        }

        Project dependencyProject = projectService.getRawProjectById(dependency.getProjectId());
        if (dependencyProject == null || analyticsEligibilityService.shouldCountProjectEngagement(dependencyProject, context.currentUser())) {
            trackingService.logDownload(
                    dependency.getProjectId(),
                    null,
                    dependencyProject != null ? dependencyProject.getAuthor() : null,
                    context.apiRequest(),
                    context.clientIp()
            );
        }
    }

    private String buildModpackFilename(Project project, ProjectVersion version) {
        return sanitizeProjectName(project.getTitle()) + "-" + version.getVersionNumber() + ".zip";
    }

    private String sanitizeProjectName(String title) {
        return title.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private String extractFilename(String fileUrl) {
        String filename = fileUrl != null && fileUrl.contains("/")
                ? fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
                : "download";
        if (filename.length() > 37 && filename.charAt(36) == '-') {
            return filename.substring(37);
        }
        return filename;
    }

    private record DownloadContext(boolean apiRequest, String clientIp, User currentUser) {
    }
}
