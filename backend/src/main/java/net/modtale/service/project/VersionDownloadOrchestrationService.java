package net.modtale.service.project;

import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.exception.InvalidDownloadTokenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.dto.response.project.BundleDownloadUrlResponse;
import net.modtale.model.dto.response.project.DownloadUrlResponse;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.DownloadTokenService;
import net.modtale.service.storage.StorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class VersionDownloadOrchestrationService {

    private final ProjectVersionAccessService projectVersionAccessService;
    private final ProjectService projectService;
    private final DownloadService downloadService;
    private final DownloadTokenService downloadTokenService;
    private final TrackingService trackingService;
    private final StorageService storageService;
    private final String frontendUrl;

    public VersionDownloadOrchestrationService(
            ProjectVersionAccessService projectVersionAccessService,
            ProjectService projectService,
            DownloadService downloadService,
            DownloadTokenService downloadTokenService,
            TrackingService trackingService,
            StorageService storageService,
            AppFrontendProperties frontendProperties
    ) {
        this.projectVersionAccessService = projectVersionAccessService;
        this.projectService = projectService;
        this.downloadService = downloadService;
        this.downloadTokenService = downloadTokenService;
        this.trackingService = trackingService;
        this.storageService = storageService;
        this.frontendUrl = frontendProperties.url();
    }

    public DownloadUrlResponse createDownloadUrl(String projectId, String versionNumber, String gameVersion) {
        Project project = getProjectOrThrow(projectId,
                "We couldn't find that project, so no download link could be generated.");
        getVersionOrThrow(project, versionNumber, gameVersion,
                "We couldn't find the requested version for that project.");
        String token = downloadTokenService.generateToken(projectId, versionNumber, gameVersion);
        return new DownloadUrlResponse("/download/" + token, downloadTokenService.getTokenValiditySeconds());
    }

    public BundleDownloadUrlResponse createBundleDownloadUrl(
            String projectId,
            String versionNumber,
            String gameVersion,
            List<String> dependencies
    ) {
        Project project = getProjectOrThrow(projectId,
                "We couldn't find that project, so no bundle download link could be generated.");
        getVersionOrThrow(project, versionNumber, gameVersion,
                "We couldn't find the requested version for that bundle download.");
        String token = downloadTokenService.generateToken(projectId, versionNumber, gameVersion, dependencies);
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
        DownloadContext context = resolveDownloadContext(apiRole, referer, remoteAddress, forwardedFor, currentUser);
        DownloadTokenService.DownloadToken downloadToken = validateToken(token,
                "This download link is invalid, expired, or has already been used.");
        Project project = getRawProjectOrThrow(downloadToken.getProjectId(),
                "We couldn't find the project for this download link.");
        ProjectVersion targetVersion = getVersionOrThrow(project, downloadToken.getVersion(), downloadToken.getGameVersion(),
                "We couldn't find the version requested by this download link.");

        trackingService.logDownload(project.getId(), targetVersion.getId(), project.getAuthor(), context.apiRequest(), context.clientIp());

        if (project.getClassification() == ProjectClassification.MODPACK) {
            if (targetVersion.getDependencies() != null) {
                targetVersion.getDependencies().forEach(dep ->
                        trackingService.logDownload(dep.getModId(), null, null, context.apiRequest(), context.clientIp()));
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
        DownloadContext context = resolveDownloadContext(apiRole, referer, remoteAddress, forwardedFor, currentUser);
        DownloadTokenService.DownloadToken downloadToken = validateToken(token,
                "This bundle download link is invalid, expired, or has already been used.");
        Project project = getRawProjectOrThrow(downloadToken.getProjectId(),
                "We couldn't find the project for this bundle download link.");
        ProjectVersion targetVersion = getVersionOrThrow(project, downloadToken.getVersion(), downloadToken.getGameVersion(),
                "We couldn't find the version requested by this bundle download link.");

        trackingService.logDownload(project.getId(), targetVersion.getId(), project.getAuthor(), context.apiRequest(), context.clientIp());

        List<String> selectedDependencies = downloadToken.getSelectedDependencies();
        if (targetVersion.getDependencies() != null) {
            targetVersion.getDependencies().forEach(dep -> {
                if (dep.isEmbedded()) {
                    return;
                }
                if (selectedDependencies == null || selectedDependencies.contains(dep.getModId())) {
                    trackingService.logDownload(dep.getModId(), null, null, context.apiRequest(), context.clientIp());
                }
            });
        }

        byte[] zipData = downloadService.generateBundleZip(project, targetVersion, selectedDependencies, context.currentUser());
        return new VersionDownloadPayload(sanitizeProjectName(project.getTitle()) + "-UNZIP-ME.zip", zipData);
    }

    private DownloadContext resolveDownloadContext(
            boolean apiRole,
            String referer,
            String remoteAddress,
            String forwardedFor,
            User currentUser
    ) {
        boolean apiRequest = apiRole || referer == null || !referer.startsWith(frontendUrl);
        String clientIp = forwardedFor == null ? remoteAddress : forwardedFor.split(",")[0].trim();
        return new DownloadContext(apiRequest, clientIp, currentUser);
    }

    private DownloadTokenService.DownloadToken validateToken(String token, String failureMessage) {
        DownloadTokenService.DownloadToken downloadToken = downloadTokenService.validateAndConsume(token);
        if (downloadToken == null) {
            throw new InvalidDownloadTokenException(failureMessage);
        }
        return downloadToken;
    }

    private Project getProjectOrThrow(String projectId, String failureMessage) {
        Project project = projectService.getProjectById(projectId);
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
