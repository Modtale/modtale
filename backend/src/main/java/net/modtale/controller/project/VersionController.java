package net.modtale.controller.project;

import net.modtale.model.dto.ManifestDependencySuggestion;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.*;
import net.modtale.service.storage.DownloadTokenService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.StorageService;
import net.modtale.service.user.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class VersionController {

    private static final Logger logger = LoggerFactory.getLogger(VersionController.class);

    @Autowired private VersionService versionService;
    @Autowired private ProjectService projectService;
    @Autowired private DownloadService downloadService;
    @Autowired private DownloadTokenService downloadTokenService;
    @Autowired private TrackingService trackingService;
    @Autowired private StorageService storageService;
    @Autowired private AccountService accountService;

    @Value("${app.frontend.url}") private String frontendUrl;

    @GetMapping("/projects/{id}/versions/{version}/dependencies")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_READ', authentication)")
    public ResponseEntity<List<ProjectDependency>> getDependencies(@PathVariable String id, @PathVariable String version) {
        Project project = projectService.getProjectById(id);
        if (project == null) return ResponseEntity.notFound().build();
        ProjectVersion v = project.getVersions().stream().filter(ver -> ver.getVersionNumber().equalsIgnoreCase(version)).findFirst().orElse(null);
        if (v == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(v.getDependencies() != null ? v.getDependencies() : List.of());
    }

    @PostMapping("/projects/{id}/versions")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_CREATE', authentication)")
    public ResponseEntity<?> addVersion(
            @PathVariable String id, @RequestParam("versionNumber") String versionNumber,
            @RequestParam(value = "gameVersions", required = false) List<String> gameVersions,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "modIds", required = false) List<String> projectIds,
            @RequestParam(value = "changelog", required = false) String changelog,
            @RequestParam(value = "channel", required = false, defaultValue = "RELEASE") String channel
    ) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            if (projectIds != null && projectIds.size() == 1 && projectIds.get(0).contains(",")) projectIds = Arrays.stream(projectIds.get(0).split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            versionService.addVersion(id, versionNumber, gameVersions, file, changelog, projectIds, ProjectVersion.Channel.valueOf(channel.toUpperCase()), user);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        catch (Exception e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/versions/dependency-suggestions")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_CREATE', authentication)")
    public ResponseEntity<?> suggestManifestDependencies(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            List<ManifestDependencySuggestion> suggestions = versionService.suggestManifestDependencies(id, file, user);
            return ResponseEntity.ok(suggestions);
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        catch (SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
        catch (Exception e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    @PutMapping("/projects/{id}/versions/{versionId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_EDIT', authentication)")
    public ResponseEntity<?> updateVersion(@PathVariable String id, @PathVariable String versionId, @RequestBody Map<String, Object> body) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            List<String> projectIds = (List<String>) body.get("modIds");
            List<String> gameVersions = (List<String>) body.get("gameVersions");
            String channelStr = (String) body.get("channel");

            ProjectVersion.Channel channel = channelStr != null ? ProjectVersion.Channel.valueOf(channelStr) : null;

            versionService.updateVersion(id, versionId, projectIds, gameVersions, (String) body.get("changelog"), channel, user);
            return ResponseEntity.ok().build();
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @DeleteMapping("/projects/{id}/versions/{versionId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_DELETE', authentication)")
    public ResponseEntity<?> deleteVersion(@PathVariable String id, @PathVariable String versionId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { versionService.deleteVersion(id, versionId, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @GetMapping("/projects/{id}/versions/{version}/download-url")
    public ResponseEntity<?> getDownloadUrl(@PathVariable String id, @PathVariable String version) {
        try {
            Project project = projectService.getProjectById(id);
            if (project == null) return ResponseEntity.notFound().build();
            if (project.getVersions().stream().noneMatch(v -> v.getVersionNumber().equalsIgnoreCase(version))) return ResponseEntity.notFound().build();
            String token = downloadTokenService.generateToken(id, version);
            return ResponseEntity.ok(Map.of("downloadUrl", "/download/" + token, "expiresIn", 300));
        } catch (Exception e) { return ResponseEntity.status(500).build(); }
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> downloadWithToken(@PathVariable String token, HttpServletRequest request) {
        try {
            DownloadTokenService.DownloadToken dt = downloadTokenService.validateAndConsume(token);
            if (dt == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

            Project project = projectService.getRawProjectById(dt.getProjectId());
            if (project == null) return ResponseEntity.notFound().build();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isApi = (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API"))) || (request.getHeader("Referer") == null || !request.getHeader("Referer").startsWith(frontendUrl));
            String clientIp = request.getHeader("X-Forwarded-For") == null ? request.getRemoteAddr() : request.getHeader("X-Forwarded-For").split(",")[0];

            ProjectVersion targetVersion = project.getVersions().stream().filter(v -> v.getVersionNumber().equalsIgnoreCase(dt.getVersion())).findFirst().orElse(null);
            if (targetVersion == null) return ResponseEntity.notFound().build();

            trackingService.logDownload(project.getId(), targetVersion.getId(), project.getAuthor(), isApi, clientIp);

            User user = accountService.getCurrentUser();

            if (project.getClassification() != null && "MODPACK".equals(project.getClassification().name())) {
                if (targetVersion.getDependencies() != null) {
                    targetVersion.getDependencies().forEach(dep -> trackingService.logDownload(dep.getModId(), null, null, isApi, clientIp));
                }
                byte[] zipData = downloadService.generateModpackZip(project, targetVersion, user);
                String zipFilename = project.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + "-" + targetVersion.getVersionNumber() + ".zip";
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(new ByteArrayResource(zipData));
            }

            byte[] data = storageService.download(targetVersion.getFileUrl());

            String filename = targetVersion.getFileUrl() != null && targetVersion.getFileUrl().contains("/")
                    ? targetVersion.getFileUrl().substring(targetVersion.getFileUrl().lastIndexOf('/') + 1)
                    : "download";

            if (filename.length() > 37 && filename.charAt(36) == '-') filename = filename.substring(37);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(data));

        } catch (Exception e) {
            logger.error("Error processing download", e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/projects/{id}/versions/{version}/download-bundle-url")
    public ResponseEntity<?> getDownloadBundleUrl(
            @PathVariable String id,
            @PathVariable String version,
            @RequestParam(value = "deps", required = false) List<String> deps) {
        try {
            Project project = projectService.getProjectById(id);
            if (project == null) return ResponseEntity.notFound().build();
            if (project.getVersions().stream().noneMatch(v -> v.getVersionNumber().equalsIgnoreCase(version))) return ResponseEntity.notFound().build();
            String token = downloadTokenService.generateToken(id, version, deps);
            return ResponseEntity.ok(Map.of(
                    "downloadUrl", "/download-bundle/" + token,
                    "expiresIn", 300
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/download-bundle/{token}")
    public ResponseEntity<Resource> downloadBundleWithToken(@PathVariable String token, HttpServletRequest request) {
        try {
            DownloadTokenService.DownloadToken dt = downloadTokenService.validateAndConsume(token);
            if (dt == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

            Project project = projectService.getRawProjectById(dt.getProjectId());
            if (project == null) return ResponseEntity.notFound().build();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isApi = (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API"))) || (request.getHeader("Referer") == null || !request.getHeader("Referer").startsWith(frontendUrl));
            String clientIp = request.getHeader("X-Forwarded-For") == null ? request.getRemoteAddr() : request.getHeader("X-Forwarded-For").split(",")[0];

            ProjectVersion targetVersion = project.getVersions().stream()
                    .filter(v -> v.getVersionNumber().equalsIgnoreCase(dt.getVersion()))
                    .findFirst().orElse(null);

            if (targetVersion == null) return ResponseEntity.notFound().build();

            trackingService.logDownload(project.getId(), targetVersion.getId(), project.getAuthor(), isApi, clientIp);

            List<String> selectedDeps = dt.getSelectedDependencies();
            if (targetVersion.getDependencies() != null) {
                targetVersion.getDependencies().forEach(dep -> {
                    if (selectedDeps == null || selectedDeps.contains(dep.getModId())) {
                        trackingService.logDownload(dep.getModId(), null, null, isApi, clientIp);
                    }
                });
            }

            User user = accountService.getCurrentUser();
            byte[] zipData = downloadService.generateBundleZip(project, targetVersion, selectedDeps, user);
            String zipFilename = project.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + "-UNZIP-ME.zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(zipData));

        } catch (Exception e) {
            logger.error("Error processing bundle download", e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/version/{hash}")
    @PreAuthorize("@apiSecurity.hasAnyPerm('VERSION_READ', authentication)")
    public ResponseEntity<?> getVersionByHash(@PathVariable String hash) {
        Optional<ProjectVersion> v = versionService.getVersionByHash(hash);
        return v.isPresent() ? ResponseEntity.ok(v.get()) : ResponseEntity.notFound().build();
    }
}
