package net.modtale.controller.admin;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/admin/projects/{id}/version-ids/{versionId}")
@PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
public class DependencyInspectionController {
    private final ProjectService projects;
    private final Supplier<DependencyReviewSource> sources;
    private final DependencyArtifactVerifier verifier;
    @Autowired public DependencyInspectionController(ProjectService projects,MongoTemplate mongo,net.modtale.service.storage.StorageService storage) {
        this(projects,()->new DependencyReviewSource(mongo),new DependencyArtifactVerifier(storage));
    }
    DependencyInspectionController(ProjectService projects,Supplier<DependencyReviewSource> sources,DependencyArtifactVerifier verifier) {
        this.projects=projects;this.sources=sources;this.verifier=verifier;
    }
    public record ByteInspection(String reviewToken,String inventoryIdentity,DependencyArtifactVerifier.Result verification) {}
    @GetMapping("/dependency-bytes")
    public ResponseEntity<ByteInspection> verifyBytes(@PathVariable String id,@PathVariable String versionId,
            @RequestParam String inventoryIdentity,@RequestHeader(value="If-Match",required=false) String expected) {
        if(inventoryIdentity==null||!inventoryIdentity.matches("[0-9a-f]{64}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid dependency inventory identity");
        var before=inspect(id,versionId,expected).getBody();
        if(!before.inventory().resolved()||!inventoryIdentity.equals(before.inventory().identity()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Dependency inventory changed; inspect it again");
        var verification=verifier.verify(before.inventory());
        // Use a fresh database budget and observations after storage reads, never the pre-read cache.
        var after=inspect(id,versionId,expected).getBody();
        if(!after.inventory().resolved()||!inventoryIdentity.equals(after.inventory().identity())
                ||verification==null||!inventoryIdentity.equals(verification.inventoryIdentity()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Dependency inventory changed during byte verification");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .body(new ByteInspection(after.reviewToken(),inventoryIdentity,verification));
    }
    private static boolean sameDeclarations(ProjectVersion version,DependencyReviewGraph.Snapshot root) {
        try {
            var declarations=new ArrayList<DependencyReviewGraph.Dependency>();
            if(version.getDependencies()!=null)for(var d:version.getDependencies())
                declarations.add(new DependencyReviewGraph.Dependency(d.getSource(),d.getDependencyType(),
                        new DependencyReviewGraph.Reference(d.getProjectId(),d.getVersionNumber())));
            return declarations.equals(root.dependencies());
        } catch(RuntimeException invalid) {return false;}
    }
    public record Inspection(String reviewToken,boolean artifactBytesVerified,DependencyReviewGraph.Inventory inventory) {}
    @GetMapping("/dependencies")
    public ResponseEntity<Inspection> inspect(@PathVariable String id,@PathVariable String versionId,
            @RequestHeader(value="If-Match",required=false) String expected) {
        try {new DependencyReviewGraph.Reference(id,versionId);}
        catch(IllegalArgumentException invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid dependency inspection identity");}
        Project project=projects.getRawProjectById(id);
        if(project==null||project.getVersions()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ProjectReviewSnapshot.requireCurrent(project,expected);
        String token=ProjectReviewSnapshot.token(project);
        var matches=project.getVersions().stream().filter(Objects::nonNull).filter(v->versionId.equals(v.getId())).toList();
        if(matches.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(matches.size()!=1)throw ProjectReviewSnapshot.conflict();
        ProjectVersion version=matches.getFirst();
        var source=sources.get();var selected=source.readRoot(id,versionId);
        if(selected.state()!=DependencyReviewGraph.State.FOUND)throw new ResponseStatusException(HttpStatus.CONFLICT,"Dependency root is unavailable or ambiguous");
        var root=selected.snapshot();
        if(!id.equals(root.projectId())||!versionId.equals(root.versionId())
                ||!Objects.equals(version.getVersionNumber(),root.versionNumber())||!Objects.equals(version.getHash(),root.artifactSha256())
                ||!Objects.equals(version.getFileUrl(),root.fileReference())
                ||!Objects.equals(ArtifactReviewContext.fingerprint(version),root.contextSha256())
                ||ArtifactReviewContext.hasSupplementalContent(version)!=root.supplementalContent()
                ||!sameDeclarations(version,root))throw ProjectReviewSnapshot.conflict();
        var inventory=DependencyReviewGraph.inspect(root,source,DependencyReviewGraph.Limits.defaults());
        Project current=projects.getRawProjectById(id);
        if(current==null||current.getVersions()==null)throw ProjectReviewSnapshot.conflict();
        ProjectReviewSnapshot.requireCurrent(current,token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .body(new Inspection(token,false,inventory));
    }
}
