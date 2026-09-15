package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Retained lookup hints only; every reuse authenticates the original observation and job. */
final class ProjectMutationObservationProgress {
    static final String COLLECTION="project_mutation_observation_progress";
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private final MongoCollection<Document> records;
    private final ProjectMutationJobAccounting accounting;
    ProjectMutationObservationProgress(MongoTemplate mongo,ProjectMutationJobAccounting accounting){
        this.accounting=Objects.requireNonNull(accounting);
        records=mongo.getCollection(COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(5,TimeUnit.SECONDS)).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    ProjectMutationJobAccounting.Receipt completed(ProjectMutationAdmissionAttempts.Scope scope,ProjectMutationPriorWorkReader.Work work,BooleanSupplier allowed,ProjectMutationJobAccounting.ReadBatch batch){
        permission(allowed);var identity=identity(scope,work);var stored=read(identity.getString("_id"));if(stored==null)return null;
        var id=stored.get("observationId");if(!(id instanceof String value) || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid();
        identity.append("observationId",value);if(!Arrays.equals(bytes(identity),bytes(stored)))throw invalid();
        var receipt=batch.receipt(value,scope.projectId(),work.mutationId(),work.versionId());
        requireCompleted(receipt,work);permission(allowed);return receipt;
    }
    void retain(ProjectMutationAdmissionAttempts.Scope scope,ProjectMutationPriorWorkReader.Work work,ProjectMutationJobAccounting.Receipt receipt,BooleanSupplier allowed){
        permission(allowed);requireCompleted(receipt,work);
        var verified=accounting.receiptWithinBudget(receipt.id(),scope.projectId(),work.mutationId(),work.versionId(),allowed);
        if(!receipt.equals(verified))throw invalid();
        var expected=identity(scope,work).append("observationId",receipt.id());permission(allowed);
        try{ReviewRepairIo.collection(records).insertOne(expected);}catch(MongoException uncertain){/* Exact read-back below; no observation replay. */}
        var stored=read(expected.getString("_id"));if(stored==null || !Arrays.equals(bytes(expected),bytes(stored)))throw invalid();permission(allowed);
    }
    static boolean isCompleted(ProjectMutationJobAccounting.Receipt receipt){
        if(receipt==null || !"OBSERVED".equals(receipt.state()) || receipt.observation()==null || !"REMOTE_STATUS".equals(receipt.observation().kind()))return false;
        var status=receipt.observation().status();return status!=null && "COMPLETED".equals(status.state()) && "COMPLETED".equals(status.workState());
    }
    private static void requireCompleted(ProjectMutationJobAccounting.Receipt receipt,ProjectMutationPriorWorkReader.Work work){
        if(!isCompleted(receipt) || !work.mutationId().equals(receipt.mutationId()) || !work.versionId().equals(receipt.versionId()))throw invalid();
        ReviewOrphanCancellationJournal.validateObservation(receipt.observation(),work.binding());
    }
    private static Document identity(ProjectMutationAdmissionAttempts.Scope scope,ProjectMutationPriorWorkReader.Work work){
        if(work.kind()!=ProjectMutationPriorWorkReader.Kind.REMOTE_JOB)throw invalid();
        var target=new Document("projectId",scope.projectId()).append("versionId",scope.versionId()).append("mutationId",scope.mutationId()).append("requestId",scope.requestId()).append("scanAttempt",scope.scanAttempt());
        var fields=new Document("scope",target).append("mutationId",work.mutationId()).append("versionId",work.versionId()).append("beforeSha256",work.beforeSha256());
        var result=new Document("_id",UUID.nameUUIDFromBytes(bytes(fields)).toString());result.putAll(fields);return result;
    }
    private Document read(String id){return ReviewRepairIo.collection(records).find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();}
    private static byte[] bytes(Document doc){var buffer=new RawBsonDocument(doc,new DocumentCodec()).getByteBuffer().asNIO();var result=new byte[buffer.remaining()];buffer.get(result);return result;}
    private static void permission(BooleanSupplier allowed){if(!allowed.getAsBoolean())throw new SecurityException("Observation progress is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Completed observation progress is unavailable or inconsistent");}
}
