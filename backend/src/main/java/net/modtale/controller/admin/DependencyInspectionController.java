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
    @Autowired public DependencyInspectionController(ProjectService projects,MongoTemplate mongo) {
        this(projects,()->new DependencyReviewSource(mongo));
    }
    DependencyInspectionController(ProjectService projects,Supplier<DependencyReviewSource> sources) {
        this.projects=projects;this.sources=sources;
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
