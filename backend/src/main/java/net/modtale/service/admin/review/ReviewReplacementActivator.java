package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** Consumes a signed decision atomically; the existing remote scheduler delivers the fixed request. */
public final class ReviewReplacementActivator {
    public record Result(String state,String afterSha256) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final List<String> STATUSES=List.of("PENDING","PUBLISHED","UNLISTED","PRIVATE");
    private static final TransactionOptions OPTIONS=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
            .readPreference(ReadPreference.primary()).writeConcern(WriteConcern.MAJORITY.withJournal(true)).maxCommitTime(5000L,TimeUnit.MILLISECONDS).build();
    private final MongoTemplate mongo;
    private final ReviewReplacementActivationDecision decisions;
    private final ReviewSnapshotArchive archive;
    private final ReviewRepairJournal journal;
    private final RawReviewSnapshotReader reader;
    private final Supplier<ReviewReplacementPreparation.Configuration> configuration;
    private final MongoCollection<Document> projects,admissions,operations;
    public ReviewReplacementActivator(MongoTemplate mongo,ReviewReplacementActivationDecision decisions,ReviewSnapshotArchive archive,
            ReviewRepairJournal journal,RawReviewSnapshotReader reader,Supplier<ReviewReplacementPreparation.Configuration> configuration) {
        this.mongo=Objects.requireNonNull(mongo);this.decisions=Objects.requireNonNull(decisions);this.archive=Objects.requireNonNull(archive);
        this.journal=Objects.requireNonNull(journal);this.reader=Objects.requireNonNull(reader);this.configuration=Objects.requireNonNull(configuration);
        projects=mongo.getCollection("projects");admissions=mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS);
        operations=mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Result activate(ReviewReplacementActivationDecision.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);var previous=receipt(prepared,actor,permitted);
        if(!"UNKNOWN".equals(previous.state()))return previous;
        var context=decisions.verifyCurrent(prepared,actor,permitted);var source=context.decision();
        requireConfiguration(context);permission(permitted);
        var hello=ReviewRepairIo.database(mongo.getDb()).runCommand(new Document("hello",1),ReadPreference.primary());
        if(!(hello.get("setName") instanceof String) && !"isdbgrid".equals(hello.get("msg")))throw new IllegalStateException("Activation requires transaction support");
        var claim=journal.claim(new ReviewRepairPreparation.Prepared(source.id(),digest(source.versionBytes()),source.createdAt(),source.expiresAt()),
                actor,ReviewSnapshotArchive.Action.REPLACEMENT_ACTIVATION,permitted);
        if(claim==null)return receipt(prepared,actor,permitted);
        boolean commitAttempted=false;
        try(var session=mongo.getMongoDatabaseFactory().getSession(ReviewRepairIo.sessionOptions())) {
            try {
                session.startTransaction(ReviewRepairIo.transactionOptions(OPTIONS));permission(permitted);
                var op=ReviewRepairIo.collection(operations,session).find(session,new Document("_id",claim.id()).append("token",claim.token()).append("state","EXECUTING"))
                        .collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
                if(op==null)throw new IllegalStateException("Activation claim changed");
                var current=reader.capture(session,source.projectId(),source.versionIndex(),context.replacement().versionId());
                var admission=ReviewRepairIo.collection(admissions,session).find(session,new Document("_id",prepared.replacementId())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
                String state="NOT_APPLIED",after=null;
                if(current.sha256().equals(prepared.heldSha256()) && admission!=null && Arrays.equals(bytes(admission),bytes(context.admission()))) {
                    String prefix="versions."+source.versionIndex()+".scanResult.";
                    var query=new Document("_id",source.projectId()).append("status",new Document("$in",STATUSES)).append("$expr",new Document("$and",List.of(
                            new Document("$eq",List.of(new Document("$type","$status"),"string")),
                            new Document("$gte",List.of("$$NOW",new Date(source.createdAt()))),new Document("$lt",List.of("$$NOW",new Date(source.expiresAt()))))));
                    requireConfiguration(context);permission(permitted);
                    if(ReviewRepairIo.collection(projects,session).updateOne(session,query,new Document("$set",new Document(prefix+"scanState","REMOTE_REVIEW")
                            .append(prefix+"scanTimestamp",System.currentTimeMillis())),new UpdateOptions().collation(BINARY)).getModifiedCount()==1) {
                        after=reader.capture(session,source.projectId(),source.versionIndex(),context.replacement().versionId()).sha256();
                        var fields=new Document("state","ACTIVE").append("activationId",prepared.id()).append("activatedSha256",after);
                        if(ReviewRepairIo.collection(admissions,session).updateOne(session,admission,new Document("$set",fields),new UpdateOptions().collation(BINARY)).getModifiedCount()!=1)
                            throw new IllegalStateException("Admission changed");
                        state="APPLIED";
                    }
                }
                var fields=new Document("state",state).append("afterSha256",after).append("replacementId",prepared.replacementId());
                if(ReviewRepairIo.collection(operations,session).updateOne(session,op,List.of(new Document("$set",fields.append("finishedAt","$$NOW"))),new UpdateOptions().collation(BINARY)).getModifiedCount()!=1)
                    throw new IllegalStateException("Activation claim changed");
                requireConfiguration(context);permission(permitted);commitAttempted=true;session.commitTransaction();return new Result(state,after);
            } catch(RuntimeException failure) {
                if(!commitAttempted)try{session.abortTransaction();}catch(RuntimeException ignored){}
                throw failure;
            }
        } catch(RuntimeException uncertain) {
            try(var cleanup=ReviewRepairIo.cleanup()) {
                if(uncertain instanceof SecurityException) {try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}throw uncertain;}
                try{var observed=receipt(prepared,actor,permitted);if(!"UNKNOWN".equals(observed.state()))return observed;}catch(RuntimeException ignored){}
                try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}
                return new Result("UNKNOWN",null);
            }
        }
    }
    public Result receipt(ReviewReplacementActivationDecision.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);
        if(!prepared.equals(decisions.recover(prepared.id(),actor,permitted)))throw new IllegalStateException("Activation decision changed");
        var source=archive.load(prepared.id());
        var op=ReviewRepairIo.collection(operations).find(new Document("_id",prepared.id())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();permission(permitted);
        if(op==null || !actor.equals(op.get("actor")) || !"REPLACEMENT_ACTIVATION".equals(op.get("action"))
                || !prepared.replacementId().equals(op.get("replacementId")) || !digest(source.versionBytes()).equals(op.get("sha256"))
                || !Long.valueOf(prepared.createdAt()).equals(op.get("createdAt")) || !Long.valueOf(prepared.expiresAt()).equals(op.get("expiresAt"))
                || !(op.get("finishedAt") instanceof Date))return new Result("UNKNOWN",null);
        if("NOT_APPLIED".equals(op.get("state")) && op.get("afterSha256")==null)return new Result("NOT_APPLIED",null);
        if("APPLIED".equals(op.get("state")) && op.get("afterSha256") instanceof String sha && sha.matches("[0-9a-f]{64}"))return new Result("APPLIED",sha);
        return new Result("UNKNOWN",null);
    }
    private void requireConfiguration(ReviewReplacementActivationDecision.Context context) {
        // Runtime wiring must supply local current configuration, without network IO inside the transaction.
        var binding=context.replacement();var expected=new ReviewReplacementPreparation.Configuration(binding.policyVersion(),binding.reviewConfigSha256(),binding.origin());
        if(!expected.equals(configuration.get()))throw new IllegalStateException("Replacement service configuration changed");
    }
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
    private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception invalid){throw new IllegalStateException(invalid);}}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Replacement activation is not permitted");}
}
