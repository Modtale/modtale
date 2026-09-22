package net.modtale.service.project.team;

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
public class ProjectTeamPersistence {
    private final MongoTemplate mongo;
    public ProjectTeamPersistence(MongoTemplate mongo) { this.mongo = mongo; }
    public record Snapshot(Document raw, Project project) {}
    public Snapshot capture(String id, String token) {
        var entity = mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var query = new QueryMapper(mongo.getConverter()).getMappedObject(new Document("_id", id), entity);
        var raw = mongo.getCollection(mongo.getCollectionName(Project.class)).find(query).first();
        if (raw == null) throw ProjectTeamSnapshot.conflict();
        var project = mongo.getConverter().read(Project.class, raw);
        ProjectTeamSnapshot.requireCurrent(project, token);
        return new Snapshot(raw, project);
    }
    public boolean applyTeam(Snapshot snapshot) { return applyTeam(snapshot, null); }
    public boolean resolveContributorInvite(Snapshot snapshot, String userId, String requestId, boolean accepting) {
        var original = mongo.getConverter().read(Project.class, snapshot.raw());
        var invite = net.modtale.service.project.team.ProjectInvitationPolicy.require(original, userId, requestId, accepting);
        return applyTeam(snapshot, accepting ? new Document("$gt", List.of(invite.getRequestExpiresAt(), new Document("$toLong", "$$NOW"))) : null);
    }
    public boolean resolveTransfer(Snapshot snapshot, String requestId) {
        if (requestId == null || !requestId.equals(snapshot.raw().getString("pendingTransferRequestId"))
                || snapshot.raw().get("pendingTransferOwnerId") == null
                || !Objects.equals(snapshot.raw().get("pendingTransferOwnerId"), snapshot.raw().get("authorId")))
            throw ProjectTeamSnapshot.conflict();
        return applyTeam(snapshot, new Document("$gt", List.of("$pendingTransferExpiresAt", new Document("$toLong", "$$NOW"))));
    }
    private boolean applyTeam(Snapshot snapshot, Object guard) {
        var encoded = new Document(); mongo.getConverter().write(snapshot.project(), encoded);
        var update = new Update();
        for (String field : List.of("authorId", "author", "pendingTransferTo", "pendingTransferRequestId", "pendingTransferOwnerId", "pendingTransferExpiresAt", "teamMembers", "teamInvites", "projectRoles"))
            update.set(field, encoded.get(field));
        update.set("updatedAt", java.time.LocalDateTime.now().toString());
        return applyUpdate(snapshot, update, guard);
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
