package net.modtale.controller.project;

import net.modtale.model.dto.project.ExternalProjectReferenceDTO;
import net.modtale.model.project.ProjectDependency;
import net.modtale.service.project.version.ExternalProjectReferenceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class ExternalProjectController {

    private final ExternalProjectReferenceService externalProjectReferenceService;

    public ExternalProjectController(ExternalProjectReferenceService externalProjectReferenceService) {
        this.externalProjectReferenceService = externalProjectReferenceService;
    }

    @GetMapping("/projects/external/resolve")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<ExternalProjectReferenceDTO> resolveExternalProject(
            @RequestParam String url,
            @RequestParam(required = false) ProjectDependency.Source source
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(externalProjectReferenceService.resolve(url, source));
    }
}
