package net.modtale.service.user.account;

import java.util.ArrayList;
import java.util.List;
import net.modtale.model.user.User;
import net.modtale.util.MongoIdUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccountPreferencesPersistence {
    public enum Field {
        AVATAR("avatarUrl"), BANNER("bannerUrl"), NOTIFICATIONS("notificationPreferences"),
        LAUNCHER("launcherSettings");
        private final String key;
        Field(String key) { this.key = key; }
    }

    public record Snapshot(Document raw, User user) {}
    private final MongoTemplate mongo;

    public AccountPreferencesPersistence(MongoTemplate mongo) { this.mongo = mongo; }

    public Snapshot capture(String id) {
        if (id == null || id.isBlank()) return null;
        var matches = mongo.getCollection(mongo.getCollectionName(User.class))
                .find(new Document("_id", new Document("$in", MongoIdUtils.expandIds(List.of(id)))))
                .limit(2).into(new ArrayList<>());
        if (matches.size() != 1 || matches.getFirst().get("deletedAt") != null) return null;
        var raw = matches.getFirst();
        return new Snapshot(raw, mongo.getConverter().read(User.class, raw));
    }

    public boolean update(Snapshot snapshot, Field field) {
        if (snapshot == null || field == null) return false;
        var converted = new Document();
        mongo.getConverter().write(snapshot.user(), converted);
        // The enum is the complete writable surface; no account authority fields are accepted.
        var update = new Document("$set", new Document(field.key, converted.get(field.key)));
        return mongo.getCollection(mongo.getCollectionName(User.class)).updateOne(
                new Document("_id", snapshot.raw().get("_id")).append("$expr",
                        new Document("$eq", List.of("$$ROOT", new Document("$literal", snapshot.raw())))),
                update).getMatchedCount() == 1;
    }
}
