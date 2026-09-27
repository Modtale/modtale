package net.modtale.controller.admin;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
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
import java.util.function.Function;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/admin/projects/{id}/version-ids/{versionId}")
@PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
public class DependencyInspectionController {
    private final ProjectService projects;
    private final Supplier<DependencyReviewSource> sources;
    private final DependencyArtifactVerifier verifier;
    private final ModpackOverrideInspector overrideInspector;
    @Autowired public DependencyInspectionController(ProjectService projects,MongoTemplate mongo,net.modtale.service.storage.StorageService storage) {
        this(projects,()->new DependencyReviewSource(mongo),new DependencyArtifactVerifier(storage),new ModpackOverrideInspector(storage));
    }
    DependencyInspectionController(ProjectService projects,Supplier<DependencyReviewSource> sources,DependencyArtifactVerifier verifier,
            ModpackOverrideInspector overrideInspector) {
        this.projects=projects;this.sources=sources;this.verifier=verifier;this.overrideInspector=overrideInspector;
    }
    public record OverrideInspection(String reviewToken,String artifactSha256,ModpackOverrideInspector.Result observation) {}
    @GetMapping("/override-contents")
    public ResponseEntity<OverrideInspection> inspectOverrideContents(@PathVariable String id,@PathVariable String versionId,
            @RequestParam String artifactSha256,@RequestHeader(value="If-Match",required=false) String expected) {
        var checked=readOverride(id,versionId,artifactSha256,expected,version->overrideInspector.inspect(
                version.getOverrideFileUrl(),artifactSha256,version.getModpackConfigs(),version.getDependencies()));
        return overrideResponse(checked,artifactSha256);
    }
    @GetMapping("/override-config-window")
    public ResponseEntity<OverrideInspection> inspectOverrideConfigWindow(@PathVariable String id,@PathVariable String versionId,
            @RequestParam String artifactSha256,@RequestParam String path,@RequestParam(defaultValue="0") int offset,
            @RequestParam(defaultValue="32768") int characters,@RequestHeader(value="If-Match",required=false) String expected) {
        if(path==null||path.isBlank()||path.length()>2048||offset<0||characters<1||characters>32768)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid config window request");
        var checked=readOverride(id,versionId,artifactSha256,expected,version->{
            if(version.getModpackConfigs()==null||version.getModpackConfigs().stream()
                    .noneMatch(config->config!=null&&path.equals(config.path())))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Config is not attached to this version");
            return overrideInspector.inspectWindow(version.getOverrideFileUrl(),artifactSha256,
                    version.getModpackConfigs(),version.getDependencies(),path,offset,characters);
        });
        return overrideResponse(checked,artifactSha256);
    }
    private static ResponseEntity<OverrideInspection> overrideResponse(CheckedOverride<ModpackOverrideInspector.Result> checked,
            String artifactSha256) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .body(new OverrideInspection(checked.reviewToken(),artifactSha256,checked.observation()));
    }
    private record CheckedOverride<T>(String reviewToken,T observation) {}
    private <T> CheckedOverride<T> readOverride(String id,String versionId,String artifactSha256,String expected,
            Function<ProjectVersion,T> read) {
        if(artifactSha256==null||!artifactSha256.matches("[0-9a-f]{64}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid uploaded artifact identity");
        var before=inspect(id,versionId,expected).getBody();
        var project=projects.getRawProjectById(id);
        if(project==null||project.getClassification()!=ProjectClassification.MODPACK||project.getVersions()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No modpack override is available");
        ProjectReviewSnapshot.requireCurrent(project,before.reviewToken());
        var versions=project.getVersions().stream().filter(Objects::nonNull).filter(v->versionId.equals(v.getId())).toList();
        if(versions.size()!=1)throw ProjectReviewSnapshot.conflict();
        var version=versions.getFirst();
        if(version.getOverrideFileUrl()==null||version.getOverrideFileUrl().isBlank()
                ||!artifactSha256.equals(version.getHash())
                ||version.getModpackConfigs()!=null&&version.getModpackConfigs().size()>100
                ||version.getDependencies()!=null&&version.getDependencies().size()>256
                ||before.inventory().gaps().stream().anyMatch(gap->gap.reason()==DependencyReviewGraph.Reason.CHANGED))
            throw ProjectReviewSnapshot.conflict();
        var observation=read.apply(version);
        var after=inspect(id,versionId,expected).getBody();
        if(observation==null||!before.reviewToken().equals(after.reviewToken())
                ||!before.inventory().root().equals(after.inventory().root())
                ||!before.inventory().nodes().equals(after.inventory().nodes())
                ||!before.inventory().edges().equals(after.inventory().edges())
                ||!before.inventory().gaps().equals(after.inventory().gaps()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Modpack override changed during inspection");
        return new CheckedOverride<>(after.reviewToken(),observation);
    }
    public record ByteInspection(String reviewToken,String inventoryIdentity,DependencyArtifactVerifier.Result verification) {}
    public record RootByteInspection(String reviewToken,String artifactSha256,DependencyArtifactVerifier.Result verification) {}
    @GetMapping("/root-bytes")
    public ResponseEntity<RootByteInspection> verifyRootBytes(@PathVariable String id,@PathVariable String versionId,
            @RequestParam String artifactSha256,@RequestHeader(value="If-Match",required=false) String expected) {
        if(artifactSha256==null||!artifactSha256.matches("[0-9a-f]{64}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid uploaded artifact identity");
        var before=inspect(id,versionId,expected).getBody();
        var root=before.inventory().nodes().stream().filter(node->node.projectId().equals(before.inventory().root().projectId())
                &&node.versionId().equals(before.inventory().root().versionId())).findFirst()
                .orElseThrow(ProjectReviewSnapshot::conflict);
        if(!artifactSha256.equals(root.artifactSha256())||before.inventory().gaps().stream()
                .anyMatch(gap->gap.reason()==DependencyReviewGraph.Reason.CHANGED))
            throw ProjectReviewSnapshot.conflict();
        var verification=verifier.verifyRoot(root);
        var after=inspect(id,versionId,expected).getBody();
        if(!Objects.equals(before.reviewToken(),after.reviewToken())
                ||!before.inventory().root().equals(after.inventory().root())
                ||!before.inventory().nodes().equals(after.inventory().nodes())
                ||!before.inventory().edges().equals(after.inventory().edges())
                ||!before.inventory().gaps().equals(after.inventory().gaps())
                ||verification==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Uploaded artifact changed during byte verification");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .body(new RootByteInspection(after.reviewToken(),artifactSha256,verification));
    }
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
    public record Inspection(String reviewToken,boolean artifactBytesVerified,boolean modpackOverrideAvailable,
            DependencyReviewGraph.Inventory inventory) {}
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
                ||!Objects.equals(ArtifactReviewContext.inspectionFileReference(version,project.getClassification()),root.fileReference())
                ||!Objects.equals(ArtifactReviewContext.fingerprint(version),root.contextSha256())
                ||ArtifactReviewContext.hasSupplementalContent(version)!=root.supplementalContent()
                ||!sameDeclarations(version,root))throw ProjectReviewSnapshot.conflict();
        var inventory=DependencyReviewGraph.inspect(root,source,DependencyReviewGraph.Limits.defaults());
        Project current=projects.getRawProjectById(id);
        if(current==null||current.getVersions()==null)throw ProjectReviewSnapshot.conflict();
        ProjectReviewSnapshot.requireCurrent(current,token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .body(new Inspection(token,false,project.getClassification()==ProjectClassification.MODPACK
                        &&version.getOverrideFileUrl()!=null&&!version.getOverrideFileUrl().isBlank(),inventory));
    }
}
