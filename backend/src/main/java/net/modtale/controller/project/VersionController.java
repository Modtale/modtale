package net.modtale.controller.project;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import net.modtale.model.dto.project.ManifestInspectionResult;
import net.modtale.model.dto.request.project.CreateVersionRequest;
import net.modtale.model.dto.request.project.UpdateVersionRequest;
import net.modtale.model.dto.response.project.BundleDownloadUrlResponse;
import net.modtale.model.dto.response.project.DownloadUrlResponse;
import net.modtale.model.dto.response.project.VersionDependenciesView;
import net.modtale.model.user.User;
import net.modtale.service.project.version.VersionApplicationService;
import net.modtale.service.project.version.VersionDownloadPayload;
import net.modtale.service.user.account.AccountService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class VersionController {

    private final VersionApplicationService versionApplicationService;
    private final AccountService accountService;

    public VersionController(VersionApplicationService versionApplicationService, AccountService accountService) {
        this.versionApplicationService = versionApplicationService;
        this.accountService = accountService;
    }

    @GetMapping("/projects/{id}/versions/{version}/dependencies")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<VersionDependenciesView> getDependencies(
            @PathVariable String id,
            @PathVariable String version,
            @RequestParam(value = "gameVersion", required = false) String gameVersion,
            Authentication authentication
    ) {
        return ResponseEntity.ok(versionApplicationService.getDependencies(
                id,
                version,
                gameVersion,
                accountService.getCurrentUser(authentication)
        ));
    }

    @PostMapping("/projects/{id}/versions")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_CREATE', authentication)")
    public ResponseEntity<Void> addVersion(
            @PathVariable String id,
            @Valid @ModelAttribute CreateVersionRequest requestPayload,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "uploading a project version");
        versionApplicationService.addVersion(id, requestPayload, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/versions/dependency-suggestions")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_CREATE', authentication)")
    public ResponseEntity<ManifestInspectionResult> suggestManifestDependencies(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "inspecting version dependencies");
        return ResponseEntity.ok(versionApplicationService.inspectManifest(id, file, user));
    }

    @PutMapping("/projects/{id}/versions/{versionId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_EDIT', authentication)")
    public ResponseEntity<Void> updateVersion(
            @PathVariable String id,
            @PathVariable String versionId,
            @Valid @RequestBody UpdateVersionRequest requestPayload,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "updating a project version");
        versionApplicationService.updateVersion(id, versionId, requestPayload, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{id}/versions/{versionId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'VERSION_DELETE', authentication)")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable String id,
            @PathVariable String versionId,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "deleting a project version");
        versionApplicationService.deleteVersion(id, versionId, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/projects/{id}/versions/{version}/download-url")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable String id,
            @PathVariable String version,
            @RequestParam(value = "gameVersion", required = false) String gameVersion,
            Authentication authentication
    ) {
        return ResponseEntity.ok(versionApplicationService.createDownloadUrl(
                id,
                version,
                gameVersion,
                accountService.getCurrentUser(authentication)
        ));
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> downloadWithToken(
            @PathVariable String token,
            Authentication authentication,
            HttpServletRequest request
    ) throws IOException {
        VersionDownloadPayload payload = versionApplicationService.downloadVersion(
                token,
                hasApiRole(authentication),
                request.getHeader("Referer"),
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                accountService.getCurrentUser(authentication)
        );
        return asDownloadResponse(payload);
    }

    @GetMapping("/projects/{id}/versions/{version}/download-bundle-url")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<BundleDownloadUrlResponse> getDownloadBundleUrl(
            @PathVariable String id,
            @PathVariable String version,
            @RequestParam(value = "gameVersion", required = false) String gameVersion,
            @RequestParam(value = "deps", required = false) List<String> deps,
            Authentication authentication
    ) {
        return ResponseEntity.ok(versionApplicationService.createBundleDownloadUrl(
                id,
                version,
                gameVersion,
                deps,
                accountService.getCurrentUser(authentication)
        ));
    }

    @GetMapping("/download-bundle/{token}")
    public ResponseEntity<Resource> downloadBundleWithToken(
            @PathVariable String token,
            Authentication authentication,
            HttpServletRequest request
    ) throws IOException {
        VersionDownloadPayload payload = versionApplicationService.downloadBundle(
                token,
                hasApiRole(authentication),
                request.getHeader("Referer"),
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                accountService.getCurrentUser(authentication)
        );
        return asDownloadResponse(payload);
    }

    private ResponseEntity<Resource> asDownloadResponse(VersionDownloadPayload payload) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.filename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(payload.bytes()));
    }

    private boolean hasApiRole(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_API"));
    }
}
