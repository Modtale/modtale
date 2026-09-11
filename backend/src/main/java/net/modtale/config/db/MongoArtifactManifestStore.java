package net.modtale.config.db;

import com.mongodb.MongoWriteException;
import net.modtale.model.project.SecurityManifest;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mapping.MappingException;
import java.util.*;

/** Immutable, content-addressed records; raw driver access avoids recursive mapping conversions. */
public final class MongoArtifactManifestStore implements ArtifactManifestStore {
    public static final String COLLECTION = "artifact_manifests";
    private final MongoDatabaseFactory database;
    public MongoArtifactManifestStore(MongoDatabaseFactory database) { this.database = database; }
    @Override public String put(Map<String,String> entries) {
        if (!SecurityManifest.valid(entries, false)) throw new MappingException("Invalid artifact manifest");
        var snapshot = Collections.unmodifiableMap(new TreeMap<>(entries));
        String identity = SecurityManifest.identity(snapshot);
        var values = new ArrayList<Document>();
        snapshot.forEach((path, hash) -> values.add(new Document("path", path).append("sha256", hash)));
        try {
            database.getMongoDatabase().getCollection(COLLECTION).insertOne(new Document("_id", identity)
                    .append("entries", values).append("createdAt", new Date()));
        } catch (MongoWriteException duplicate) {
            if (duplicate.getError().getCode() != 11000) throw duplicate;
            get(identity); // An existing record must really contain the same immutable manifest.
        }
        return identity;
    }
    @Override public Map<String,String> get(String identity) {
        if (!SecurityManifest.digest(identity)) throw new MappingException("Invalid artifact manifest reference");
        Document document = database.getMongoDatabase().getCollection(COLLECTION).find(new Document("_id", identity)).first();
        if (document == null) throw new MappingException("Artifact manifest is unavailable");
        var evidence = new SecurityEvidenceConverters.Read().convert(new Document("entryHashes", document.get("entries")));
        var entries = evidence.entryHashes();
        if (!SecurityManifest.valid(entries, false) || !identity.equals(SecurityManifest.identity(entries)))
            throw new MappingException("Artifact manifest integrity check failed");
        return Collections.unmodifiableMap(new TreeMap<>(entries));
    }
}
