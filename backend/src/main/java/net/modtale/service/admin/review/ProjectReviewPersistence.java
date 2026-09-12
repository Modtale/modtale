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
            String path = "versions." + index + ".";
            update.set(path + "reviewStatus", version.getReviewStatus()).set(path + "rejectionReason", version.getRejectionReason())
                    .set(path + "scheduledPublishDate", version.getScheduledPublishDate()).set(path + "scanResult", version.getScanResult())
                    .set(path + "approvedSecurityEvidence", version.getApprovedSecurityEvidence())
                    .set(path + "approvedSecurityContextSha256", version.getApprovedSecurityContextSha256())
                    .set(path + "securityApprovedAt", version.getSecurityApprovedAt())
                    .set(path + "approvedIssueBaselines", version.getApprovedIssueBaselines());
        }
        return applyUpdate(snapshot, update);
    }
    public boolean applyMetadataRepair(Snapshot snapshot, Map<String, Object> metadata) {
        net.modtale.service.admin.project.ProjectMetadataRepair.validate(metadata);
        var update = new Update();
        metadata.forEach(update::set);
        update.set("updatedAt", java.time.LocalDateTime.now().toString()).set("rankingDirty", true);
        return applyUpdate(snapshot, update);
    }
    private boolean applyUpdate(Snapshot snapshot, Update update) {
        var entity = mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var mapped = new UpdateMapper(mongo.getConverter()).getMappedObject(update.getUpdateObject(), entity);
        var query = new Document("_id", snapshot.raw().get("_id")).append("$expr",
                new Document("$eq", List.of("$$ROOT", new Document("$literal", snapshot.raw()))));
        return mongo.getCollection(mongo.getCollectionName(Project.class)).updateOne(query, mapped).getModifiedCount() > 0;
    }
}
