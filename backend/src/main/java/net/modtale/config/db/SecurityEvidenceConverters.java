package net.modtale.config.db;

import java.util.*;
import net.modtale.model.project.ScanResult.SecurityEvidence;
import net.modtale.model.project.SecurityManifest;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mapping.MappingException;

public final class SecurityEvidenceConverters {
    private SecurityEvidenceConverters() {}

    @WritingConverter
    public static final class Write implements Converter<SecurityEvidence, Document> {
        private final ArtifactManifestStore store;
        public Write() { this(null); }
        public Write(ArtifactManifestStore store) { this.store = store; }
        @Override public Document convert(SecurityEvidence evidence) {
            if (store != null && evidence.entryHashes() instanceof ReferencedArtifactManifest reference && reference.store == store)
                return header(evidence).append("manifestRef", reference.identity);
            if (!SecurityManifest.valid(evidence.entryHashes(), true)) throw new MappingException("Invalid or oversized security manifest");
            if (store != null && evidence.entryHashes() != null && !evidence.entryHashes().isEmpty())
                return header(evidence).append("manifestRef", store.put(evidence.entryHashes()));
            var entries = new ArrayList<Document>();
            if (evidence.entryHashes() != null) evidence.entryHashes().forEach((path, hash) ->
                    entries.add(new Document("path", path).append("sha256", hash)));
            return header(evidence).append("entryHashes", entries);
        }
        private Document header(SecurityEvidence evidence) {
            return new Document("policyVersion", evidence.policyVersion())
                    .append("artifactSha256", evidence.artifactSha256())
                    .append("contentSha256", evidence.contentSha256())
                    .append("complete", evidence.complete())
                    .append("clearanceGranted", evidence.clearanceGranted())
                    .append("reviewState", evidence.reviewState());
        }
    }

    @ReadingConverter
    public static final class Read implements Converter<Document, SecurityEvidence> {
        private final ArtifactManifestStore store;
        public Read() { this(null); }
        public Read(ArtifactManifestStore store) { this.store = store; }
        @Override public SecurityEvidence convert(Document source) {
            if (source.containsKey("manifestRef")) {
                if (source.containsKey("entryHashes") || store == null || !(source.get("manifestRef") instanceof String identity)
                        || !SecurityManifest.digest(identity)) throw new MappingException("Invalid artifact manifest reference");
                return evidence(source, new ReferencedArtifactManifest(store, identity));
            }
            var entries = new LinkedHashMap<String, String>();
            Object stored = source.get("entryHashes");
            if (stored instanceof List<?> list) {
                if (list.size() > 20_000) throw new MappingException("Security manifest exceeds entry limit");
                for (Object value : list) {
                    if (!(value instanceof Document entry) || !(entry.get("path") instanceof String path)
                            || !(entry.get("sha256") instanceof String hash) || entries.putIfAbsent(path, hash) != null)
                        throw new MappingException("Invalid security manifest entry");
                }
            } else if (stored instanceof Map<?, ?> map) {
                if (map.size() > 20_000) throw new MappingException("Security manifest exceeds entry limit");
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String path) || !(entry.getValue() instanceof String hash))
                        throw new MappingException("Invalid legacy security manifest entry");
                    entries.put(path, hash);
                }
            } else if (stored != null) throw new MappingException("Invalid security manifest");
            if (!SecurityManifest.valid(entries, true)) throw new MappingException("Invalid or oversized security manifest");
            return evidence(source, entries);
        }
        private SecurityEvidence evidence(Document source, Map<String,String> entries) {
            return new SecurityEvidence(source.getString("policyVersion"), source.getString("artifactSha256"),
                    source.getString("contentSha256"), Boolean.TRUE.equals(source.get("complete")),
                    Boolean.TRUE.equals(source.get("clearanceGranted")), source.getString("reviewState"), entries);
        }
    }
}
