package net.modtale.service.user.organization;
import java.util.*;
import net.modtale.model.user.User;
import net.modtale.util.MongoIdUtils;
import net.modtale.exception.InvalidOrganizationRequestException;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrganizationInvitePersistence {
    private final MongoTemplate mongo;
    public OrganizationInvitePersistence(MongoTemplate mongo) { this.mongo = mongo; }
    public record Snapshot(Document raw, User organization) {}
    public Snapshot capture(String id) {
        var matches = mongo.getCollection(mongo.getCollectionName(User.class))
                .find(new Document("_id", new Document("$in", MongoIdUtils.expandIds(List.of(id))))).limit(2).into(new ArrayList<>());
        if (matches.size() != 1) throw new InvalidOrganizationRequestException("Organization not found.");
        var raw = matches.getFirst(); var organization = mongo.getConverter().read(User.class, raw);
        if (organization.isDeleted() || organization.getAccountType() != User.AccountType.ORGANIZATION)
            throw new InvalidOrganizationRequestException("Organization not available.");
        return new Snapshot(raw, organization);
    }
    public boolean create(Snapshot snapshot, User.OrganizationMember invite) {
        Object encoded = mongo.getConverter().convertToMongoType(invite);
        var update = snapshot.raw().get("pendingOrgInvites") == null
                ? new Document("$set", new Document("pendingOrgInvites", List.of(encoded)))
                : new Document("$push", new Document("pendingOrgInvites", encoded));
        return write(snapshot, update, null);
    }
    public boolean resolve(Snapshot snapshot, String userId, String requestId, boolean accepting) {
        var original = mongo.getConverter().read(User.class, snapshot.raw());
        var invite = OrganizationInvitationPolicy.require(original, userId, requestId, accepting);
        var update = new Document("$pull", new Document("pendingOrgInvites", new Document("userId", userId).append("requestId", invite.getRequestId())));
        if (accepting) {
            Object member = mongo.getConverter().convertToMongoType(new User.OrganizationMember(userId, invite.getRoleId()));
            update.append(snapshot.raw().get("organizationMembers") == null ? "$set" : "$push",
                    new Document("organizationMembers", snapshot.raw().get("organizationMembers") == null ? List.of(member) : member));
        }
        Object expiry = accepting ? new Document("$gt", List.of(invite.getRequestExpiresAt(), new Document("$toLong", "$$NOW"))) : null;
        return write(snapshot, update, expiry);
    }
    private boolean write(Snapshot snapshot, Document update, Object extraGuard) {
        Object match = new Document("$eq", List.of("$$ROOT", new Document("$literal", snapshot.raw())));
        if (extraGuard != null) match = new Document("$and", List.of(match, extraGuard));
        return mongo.getCollection(mongo.getCollectionName(User.class)).updateOne(
                new Document("_id", snapshot.raw().get("_id")).append("$expr", match), update).getModifiedCount() == 1;
    }
}
