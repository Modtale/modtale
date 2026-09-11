package net.modtale.controller.admin;

import net.modtale.model.project.*;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.WardenClientService;
import net.modtale.service.storage.StorageService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/projects/{id}/versions/{version}")
@PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
public class ArtifactInspectionController {
    private final ProjectService projects;
    private final StorageService storage;
    private final WardenClientService inspector;
    public ArtifactInspectionController(ProjectService projects, StorageService storage, WardenClientService inspector) {
        this.projects=projects;this.storage=storage;this.inspector=inspector;
    }
    @GetMapping("/structure")
    public ResponseEntity<List<String>> structure(@PathVariable String id,@PathVariable String version) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inspect(id,version,null).paths());
    }
    @GetMapping(value="/file",produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> file(@PathVariable String id,@PathVariable String version,@RequestParam String path) {
        var response=inspect(id,version,path);
        String prefix="JVM_BYTECODE".equals(response.format()) ? "// JVM bytecode of the uploaded class. LINE entries refer to original source lines.\n" : "";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(prefix+response.content());
    }
    private WardenClientService.InspectionResponse inspect(String id,String number,String path) {
        Project project=projects.getRawProjectById(id);
        if(project==null || project.getVersions()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ProjectVersion version=project.getVersions().stream().filter(v->Objects.equals(v.getVersionNumber(),number)).findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(version.getFileUrl()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        byte[] bytes=storage.download(version.getFileUrl());
        var response=inspector.inspectFile(bytes,"artifact.zip",path);
        if(response==null || !Objects.equals(version.getHash(),response.artifactSha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Artifact contents no longer match this version");
        }
        return response;
    }
}
