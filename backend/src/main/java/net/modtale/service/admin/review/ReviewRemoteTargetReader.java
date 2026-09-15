package net.modtale.service.admin.review;

import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.RemoteReviewBinding;
import net.modtale.service.security.scan.ArtifactReviewContext;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.List;
import java.util.Objects;

/** Captured evidence only: neither authorizes replacement nor observes remote job state. */
public final class ReviewRemoteTargetReader {
    public record Captured(RawReviewSnapshotReader.Captured snapshot, RemoteReviewBinding binding) {}
    private final MongoTemplate mongo;

    public ReviewRemoteTargetReader(MongoTemplate mongo) { this.mongo=Objects.requireNonNull(mongo); }

    public Captured capture(Object projectId,int versionIndex,String versionId) {
        var snapshot=new RawReviewSnapshotReader(mongo).capture(projectId,versionIndex,versionId);
        var original=new RawBsonDocument(snapshot.versionBytes()).decode(new DocumentCodec());
        return new Captured(snapshot,validate(projectId,original));
    }

    public RemoteReviewBinding validate(Object projectId,Document original) {
        try {
            var scan=original.get("scanResult",Document.class);
            var raw=scan.get("remoteReview",Document.class);
            // Do not let mapping conversions manufacture a usable remote identity from malformed BSON.
            for(String field:List.of("projectId","versionId","requestId","filePath","artifactSha256",
                    "contextSha256","policyVersion","reviewConfigSha256","jobId"))
                if(!(raw.get(field) instanceof String))throw unavailable();
            if(!(raw.get("manualRescan") instanceof Boolean))throw unavailable();
            long attempt=attempt(raw.get("attempt"));
            var origin=raw.get("origin",Document.class);
            if(origin==null || !(origin.get("deploymentId") instanceof String)
                    || !(origin.get("callerScope") instanceof String))throw unavailable();
            var binding=mongo.getConverter().read(RemoteReviewBinding.class,raw);
            if(!projectId.toString().equals(binding.projectId()) || !binding.versionId().equals(original.get("_id"))
                    || !binding.requestId().equals(scan.get("scanRequestId"))
                    || !(scan.get("manualRescan") instanceof Boolean manual) || manual!=binding.manualRescan()
                    || attempt(scan.get("scanAttempt"))!=attempt
                    || !binding.filePath().equals(original.get("fileUrl")) || !binding.artifactSha256().equals(original.get("hash")))throw unavailable();
            // Poll/result corruption must not prevent retaining an otherwise intact artifact identity.
            var context=new Document();
            for(String field:List.of("_id","fileUrl","hash","gameVersions","dependencies","manifestId","manifestVersion","overrideFileUrl","modpackConfigs"))
                if(original.containsKey(field))context.put(field,original.get(field));
            var version=mongo.getConverter().read(ProjectVersion.class,context);
            if(!binding.contextSha256().equals(ArtifactReviewContext.automaticallyReviewableFingerprint(version)))throw unavailable();
            return binding;
        } catch(RuntimeException invalid) { throw unavailable(); }
    }

    private static long attempt(Object value) {
        if(!(value instanceof Integer || value instanceof Long))throw unavailable();
        long number=((Number)value).longValue();
        if(number<1 || number>Integer.MAX_VALUE)throw unavailable();
        return number;
    }
    private static IllegalStateException unavailable() {
        return new IllegalStateException("Original remote review target is unavailable or inconsistent");
    }
}
