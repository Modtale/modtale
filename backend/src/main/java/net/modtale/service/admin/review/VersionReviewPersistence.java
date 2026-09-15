package net.modtale.service.admin.review;

import com.mongodb.client.model.Filters;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VersionReviewPersistence {
    private final MongoTemplate mongo;
    public VersionReviewPersistence(MongoTemplate mongo) {this.mongo=mongo;}
    public record Snapshot(Object projectId, Document version) {}
    public Snapshot capture(String projectId, String versionId, String expectedToken) {
        return capture(projectId, versionId, expectedToken, false);
    }
    public Snapshot captureForRescan(String projectId, String versionId, String expectedToken) {
        return capture(projectId, versionId, expectedToken, true);
    }
    private Snapshot capture(String projectId, String versionId, String expectedToken, boolean rescan) {
        var entity=mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var filter=new QueryMapper(mongo.getConverter()).getMappedObject(new Document("_id",projectId),entity);
        Document project=mongo.getCollection(mongo.getCollectionName(Project.class)).withReadPreference(com.mongodb.ReadPreference.primary())
                .withReadConcern(com.mongodb.ReadConcern.MAJORITY).find(filter)
                .collation(com.mongodb.client.model.Collation.builder().locale("simple").build()).maxTime(5,java.util.concurrent.TimeUnit.SECONDS).first();
        if(versionId==null || project==null || !(project.get("versions") instanceof List<?> versions)) throw conflict();
        var matches=versions.stream().filter(item->item instanceof Document stored && versionId.equals(stored.get("_id"))).toList();
        if(matches.size()!=1)throw conflict();
        var stored=(Document)matches.getFirst();
        ProjectVersion version;
        try {version=mongo.getConverter().read(ProjectVersion.class,stored);}catch(RuntimeException invalid){throw conflict();}
        if (rescan) {
            if (expectedToken == null || !expectedToken.equals(VersionReviewSnapshot.rescanToken(version))) throw conflict();
        } else VersionReviewSnapshot.requireCurrent(version,expectedToken);
        return new Snapshot(project.get("_id"),stored);
    }
    public boolean apply(Snapshot snapshot, ProjectVersion reviewed) {
        var originGuard = new org.springframework.data.mongodb.core.query.Query();
        var originalVersion = mongo.getConverter().read(ProjectVersion.class, snapshot.version());
        if (reviewed.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED)
            net.modtale.service.security.issue.FindingReviewHistory.requireManualApproval(mongo, snapshot.projectId().toString(), originalVersion);
        reviewed.setApprovedFindingReviewHead(reviewed.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED
                ? originalVersion.getFindingReviewHead() : null);
        var original = originalVersion.getScanResult();
        if (reviewed.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED && original != null && original.getReusedReviewVersion() != null
                && (!net.modtale.service.security.scan.ArtifactClearancePolicy.complete(original)
                || !net.modtale.service.security.scan.ArtifactReviewLineage.bind(mongo, snapshot.projectId().toString(), original, originGuard))) return false;
        var update=new Update().set("versions.$.reviewStatus",reviewed.getReviewStatus())
                .set("versions.$.rejectionReason",reviewed.getRejectionReason())
                .set("versions.$.scheduledPublishDate",reviewed.getScheduledPublishDate())
                .set("versions.$.scanResult",reviewed.getScanResult())
                .set("versions.$.securityApprovalProjectId", reviewed.getSecurityApprovalProjectId())
                .set("versions.$.approvedReviewOrigins",reviewed.getApprovedReviewOrigins())
                .set("versions.$.approvedFindingReviewHead", reviewed.getApprovedFindingReviewHead())
                .set("versions.$.approvedSecurityEvidence",reviewed.getApprovedSecurityEvidence())
                .set("versions.$.approvedSecurityContextSha256",reviewed.getApprovedSecurityContextSha256())
                .set("versions.$.securityApprovedAt",reviewed.getSecurityApprovedAt())
                .set("versions.$.approvedIssueBaselines",reviewed.getApprovedIssueBaselines())
                .set("updatedAt",LocalDateTime.now().toString());
        return applyUpdate(snapshot, update, originGuard.getQueryObject());
    }
    public boolean queueRescan(Snapshot snapshot, net.modtale.model.project.ScanResult queued) {
        if (snapshot.version().get("scanResult") instanceof Document scan && scan.get("remoteReview") != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This version has a retained remote review. A replacement scan must preserve its review history first.");
        return applyUpdate(snapshot, new Update()
                .set("versions.$.scanResult", queued)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString()));
    }
    public boolean appendFindingReview(Snapshot snapshot, String decisionId) {
        return applyUpdate(snapshot, new Update().set("versions.$.findingReviewHead", decisionId)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString()));
    }
    private boolean applyUpdate(Snapshot snapshot, Update update) { return applyUpdate(snapshot, update, new Document()); }
    private boolean applyUpdate(Snapshot snapshot, Update update, Document originGuard) {
        var entity=mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var mapped=new UpdateMapper(mongo.getConverter()).getMappedObject(update.getUpdateObject(),entity);
        if(!(snapshot.version().get("_id") instanceof String versionId))return false;
        var unique=new Document("$eq",List.of(new Document("$size",new Document("$filter",
                new Document("input",new Document("$cond",List.of(new Document("$isArray","$versions"),"$versions",List.of())))
                        .append("as","v").append("cond",new Document("$eq",List.of("$$v._id",new Document("$literal",versionId)))))),1));
        var filter=Filters.and(Filters.eq("_id",snapshot.projectId()),new Document("versions",new Document("$eq",snapshot.version())),new Document("$expr",unique));
        if (!originGuard.isEmpty()) filter = Filters.and(filter, originGuard);
        return mongo.getCollection(mongo.getCollectionName(Project.class)).withWriteConcern(com.mongodb.WriteConcern.MAJORITY.withJournal(true)
                .withWTimeout(10,java.util.concurrent.TimeUnit.SECONDS)).updateOne(filter,mapped,
                        new com.mongodb.client.model.UpdateOptions().collation(com.mongodb.client.model.Collation.builder().locale("simple").build())).getModifiedCount()>0;
    }
    public static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,"This version changed while the decision was being applied. Refresh its evidence before deciding.");
    }
}
