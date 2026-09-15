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
import java.util.function.Function;

/** Shared one-shot remote status observations; never starts or cancels remote work. */
final class ReviewRemoteStatusObserver implements AutoCloseable {
    record Target(Document intent,net.modtale.model.project.RemoteReviewBinding binding) {
        Target {intent=new RawBsonDocument(bytes(intent)).decode(new DocumentCodec());Objects.requireNonNull(binding);}
        @Override public Document intent(){return new RawBsonDocument(bytes(intent)).decode(new DocumentCodec());}
    }
    record Receipt(String id,String state,ReviewOrphanCancellationJournal.Observation observation,Long receivedAt) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private final MongoCollection<Document> observations;
    private final RemoteReviewClient client;
    private final Semaphore slots;
    private final long budgetNanos;
    private final AtomicBoolean closed=new AtomicBoolean();
    ReviewRemoteStatusObserver(MongoTemplate mongo,String collection,RemoteReviewClient client,int concurrency,Duration budget) {
        if(concurrency<1 || concurrency>2 || budget==null || budget.compareTo(Duration.ofSeconds(1))<0 || budget.compareTo(Duration.ofSeconds(30))>0)throw new IllegalArgumentException("Invalid reconciliation capacity");
        this.client=Objects.requireNonNull(client);slots=new Semaphore(concurrency);budgetNanos=budget.toNanos();
        observations=mongo.getCollection(collection).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(5,TimeUnit.SECONDS)).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    Receipt check(String id,Function<BooleanSupplier,Target> resolve,BooleanSupplier permitted) {
        permission(permitted);if(!uuid(id))throw new IllegalArgumentException("Invalid observation identity");
        if(closed.get() || !slots.tryAcquire())throw new IllegalStateException("Reconciliation unavailable");
        long started=System.nanoTime();LongSupplier remaining=()->budgetNanos-(System.nanoTime()-started);
        BooleanSupplier allowed=()->{if(closed.get() || Thread.currentThread().isInterrupted() || remaining.getAsLong()<=0)throw new IllegalStateException("Reconciliation stopped");return permitted.getAsBoolean();};
        try(var io=ReviewRepairIo.open(remaining)) {
            var target=resolve.apply(allowed);var expected=target.intent();var existing=read(id);
            if(existing!=null)return decoded(existing,id,expected,target,allowed);
            var reserved=new Document("_id",id).append("intent",expected).append("token",UUID.randomUUID().toString()).append("state","RESERVED");
            permission(allowed);
            try {ReviewRepairIo.collection(observations).insertOne(reserved);}
            catch(MongoException uncertain) {
                var stored=read(id);if(stored==null)throw unavailable();
                if(!Arrays.equals(bytes(stored),bytes(reserved)))return decoded(stored,id,expected,target,allowed);
            }
            permission(allowed);ReviewOrphanCancellationJournal.Observation observation;
            try {
                var status=client.status(target.binding(),()->{
                    if(!target.equals(resolve.apply(allowed)))throw unavailable();
                    permission(allowed);
                    var reading=ReviewRepairIo.collection(observations).findOneAndUpdate(exact(reserved),List.of(new Document("$set",new Document("state","READING").append("startedAt","$$NOW"))),
                            new FindOneAndUpdateOptions().collation(BINARY).returnDocument(ReturnDocument.AFTER));
                    if(reading==null)return false;decoded(reading,id,expected,target,allowed);return allowed.getAsBoolean();
                },io::remainingNanos);
                observation=new ReviewOrphanCancellationJournal.Observation("REMOTE_STATUS",status,null);
            }catch(RemoteReviewClient.Unavailable failure) {
                int code=failure.status();String kind=code==404?"NOT_FOUND":code==409?"CONTEXT_CONFLICT":code==401||code==403||code==429?"UNAVAILABLE":"UNKNOWN";
                observation=new ReviewOrphanCancellationJournal.Observation(kind,null,code);
            }catch(RemoteReviewClient.Superseded stopped){observation=new ReviewOrphanCancellationJournal.Observation("UNKNOWN",null,null);}
            try(var cleanup=ReviewRepairIo.cleanup()) {
                if(!target.equals(resolve.apply(permitted)))throw unavailable();ReviewOrphanCancellationJournal.validateObservation(observation,target.binding());
                var stored=read(id);decoded(stored,id,expected,target,permitted);
                if("READING".equals(stored.get("state")) && reserved.get("token").equals(stored.get("token"))) {
                    permission(permitted);
                    var fields=new Document("state","UNKNOWN".equals(observation.kind())?"UNKNOWN":"OBSERVED")
                            .append("observation",literal(ReviewOrphanCancellationJournal.observationDocument(observation))).append("receivedAt","$$NOW");
                    try {ReviewRepairIo.collection(observations).updateOne(exact(stored),List.of(new Document("$set",fields)),new UpdateOptions().collation(BINARY));}
                    catch(MongoException uncertain){ /* Read-back preserves uncertainty; no second GET. */ }
                }
                return receipt(id,resolve,permitted);
            }
        }finally{slots.release();}
    }
    /** Reads only the retained local observation, including after the original proposal expiry. */
    Receipt receipt(String id,Function<BooleanSupplier,Target> resolve,BooleanSupplier permitted) {
        permission(permitted);if(!uuid(id))throw new IllegalArgumentException("Invalid observation identity");
        var target=resolve.apply(permitted);return decoded(read(id),id,target.intent(),target,permitted);
    }
    private static Receipt decoded(Document stored,String id,Document intent,
                                   Target target,BooleanSupplier permitted) {
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
                    observation=ReviewOrphanCancellationJournal.observation(stored.get("observation",Document.class));ReviewOrphanCancellationJournal.validateObservation(observation,target.binding());
                    if("UNKNOWN".equals(state)!= "UNKNOWN".equals(observation.kind()))throw unavailable();received=end.getTime();
                }
            }
            permission(permitted);return new Receipt(id,state,observation,received);
        }catch(SecurityException denied){throw denied;}
        catch(RuntimeException invalid){throw unavailable();}
    }
    private Document read(String id){return ReviewRepairIo.collection(observations).find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();}
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
