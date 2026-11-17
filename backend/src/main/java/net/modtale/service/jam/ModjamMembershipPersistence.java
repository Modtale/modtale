package net.modtale.service.jam;

import java.util.ArrayList;
import java.util.List;
import net.modtale.model.jam.Modjam;
import net.modtale.model.user.User;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class ModjamMembershipPersistence {
    private final MongoTemplate mongo;

    public ModjamMembershipPersistence(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Modjam participate(String jamId, String userId, boolean join) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(jamId).and("participantIds").is(null)),
                new Update().set("participantIds", List.of()), Modjam.class);
        Update jamUpdate = join ? new Update().addToSet("participantIds", userId) : new Update().pull("participantIds", userId);
        Modjam jam = mongo.findAndModify(Query.query(Criteria.where("_id").is(jamId)),
                jamUpdate, FindAndModifyOptions.options().returnNew(true), Modjam.class);
        if (jam != null) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(userId).and("deletedAt").is(null).and("joinedModjamIds").is(null)),
                    new Update().set("joinedModjamIds", List.of()), User.class);
            // Always reconcile both sides. A repeated request repairs an interrupted
            // first write without duplicating memberships or replacing an account.
            Update userUpdate = join ? new Update().addToSet("joinedModjamIds", jamId) : new Update().pull("joinedModjamIds", jamId);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(userId).and("deletedAt").is(null)), userUpdate, User.class);
        }
        return jam;
    }

    public Modjam inviteJudge(String jamId, String userId, String username) {
        Object entries = inviteEntries();
        Object keys = new Document("$map", new Document("input", entries).append("as", "invite").append("in", "$$invite.k"));
        Document eligible = new Document("$and", List.of(
                new Document("$not", List.of(new Document("$in", List.of(literal(userId), array("judgeIds"))))),
                new Document("$not", List.of(new Document("$in", List.of(literal(userId), keys))))));
        Object invites = new Document("$arrayToObject", new Document("$concatArrays", List.of(entries,
                literal(List.of(new Document("k", userId).append("v", username))))));
        return changeJudges(jamId, eligible, new Document("pendingJudgeInviteUsers", invites));
    }

    public Modjam answerJudgeInvite(String jamId, String userId, boolean accept) {
        Object entries = inviteEntries();
        Object keys = new Document("$map", new Document("input", entries).append("as", "invite").append("in", "$$invite.k"));
        Document eligible = new Document("$in", List.of(literal(userId), keys));
        Document fields = new Document("pendingJudgeInviteUsers", withoutInvites(List.of(userId)));
        if (accept) fields.append("judgeIds", new Document("$setUnion", List.of(array("judgeIds"), literal(List.of(userId)))));
        return changeJudges(jamId, eligible, fields);
    }

    public Modjam removeJudges(String jamId, List<String> userIds) {
        Document fields = new Document("pendingJudgeInviteUsers", withoutInvites(userIds))
                .append("judgeIds", new Document("$setDifference", List.of(array("judgeIds"), literal(userIds))));
        return changeJudges(jamId, null, fields);
    }

    private Modjam changeJudges(String jamId, Document guard, Document fields) {
        Query query = Query.query(Criteria.where("_id").is(jamId));
        if (guard != null) query.addCriteria(Criteria.where("$expr").is(guard));
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(context -> new Document("$set", fields));
        // The display list is derived from the updated identity map, rather than
        // restoring a stale list that could erase a concurrently accepted invite.
        stages.add(context -> new Document("$set", new Document("pendingJudgeInvites",
                new Document("$map", new Document("input", inviteEntries()).append("as", "invite").append("in", "$$invite.v")))));
        return mongo.findAndModify(query, AggregationUpdate.from(stages),
                FindAndModifyOptions.options().returnNew(true), Modjam.class);
    }

    private static Object withoutInvites(List<String> userIds) {
        return new Document("$arrayToObject", new Document("$filter", new Document("input", inviteEntries())
                .append("as", "invite").append("cond", new Document("$not", List.of(
                        new Document("$in", List.of("$$invite.k", literal(userIds))))))));
    }

    private static Object inviteEntries() {
        return new Document("$objectToArray", new Document("$ifNull", List.of("$pendingJudgeInviteUsers", new Document())));
    }

    private static Object array(String name) { return new Document("$ifNull", List.of("$" + name, List.of())); }
    private static Object literal(Object value) { return new Document("$literal", value); }
}
