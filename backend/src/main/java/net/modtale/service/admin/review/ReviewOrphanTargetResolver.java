package net.modtale.service.admin.review;

import net.modtale.model.project.*;
import org.bson.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;

/** Original target evidence only; resolving a target never sends or authorizes a remote cancellation. */
public final class ReviewOrphanTargetResolver {
    public record Target(String isolationId,Object projectId,int versionIndex,String beforeSha256,RemoteReviewBinding binding) {}
    private final ReviewSnapshotArchive archive;
    private final ReviewIsolationExecutor isolation;
    private final MongoTemplate mongo;
    public ReviewOrphanTargetResolver(MongoTemplate mongo,ReviewSnapshotArchive archive,ReviewIsolationExecutor isolation) {
        this.mongo=Objects.requireNonNull(mongo);this.archive=Objects.requireNonNull(archive);this.isolation=Objects.requireNonNull(isolation);
    }
    public Target resolve(String isolationId,String actor) {
        var recovered=isolation.recover(isolationId,actor);
        if(!"APPLIED".equals(recovered.receipt().outcome().state()))throw unavailable();
        var source=archive.load(isolationId);
        try {
            if(!source.id().equals(recovered.prepared().id()) || !source.actorId().equals(actor) || source.action()!=ReviewSnapshotArchive.Action.ISOLATE_REVIEW
                    || source.createdAt()!=recovered.prepared().createdAt() || source.expiresAt()!=recovered.prepared().expiresAt()
                    || !source.projectId().equals(recovered.receipt().projectId()) || source.versionIndex()!=recovered.receipt().versionIndex()
                    || !digest(source.versionBytes()).equals(recovered.prepared().sha256()))throw unavailable();
            var original=new RawBsonDocument(source.versionBytes()).decode(new org.bson.codecs.DocumentCodec());
            var scan=original.get("scanResult",Document.class);
            if(!"PENDING".equals(original.get("reviewStatus")) || !"SCANNING".equals(scan.get("status"))
                    || !"REMOTE_REVIEW".equals(scan.get("scanState")))throw unavailable();
            var binding=new ReviewRemoteTargetReader(mongo).validate(source.projectId(),original);
            return new Target(isolationId,source.projectId(),source.versionIndex(),recovered.prepared().sha256(),binding);
        } catch(RuntimeException invalid) {throw unavailable();}
    }
    private static String digest(byte[] value) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("Original remote review target is unavailable or inconsistent");}
}
