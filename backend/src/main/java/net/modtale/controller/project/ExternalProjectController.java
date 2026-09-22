package net.modtale.controller.project;

import net.modtale.model.dto.project.ExternalProjectReferenceDTO;
import net.modtale.model.dto.project.CurseForgeCatalogDTO;
import net.modtale.model.dto.project.ArtifactIdentityDTO;
import net.modtale.model.project.ProjectDependency;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.service.project.version.CurseForgeApiClient;
import net.modtale.service.project.version.ExternalProjectReferenceService;
import net.modtale.service.project.version.ArtifactIdentityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ExternalProjectController {

    private final ExternalProjectReferenceService externalProjectReferenceService;
    private final CurseForgeApiClient curseForgeApiClient;
    private final ArtifactIdentityService artifactIdentityService;

    public ExternalProjectController(
            ExternalProjectReferenceService externalProjectReferenceService,
            CurseForgeApiClient curseForgeApiClient,
            ArtifactIdentityService artifactIdentityService
    ) {
        this.externalProjectReferenceService = externalProjectReferenceService;
        this.curseForgeApiClient = curseForgeApiClient;
        this.artifactIdentityService = artifactIdentityService;
    }

    @GetMapping("/projects/external/resolve")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<ExternalProjectReferenceDTO> resolveExternalProject(
            @RequestParam String url,
            @RequestParam(required = false) ProjectDependency.Source source
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(externalProjectReferenceService.resolve(url, source));
    }

    @GetMapping("/projects/external/curseforge")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<CurseForgeCatalogDTO.Page> browseCurseForge(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String gameVersion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "downloads") String sort
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CurseForgeCatalogDTO.Page.from(artifactIdentityService.removeModtaleAliases(
                        curseForgeApiClient.searchMods(search, gameVersion, page, size, sort))));
    }

    @PostMapping("/projects/external/identify")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<ArtifactIdentityDTO.Response> identifyArtifacts(@Valid @RequestBody ArtifactIdentityDTO.Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(artifactIdentityService.identify(request));
    }

    @GetMapping("/projects/external/curseforge/{projectId}")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<CurseForgeCatalogDTO.Project> getCurseForgeProject(
            @org.springframework.web.bind.annotation.PathVariable long projectId
    ) {
        CurseForgeApiClient.CurseForgeProject project = curseForgeApiClient.getProject(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("CurseForge project was not found."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CurseForgeCatalogDTO.Project.from(project));
    }

    @GetMapping("/projects/external/curseforge/{projectId}/files/{fileId}/download-url")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<CurseForgeCatalogDTO.Download> getCurseForgeDownload(
            @org.springframework.web.bind.annotation.PathVariable long projectId,
            @org.springframework.web.bind.annotation.PathVariable long fileId
    ) {
        CurseForgeApiClient.CurseForgeDownload download = curseForgeApiClient.getDownload(projectId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("This exact CurseForge file is unavailable."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CurseForgeCatalogDTO.Download.from(download));
    }
}
