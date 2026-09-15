package net.modtale.service.social;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.util.MongoIdUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class FavoritePersistence {
    private final MongoTemplate mongo;
    private static final TransactionOptions OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary()).maxCommitTime(10L, TimeUnit.SECONDS).build();

    public FavoritePersistence(MongoTemplate mongo) { this.mongo = mongo; }

    public int toggle(String projectId, String userId) {
        try (var session = mongo.getMongoDatabaseFactory().getSession(ClientSessionOptions.builder().build())) {
            return session.withTransaction(() -> {
                var projects = mongo.getCollection(mongo.getCollectionName(Project.class));
                var users = mongo.getCollection(mongo.getCollectionName(User.class));
                var project = requireUnique(projects, session, projectId);
                var user = requireUnique(users, session, userId);
                if (user.get("deletedAt") != null || "DELETED".equals(project.getString("status")))
                    throw new ResourceNotFoundException("Project or account is no longer available.");
                String canonicalId = project.get("_id").toString();
                var likes = user.getList("likedModIds", String.class, List.of());
                boolean removing = likes != null && likes.contains(canonicalId);
                var count = project.get("favoriteCount", Number.class);
                int nextCount = Math.toIntExact(Math.max(0L, (count == null ? 0L : count.longValue()) + (removing ? -1 : 1)));
                var userChange = new Document(removing ? "$pull" : "$addToSet", new Document("likedModIds", canonicalId));
                // Legacy null arrays are initialized in the same transaction before the set operation.
                if (user.containsKey("likedModIds") && user.get("likedModIds") == null)
                    users.updateOne(session, new Document("_id", user.get("_id")), new Document("$set", new Document("likedModIds", List.of())));
                users.updateOne(session, new Document("_id", user.get("_id")), userChange);
                projects.updateOne(session, new Document("_id", project.get("_id")),
                        new Document("$set", new Document("favoriteCount", nextCount).append("rankingDirty", true)));
                return nextCount;
            }, OPTIONS);
        }
    }

    private Document requireUnique(MongoCollection<Document> collection, ClientSession session, String id) {
        if (id == null || id.isBlank()) throw new ResourceNotFoundException("Project or account not found.");
        var matches = collection.find(session, new Document("_id", new Document("$in", MongoIdUtils.expandIds(List.of(id)))))
                .limit(2).into(new ArrayList<>());
        if (matches.size() != 1) throw new ResourceNotFoundException("Project or account not found.");
        return matches.getFirst();
    }
}
