package net.modtale.service.admin.review;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ProjectReviewPersistence {
    private final MongoTemplate mongo;
    public ProjectReviewPersistence(MongoTemplate mongo) { this.mongo = mongo; }
    public record Snapshot(Document raw, Project project) {}
    public Snapshot capture(String id, String token) {
        var entity = mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var query = new QueryMapper(mongo.getConverter()).getMappedObject(new Document("_id", id), entity);
        var raw = mongo.getCollection(mongo.getCollectionName(Project.class)).find(query).first();
        if (raw == null) throw ProjectReviewSnapshot.conflict();
        var project = mongo.getConverter().read(Project.class, raw);
        ProjectReviewSnapshot.requireCurrent(project, token);
        return new Snapshot(raw, project);
    }
    public boolean apply(Snapshot snapshot, String approvedVersionId) {
        Project project = snapshot.project();
        var originGuard = new org.springframework.data.mongodb.core.query.Query();
        var update = new Update().set("status", project.getStatus()).set("expiresAt", project.getExpiresAt())
                .set("updatedAt", project.getUpdatedAt()).set("createdAt", project.getCreatedAt())
                .set("approvedBy", project.getApprovedBy()).set("imageUrl", project.getImageUrl())
                .set("rankingDirty", true);
        if (approvedVersionId != null) {
            var versions = project.getVersions();
            int index = -1;
            for (int i = 0; i < versions.size(); i++) if (approvedVersionId.equals(versions.get(i).getId())) {
                if (index >= 0) throw ProjectReviewSnapshot.conflict();
                index = i;
            }
            if (index < 0) throw ProjectReviewSnapshot.conflict();
            ProjectVersion version = versions.get(index);
            var original = mongo.getConverter().read(ProjectVersion.class, snapshot.raw().getList("versions", Document.class).get(index));
            if (!approvedVersionId.equals(original.getId())) throw ProjectReviewSnapshot.conflict();
            if (version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED)
                net.modtale.service.security.issue.FindingReviewHistory.requireManualApproval(mongo, project.getId(), original);
            var scan = original.getScanResult();
            if (version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED && scan != null && scan.getReusedReviewVersion() != null
                    && (!net.modtale.service.security.scan.ArtifactClearancePolicy.complete(scan)
                    || !net.modtale.service.security.scan.ArtifactReviewLineage.bind(mongo, project.getId(), scan, originGuard))) return false;
            String path = "versions." + index + ".";
            update.set(path + "reviewStatus", version.getReviewStatus()).set(path + "rejectionReason", version.getRejectionReason())
                    .set(path + "scheduledPublishDate", version.getScheduledPublishDate()).set(path + "scanResult", version.getScanResult())
                    .set(path + "securityApprovalProjectId", version.getSecurityApprovalProjectId())
                    .set(path + "approvedReviewOrigins", version.getApprovedReviewOrigins())
                    .set(path + "approvedSecurityEvidence", version.getApprovedSecurityEvidence())
                    .set(path + "approvedSecurityContextSha256", version.getApprovedSecurityContextSha256())
                    .set(path + "securityApprovedAt", version.getSecurityApprovedAt())
                    .set(path + "approvedIssueBaselines", version.getApprovedIssueBaselines());
        }
        return applyUpdate(snapshot, update, originGuard.getQueryObject().get("$expr"));
    }
    public boolean applyMetadataRepair(Snapshot snapshot, Map<String, Object> metadata) {
        net.modtale.service.admin.project.ProjectMetadataRepair.validate(metadata);
        var update = new Update();
        metadata.forEach(update::set);
        update.set("updatedAt", java.time.LocalDateTime.now().toString()).set("rankingDirty", true);
        return applyUpdate(snapshot, update);
    }
    private boolean applyUpdate(Snapshot snapshot, Update update) { return applyUpdate(snapshot, update, null); }
    private boolean applyUpdate(Snapshot snapshot, Update update, Object originGuard) {
        var entity = mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var mapped = new UpdateMapper(mongo.getConverter()).getMappedObject(update.getUpdateObject(), entity);
        Object expression = new Document("$eq", List.of("$$ROOT", new Document("$literal", snapshot.raw())));
        if (originGuard != null) expression = new Document("$and", List.of(expression, originGuard));
        var query = new Document("_id", snapshot.raw().get("_id")).append("$expr", expression);
        return mongo.getCollection(mongo.getCollectionName(Project.class)).updateOne(query, mapped).getModifiedCount() > 0;
    }
}
