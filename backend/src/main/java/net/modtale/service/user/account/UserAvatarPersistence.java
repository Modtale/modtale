package net.modtale.service.user.account;

import java.util.ArrayList;
import java.util.List;
import net.modtale.model.user.User;
import net.modtale.util.MongoIdUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserAvatarPersistence {
    private final MongoTemplate mongo;
    public UserAvatarPersistence(MongoTemplate mongo) { this.mongo = mongo; }
    public record Snapshot(Document raw, User user) {}
    public Snapshot capture(String id) {
        if (id == null || id.isBlank()) return null;
        var matches = mongo.getCollection(mongo.getCollectionName(User.class))
                .find(new Document("_id", new Document("$in", MongoIdUtils.expandIds(List.of(id)))))
                .limit(2).into(new ArrayList<>());
        if (matches.size() != 1 || matches.getFirst().get("deletedAt") != null) return null;
        var raw = matches.getFirst();
        return new Snapshot(raw, mongo.getConverter().read(User.class, raw));
    }
    public boolean update(Snapshot snapshot, String avatarUrl) {
        if (snapshot == null || avatarUrl == null || avatarUrl.isBlank()) return false;
        return mongo.getCollection(mongo.getCollectionName(User.class)).updateOne(
                new Document("_id", snapshot.raw().get("_id")).append("$expr",
                        new Document("$eq", List.of("$$ROOT", new Document("$literal", snapshot.raw())))),
                new Document("$set", new Document("avatarUrl", avatarUrl))).getModifiedCount() == 1;
    }
}
