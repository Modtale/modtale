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
            version.setApprovedFindingReviewHead(version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED
                    ? original.getFindingReviewHead() : null);
            var scan = original.getScanResult();
            if (version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED && scan != null && scan.getReusedReviewVersion() != null
                    && (!net.modtale.service.security.scan.ArtifactClearancePolicy.complete(scan)
                    || !net.modtale.service.security.scan.ArtifactReviewLineage.bind(mongo, project.getId(), scan, originGuard))) return false;
            String path = "versions." + index + ".";
            update.set(path + "reviewStatus", version.getReviewStatus()).set(path + "rejectionReason", version.getRejectionReason())
                    .set(path + "scheduledPublishDate", version.getScheduledPublishDate()).set(path + "scanResult", version.getScanResult())
                    .set(path + "securityApprovalProjectId", version.getSecurityApprovalProjectId())
                    .set(path + "approvedReviewOrigins", version.getApprovedReviewOrigins())
                    .set(path + "approvedFindingReviewHead", version.getApprovedFindingReviewHead())
                    .set(path + "approvedSecurityEvidence", version.getApprovedSecurityEvidence())
                    .set(path + "approvedSecurityContextSha256", version.getApprovedSecurityContextSha256())
                    .set(path + "securityApprovedAt", version.getSecurityApprovedAt())
                    .set(path + "approvedIssueBaselines", version.getApprovedIssueBaselines());
        }
        return applyUpdate(snapshot, update, originGuard.getQueryObject().get("$expr"));
    }
    public boolean submitDraft(Snapshot snapshot) {
        if (snapshot.project().getStatus() != net.modtale.model.project.ProjectStatus.PENDING
                || !"DRAFT".equals(snapshot.raw().getString("status"))) throw ProjectReviewSnapshot.conflict();
        var update = new Update().set("status", net.modtale.model.project.ProjectStatus.PENDING)
                .set("expiresAt", null).set("updatedAt", java.time.LocalDateTime.now().toString());
        var originals = snapshot.raw().getList("versions", Document.class, List.of());
        var versions = snapshot.project().getVersions();
        if (originals.size() != versions.size()) throw ProjectReviewSnapshot.conflict();
        for (int i = 0; i < versions.size(); i++) {
            var original = mongo.getConverter().read(ProjectVersion.class, originals.get(i));
            var version = versions.get(i);
            if (!Objects.equals(original.getId(), version.getId())) throw ProjectReviewSnapshot.conflict();
            String path = "versions." + i + ".";
            if (original.getScanResult() == null && version.getScanResult() != null) {
                update.set(path + "scanResult", version.getScanResult()).set(path + "reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                        .set(path + "scheduledPublishDate", null);
            } else if (original.getReviewStatus() == null || original.getReviewStatus() == ProjectVersion.ReviewStatus.REJECTED) {
                update.set(path + "reviewStatus", ProjectVersion.ReviewStatus.PENDING);
            }
        }
        return applyUpdate(snapshot, update);
    }
    public boolean applyTeam(Snapshot snapshot) {
        var encoded = new Document(); mongo.getConverter().write(snapshot.project(), encoded);
        var update = new Update();
        for (String field : List.of("authorId", "author", "pendingTransferTo", "teamMembers", "teamInvites", "projectRoles"))
            update.set(field, encoded.get(field));
        update.set("updatedAt", java.time.LocalDateTime.now().toString());
        return applyUpdate(snapshot, update);
    }
    public boolean applyDeletionState(Snapshot snapshot, boolean scrub) {
        var project = snapshot.project();
        var update = new Update().set("status", project.getStatus()).set("deletedAt", project.getDeletedAt())
                .set("updatedAt", java.time.LocalDateTime.now().toString()).set("rankingDirty", true);
        if (scrub) {
            var encoded = new Document(); mongo.getConverter().write(project, encoded);
            for (String field : List.of("title", "description", "about", "slug", "imageUrl", "bannerUrl", "galleryImages",
                    "galleryImageCaptions", "teamMembers", "teamInvites", "projectRoles", "comments", "tags"))
                update.set(field, encoded.get(field));
        }
        return applyUpdate(snapshot, update);
    }
    public boolean deleteProject(Snapshot snapshot) {
        var query = new Document("_id", snapshot.raw().get("_id")).append("$expr",
                new Document("$eq", List.of("$$ROOT", new Document("$literal", snapshot.raw()))));
        return mongo.getCollection(mongo.getCollectionName(Project.class)).deleteOne(query).getDeletedCount() == 1;
    }
    public boolean applyPresentation(Snapshot snapshot, boolean media) {
        var fields = media ? List.of("imageUrl", "bannerUrl", "galleryImages", "galleryImageCaptions")
                : List.of("classification", "tags", "title", "description", "about", "categories", "slug", "license",
                        "customLicenseOpenSource", "repositoryUrl", "types", "allowModpacks", "allowComments",
                        "hmWikiEnabled", "hmWikiSlug", "galleryCarouselEnabled", "links", "imageUrl");
        var encoded = new Document(); mongo.getConverter().write(snapshot.project(), encoded);
        var update = new Update();
        for (var field : fields) update.set(field, encoded.get(field));
        update.set("updatedAt", java.time.LocalDateTime.now().toString()).set("rankingDirty", true);
        return applyUpdate(snapshot, update);
    }
    public boolean applyVersionList(Snapshot snapshot) {
        var original = new HashMap<String, Document>();
        for (var raw : snapshot.raw().getList("versions", Document.class, List.of())) {
            var version = mongo.getConverter().read(ProjectVersion.class, raw);
            if (version.getId() == null || original.put(version.getId(), raw) != null) throw ProjectReviewSnapshot.conflict();
        }
        var seen = new HashSet<String>();
        var updated = new ArrayList<Document>();
        for (var version : snapshot.project().getVersions()) {
            if (version.getId() == null || !seen.add(version.getId())) throw ProjectReviewSnapshot.conflict();
            var prior = original.get(version.getId());
            if (prior == null) {
                if (version.getReviewStatus() != ProjectVersion.ReviewStatus.PENDING) throw ProjectReviewSnapshot.conflict();
                var added = new Document(); mongo.getConverter().write(version, added); updated.add(added);
            } else {
                // Retained versions keep every stored field, including evidence unknown to this binary.
                var retained = new Document(prior);
                var before = mongo.getConverter().read(ProjectVersion.class, prior);
                if (!Objects.equals(before.getGameVersions(), version.getGameVersions())) {
                    retained.put("gameVersions", version.getGameVersions());
                    retained.put("reviewStatus", "PENDING"); retained.put("scheduledPublishDate", null);
                    retained.put("scanResult", mongo.getConverter().convertToMongoType(version.getScanResult()));
                    for (String field : List.of("approvedSecurityEvidence", "approvedSecurityContextSha256", "approvedReviewOrigins",
                            "securityApprovalProjectId", "approvedFindingReviewHead", "approvedIssueBaselines")) retained.put(field, null);
                    retained.put("securityApprovedAt", 0L);
                }
                updated.add(retained);
            }
        }
        return applyUpdate(snapshot, new Update().set("versions", updated)
                .set("classification", snapshot.project().getClassification())
                .set("childProjectIds", snapshot.project().getChildProjectIds())
                .set("updatedAt", java.time.LocalDateTime.now().toString()));
    }
    public boolean applyVersionEdit(Snapshot snapshot, String versionId, boolean contextChanged, boolean childIdsChanged) {
        var versions = snapshot.project().getVersions();
        int index = -1;
        for (int i = 0; i < versions.size(); i++) if (versionId.equals(versions.get(i).getId())) {
            if (index >= 0) throw ProjectReviewSnapshot.conflict();
            index = i;
        }
        if (index < 0) throw ProjectReviewSnapshot.conflict();
        var version = versions.get(index);
        String path = "versions." + index + ".";
        var update = new Update().set(path + "gameVersions", version.getGameVersions())
                .set(path + "dependencies", version.getDependencies()).set(path + "incompatibleProjectIds", version.getIncompatibleProjectIds())
                .set(path + "changelog", version.getChangelog()).set(path + "channel", version.getChannel())
                .set(path + "fileUrl", version.getFileUrl());
        if (childIdsChanged) update.set("childProjectIds", snapshot.project().getChildProjectIds());
        if (contextChanged) update.set(path + "reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set(path + "scheduledPublishDate", null).set(path + "scanResult", version.getScanResult())
                .set(path + "approvedSecurityEvidence", null).set(path + "approvedSecurityContextSha256", null)
                .set(path + "securityApprovedAt", 0).set(path + "approvedReviewOrigins", null)
                .set(path + "securityApprovalProjectId", null).set(path + "approvedFindingReviewHead", null)
                .set(path + "approvedIssueBaselines", null);
        update.set("updatedAt", java.time.LocalDateTime.now().toString());
        return applyUpdate(snapshot, update);
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
