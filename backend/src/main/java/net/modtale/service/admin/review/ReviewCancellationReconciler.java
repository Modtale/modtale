package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import net.modtale.service.security.scan.RemoteReviewClient;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Separate status observations. No cancellation, replacement or overwrite of the original outcome. */
public final class ReviewCancellationReconciler implements AutoCloseable {
    public static final String COLLECTION="review_cancellation_observations";
    public record Receipt(String id,ReviewOrphanCancellationJournal.Prepared original,String state,
                          ReviewOrphanCancellationJournal.Observation observation,Long receivedAt) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private final MongoCollection<Document> observations;
    private final ReviewOrphanCancellationJournal journal;
    private final RemoteReviewClient client;
    private final Semaphore slots;
    private final long budgetNanos;
    private final AtomicBoolean closed=new AtomicBoolean();
    public ReviewCancellationReconciler(MongoTemplate mongo,ReviewOrphanCancellationJournal journal,RemoteReviewClient client,int concurrency,Duration budget) {
        if(concurrency<1 || concurrency>2 || budget==null || budget.compareTo(Duration.ofSeconds(1))<0 || budget.compareTo(Duration.ofSeconds(30))>0)throw new IllegalArgumentException("Invalid reconciliation capacity");
        this.journal=Objects.requireNonNull(journal);this.client=Objects.requireNonNull(client);slots=new Semaphore(concurrency);budgetNanos=budget.toNanos();
        observations=mongo.getCollection(COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(5,TimeUnit.SECONDS)).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public Receipt check(String id,ReviewOrphanCancellationJournal.Prepared original,String actor,BooleanSupplier permitted) {
        permission(permitted);if(!uuid(id))throw new IllegalArgumentException("Invalid observation identity");
        if(closed.get() || !slots.tryAcquire())throw new IllegalStateException("Reconciliation unavailable");
        long started=System.nanoTime();LongSupplier remaining=()->budgetNanos-(System.nanoTime()-started);
        BooleanSupplier allowed=()->{if(closed.get() || Thread.currentThread().isInterrupted() || remaining.getAsLong()<=0)throw new IllegalStateException("Reconciliation stopped");return permitted.getAsBoolean();};
        try(var io=ReviewRepairIo.open(remaining)) {
            var target=journal.reconciliationTarget(original,actor,allowed);var expected=intent(original,actor);var existing=read(id);
            if(existing!=null)return decoded(existing,id,original,expected,target,allowed);
            var reserved=new Document("_id",id).append("intent",expected).append("token",UUID.randomUUID().toString()).append("state","RESERVED");
            permission(allowed);
            try {ReviewRepairIo.collection(observations).insertOne(reserved);}
            catch(MongoException uncertain) {
                var stored=read(id);if(stored==null)throw unavailable();
                if(!Arrays.equals(bytes(stored),bytes(reserved)))return decoded(stored,id,original,expected,target,allowed);
            }
            permission(allowed);ReviewOrphanCancellationJournal.Observation observation;
            try {
                var status=client.status(target.binding(),()->{
                    if(!target.equals(journal.reconciliationTarget(original,actor,allowed)))throw unavailable();
                    permission(allowed);
                    var reading=ReviewRepairIo.collection(observations).findOneAndUpdate(exact(reserved),List.of(new Document("$set",new Document("state","READING").append("startedAt","$$NOW"))),
                            new FindOneAndUpdateOptions().collation(BINARY).returnDocument(ReturnDocument.AFTER));
                    if(reading==null)return false;decoded(reading,id,original,expected,target,allowed);return allowed.getAsBoolean();
                },remaining);
                observation=new ReviewOrphanCancellationJournal.Observation("REMOTE_STATUS",status,null);
            }catch(RemoteReviewClient.Unavailable failure) {
                int code=failure.status();String kind=code==404?"NOT_FOUND":code==409?"CONTEXT_CONFLICT":code==401||code==403||code==429?"UNAVAILABLE":"UNKNOWN";
                observation=new ReviewOrphanCancellationJournal.Observation(kind,null,code);
            }catch(RemoteReviewClient.Superseded stopped){observation=new ReviewOrphanCancellationJournal.Observation("UNKNOWN",null,null);}
            try(var cleanup=ReviewRepairIo.cleanup()) {
                journal.reconciliationTarget(original,actor,permitted);ReviewOrphanCancellationJournal.validateObservation(observation,target);
                var stored=read(id);decoded(stored,id,original,expected,target,permitted);
                if("READING".equals(stored.get("state")) && reserved.get("token").equals(stored.get("token"))) {
                    permission(permitted);
                    var fields=new Document("state","UNKNOWN".equals(observation.kind())?"UNKNOWN":"OBSERVED")
                            .append("observation",literal(ReviewOrphanCancellationJournal.observationDocument(observation))).append("receivedAt","$$NOW");
                    try {ReviewRepairIo.collection(observations).updateOne(exact(stored),List.of(new Document("$set",fields)),new UpdateOptions().collation(BINARY));}
                    catch(MongoException uncertain){ /* Read-back preserves uncertainty; no second GET. */ }
                }
                return receipt(id,original,actor,permitted);
            }
        }finally{slots.release();}
    }
    /** Reads only the retained local observation, including after the original cancellation expiry. */
    public Receipt receipt(String id,ReviewOrphanCancellationJournal.Prepared original,String actor,BooleanSupplier permitted) {
        permission(permitted);if(!uuid(id))throw new IllegalArgumentException("Invalid observation identity");
        var target=journal.reconciliationTarget(original,actor,permitted);return decoded(read(id),id,original,intent(original,actor),target,permitted);
    }
    private static Receipt decoded(Document stored,String id,ReviewOrphanCancellationJournal.Prepared original,Document intent,
                                   ReviewOrphanTargetResolver.Target target,BooleanSupplier permitted) {
        try {
            if(stored==null || !id.equals(stored.get("_id")) || !(stored.get("token") instanceof String token) || !uuid(token)
                    || !(stored.get("intent") instanceof Document expected) || !Arrays.equals(bytes(expected),bytes(intent)))throw unavailable();
            String state=stored.getString("state");ReviewOrphanCancellationJournal.Observation observation=null;Long received=null;
            if("RESERVED".equals(state)) {if(stored.size()!=4)throw unavailable();}
            else {
                if(!(stored.get("startedAt") instanceof Date start))throw unavailable();
                if("READING".equals(state)) {if(stored.size()!=5)throw unavailable();}
                else {
                    if(!Set.of("OBSERVED","UNKNOWN").contains(state) || stored.size()!=7 || !(stored.get("receivedAt") instanceof Date end) || end.before(start))throw unavailable();
                    observation=ReviewOrphanCancellationJournal.observation(stored.get("observation",Document.class));ReviewOrphanCancellationJournal.validateObservation(observation,target);
                    if("UNKNOWN".equals(state)!= "UNKNOWN".equals(observation.kind()))throw unavailable();received=end.getTime();
                }
            }
            permission(permitted);return new Receipt(id,original,state,observation,received);
        }catch(SecurityException denied){throw denied;}
        catch(RuntimeException invalid){throw unavailable();}
    }
    private Document read(String id){return ReviewRepairIo.collection(observations).find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();}
    private static Document intent(ReviewOrphanCancellationJournal.Prepared original,String actor){return new Document("actor",actor).append("isolationId",original.isolationId())
            .append("targetSha256",original.targetSha256()).append("createdAt",original.createdAt()).append("expiresAt",original.expiresAt());}
    private static Document exact(Document stored){return new Document("_id",stored.get("_id")).append("$expr",new Document("$and",List.of(
            new Document("$eq",List.of("$$ROOT",literal(stored))),new Document("$eq",List.of(new Document("$type","$intent.createdAt"),"long")),
            new Document("$eq",List.of(new Document("$type","$intent.expiresAt"),"long")))));}
    private static byte[] bytes(Document doc){var buffer=new RawBsonDocument(doc,new DocumentCodec()).getByteBuffer().asNIO();byte[] result=new byte[buffer.remaining()];buffer.get(result);return result;}
    private static Document literal(Object value){return new Document("$literal",value);}
    private static boolean uuid(String value){return value!=null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Reconciliation is not permitted");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Reconciliation observation is unavailable or inconsistent");}
    @Override public void close(){closed.set(true);}
}
