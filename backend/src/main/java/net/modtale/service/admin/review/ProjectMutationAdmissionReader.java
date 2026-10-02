package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.model.Collation;
import net.modtale.model.project.RemoteReviewBinding;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Authenticates admission provenance, not the truth or approval authority of later scan outcomes. */
public final class ProjectMutationAdmissionReader {
    public record History(ReviewSnapshotArchive.Snapshot source,ProjectMutationAdmissionPreparation.Prepared decision) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final Set<String> OUTCOME=Set.of("scanResult","retainedRemoteReview","reviewStatus","rejectionReason","scheduledPublishDate",
            "securityApprovedAt","approvedSecurityEvidence","approvedSecurityContextSha256","approvedReviewOrigins","approvedFindingReviewHead","securityApprovalProjectId","approvedIssueBaselines");
    private final MongoTemplate mongo;private final ReviewSnapshotArchive archive;private final ReviewRemoteTargetReader targets;
    public ProjectMutationAdmissionReader(MongoTemplate mongo,ReviewSnapshotArchive archive){this.mongo=Objects.requireNonNull(mongo);this.archive=Objects.requireNonNull(archive);targets=new ReviewRemoteTargetReader(mongo);}
    public History read(Object projectId,String requestId,BooleanSupplier allowed) {
        permission(allowed);if(requestId==null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid();
        var record=read(ProjectMutationActivator.ADMISSIONS,requestId);if(record==null || !projectId.equals(record.get("projectId")) || !(record.get("decisionId") instanceof String id))throw invalid();
        var source=archive.load(id);var prepared=ProjectMutationAdmissionPreparation.decodeStored(mongo,source);
        if(!projectId.equals(source.projectId()) || !requestId.equals(prepared.binding().requestId()))throw invalid();
        String after=digest(bytes(ProjectMutationActivator.projected(source)));
        if(!Arrays.equals(bytes(record),bytes(ProjectMutationActivator.admission(source,prepared,after))))throw invalid();
        var op=read(ReviewRepairJournal.COLLECTION,id);
        if(op==null || !"APPLIED".equals(op.get("state")) || !source.actorId().equals(op.get("actor")) || !source.action().name().equals(op.get("action"))
                || !prepared.decisionSha256().equals(op.get("sha256")) || !Long.valueOf(source.createdAt()).equals(op.get("createdAt"))
                || !Long.valueOf(source.expiresAt()).equals(op.get("expiresAt")) || !prepared.mutationId().equals(op.get("mutationId"))
                || !requestId.equals(op.get("requestId")) || !after.equals(op.get("afterSha256")) || !(op.get("finishedAt") instanceof Date))throw invalid();
        permission(allowed);return new History(source,prepared);
    }
    public void requireNoAdmission(Document version,BooleanSupplier allowed) {
        var scan=version.get("scanResult") instanceof Document doc?doc:null;
        var retained=version.get("retainedRemoteReview") instanceof Document doc?doc:null;
        var remote=scan!=null && scan.get("remoteReview") instanceof Document doc?doc:null;
        for(var source:Arrays.asList(scan,remote,retained)) {
            if(source==null)continue;String field=source==scan?"scanRequestId":"requestId";
            if(source.get(field) instanceof String request)requireUnadmitted(request,allowed);
        }
    }
    public void requireUnadmitted(String requestId,BooleanSupplier allowed) {
        permission(allowed);if(read(ProjectMutationActivator.ADMISSIONS,requestId)!=null)throw invalid();permission(allowed);
    }
    public void requireHead(Object projectId,Document current,BooleanSupplier allowed) {
        permission(allowed);var pointer=current.get("versionMutation",Document.class);
        if(pointer==null || pointer.size()!=3 || !(pointer.get("requestId") instanceof String request))throw invalid();
        var history=read(projectId,request,allowed);var expected=ProjectMutationActivator.projected(history.source());
        if(!history.decision().mutationId().equals(pointer.get("operationId")) || !history.decision().versionId().equals(current.get("_id")))throw invalid();
        var left=new Document(expected);var right=new Document(current);for(var field:OUTCOME){left.remove(field);right.remove(field);}
        if(VersionReviewTransition.classify(List.of(left),List.of(right)).getFirst().changes().stream().anyMatch(c->c!=VersionReviewTransition.Change.METADATA))throw invalid();
        // Validate the original artifact/context/attempt/origin even before a remote job ID has been attached.
        var copy=new RawBsonDocument(bytes(current)).decode(new DocumentCodec());var scan=copy.get("scanResult",Document.class);
        var remote=scan==null?copy.get("retainedRemoteReview",Document.class):scan.get("remoteReview",Document.class);if(remote==null)throw invalid();
        String job=remote.get("jobId")==null?"00000000-0000-0000-0000-000000000000":remote.getString("jobId");remote.put("jobId",job);
        RemoteReviewBinding binding=targets.validate(projectId,copy);
        if(!history.decision().binding().withJobId(job).equals(binding))throw invalid();permission(allowed);
    }
    private Document read(String collection,String id){return ReviewRepairIo.collection(mongo.getCollection(collection).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY))
            .find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();}
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();var result=new byte[buffer.remaining()];buffer.get(result);return result;}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static void permission(BooleanSupplier allowed){if(!allowed.getAsBoolean())throw new SecurityException("Mutation admission history is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Mutation admission history changed or is unavailable");}
}
