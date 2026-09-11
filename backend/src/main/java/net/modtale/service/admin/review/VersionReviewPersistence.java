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
        var entity=mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var filter=new QueryMapper(mongo.getConverter()).getMappedObject(new Document("_id",projectId),entity);
        Document project=mongo.getCollection(mongo.getCollectionName(Project.class)).find(filter).first();
        if(project==null || !(project.get("versions") instanceof List<?> versions)) throw conflict();
        for(Object item:versions) if(item instanceof Document stored) {
            var version=mongo.getConverter().read(ProjectVersion.class,stored);
            if(Objects.equals(versionId,version.getId())) {
                VersionReviewSnapshot.requireCurrent(version,expectedToken);
                return new Snapshot(project.get("_id"),stored);
            }
        }
        throw conflict();
    }
    public boolean apply(Snapshot snapshot, ProjectVersion reviewed) {
        var update=new Update().set("versions.$.reviewStatus",reviewed.getReviewStatus())
                .set("versions.$.rejectionReason",reviewed.getRejectionReason())
                .set("versions.$.scheduledPublishDate",reviewed.getScheduledPublishDate())
                .set("versions.$.scanResult",reviewed.getScanResult())
                .set("versions.$.approvedSecurityEvidence",reviewed.getApprovedSecurityEvidence())
                .set("versions.$.approvedSecurityContextSha256",reviewed.getApprovedSecurityContextSha256())
                .set("versions.$.securityApprovedAt",reviewed.getSecurityApprovedAt())
                .set("versions.$.approvedIssueBaselines",reviewed.getApprovedIssueBaselines())
                .set("updatedAt",LocalDateTime.now().toString());
        var entity=mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var mapped=new UpdateMapper(mongo.getConverter()).getMappedObject(update.getUpdateObject(),entity);
        var filter=Filters.and(Filters.eq("_id",snapshot.projectId()),new Document("versions",new Document("$eq",snapshot.version())));
        return mongo.getCollection(mongo.getCollectionName(Project.class)).updateOne(filter,mapped).getModifiedCount()>0;
    }
    static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,"This version changed while the decision was being applied. Refresh its evidence before deciding.");
    }
}
