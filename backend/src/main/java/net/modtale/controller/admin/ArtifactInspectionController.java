package net.modtale.controller.admin;

import net.modtale.model.project.*;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
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
    public ResponseEntity<List<String>> structure(@PathVariable String id,@PathVariable String version, @RequestHeader(value="If-Match", required=false) String expected) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inspect(id,version,null,expected).paths());
    }
    @GetMapping(value="/file",produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> file(@PathVariable String id,@PathVariable String version,@RequestParam String path, @RequestHeader(value="If-Match", required=false) String expected) {
        var response=inspect(id,version,path,expected);
        String prefix="JVM_BYTECODE".equals(response.format()) ? "// JVM bytecode of the uploaded class. LINE entries refer to original source lines.\n" : "";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(prefix+response.content());
    }
    public record FileChange(String path, String change) {}
    public record ArtifactChanges(String reviewToken, String baselineVersion, boolean contextComparable, boolean contextChanged,
            int added, int modified, int removed, int unchanged, List<FileChange> files) {}

    @GetMapping("/changes")
    public ResponseEntity<ArtifactChanges> changes(@PathVariable String id, @PathVariable String version, @RequestHeader(value="If-Match", required=false) String expected) {
        Project project=projects.getRawProjectById(id);
        if(project==null || project.getVersions()==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ProjectReviewSnapshot.requireCurrent(project, expected);
        String snapshot = ProjectReviewSnapshot.token(project);
        ProjectVersion current = requireVersion(project, version);
        ProjectVersion baseline=project.getVersions().stream().filter(Objects::nonNull).filter(v -> v.getReviewStatus()==ProjectVersion.ReviewStatus.APPROVED
                && !Objects.equals(v.getId(),current.getId()))
                .max(Comparator.comparingLong(ProjectVersion::getSecurityApprovedAt)).orElse(null);
        if(baseline==null) return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ArtifactChanges(snapshot,null,false,false,0,0,0,0,List.of()));
        requireVersion(project, baseline.getVersionNumber());
        var before=inspect(baseline,null);
        var after=inspect(current,null);
        requireUnchanged(id, snapshot);
        if(!validManifest(before.entryHashes()) || !validManifest(after.entryHashes()) || before.policyVersion()==null
                || !before.policyVersion().equals(after.policyVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A consistent artifact comparison is unavailable");
        String priorContext=baseline.getApprovedSecurityContextSha256();
        String currentContext=net.modtale.service.security.scan.ArtifactReviewContext.fingerprint(current);
        boolean comparable=priorContext!=null && currentContext!=null
                && priorContext.equals(net.modtale.service.security.scan.ArtifactReviewContext.fingerprint(baseline));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(compare(snapshot,baseline.getVersionNumber(),
                comparable, comparable && !priorContext.equals(currentContext),before.entryHashes(),after.entryHashes()));
    }
    private static boolean validManifest(Map<String,String> entries) {
        return net.modtale.model.project.SecurityManifest.valid(entries, false);
    }
    static ArtifactChanges compare(String snapshot, String baseline, boolean comparable, boolean contextChanged,
            Map<String,String> before, Map<String,String> after) {
        var paths=new TreeSet<String>(); paths.addAll(before.keySet()); paths.addAll(after.keySet());
        var changes=new ArrayList<FileChange>(); int added=0,modified=0,removed=0,unchanged=0;
        for(String path:paths) {
            String change;
            if(!before.containsKey(path)) {change="ADDED";added++;}
            else if(!after.containsKey(path)) {change="REMOVED";removed++;}
            else if(!Objects.equals(before.get(path),after.get(path))) {change="MODIFIED";modified++;}
            else {change="UNCHANGED";unchanged++;}
            changes.add(new FileChange(path,change));
        }
        return new ArtifactChanges(snapshot,baseline,comparable,contextChanged,added,modified,removed,unchanged,List.copyOf(changes));
    }
    private WardenClientService.InspectionResponse inspect(String id,String number,String path,String expected) {
        Project project=projects.getRawProjectById(id);
        if(project==null || project.getVersions()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ProjectReviewSnapshot.requireCurrent(project, expected);
        String snapshot = ProjectReviewSnapshot.token(project);
        var result = inspect(requireVersion(project, number), path);
        requireUnchanged(id, snapshot);
        return result;
    }
    private ProjectVersion requireVersion(Project project, String number) {
        var matches = project.getVersions().stream().filter(Objects::nonNull)
                .filter(v -> Objects.equals(v.getVersionNumber(), number)).toList();
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (matches.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Version identity is ambiguous");
        return matches.getFirst();
    }
    private void requireUnchanged(String id, String snapshot) {
        var current = projects.getRawProjectById(id);
        if (current == null || current.getVersions() == null) throw ProjectReviewSnapshot.conflict();
        ProjectReviewSnapshot.requireCurrent(current, snapshot);
    }
    private WardenClientService.InspectionResponse inspect(ProjectVersion version, String path) {
        if(version.getFileUrl()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        byte[] bytes=storage.download(version.getFileUrl());
        try {
            String actual=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!actual.equals(version.getHash())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stored artifact hash mismatch");
        } catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
        var response=inspector.inspectFile(bytes,"artifact.zip",path);
        if(response==null || !Objects.equals(version.getHash(),response.artifactSha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Artifact contents no longer match this version");
        }
        return response;
    }
}
