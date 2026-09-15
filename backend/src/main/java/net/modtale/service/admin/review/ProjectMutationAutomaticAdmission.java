package net.modtale.service.admin.review;

import com.mongodb.*;
import net.modtale.service.security.scan.RemoteReviewClient;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Automatic coordination never acknowledges uncertain duplicate execution or grants clearance. */
public final class ProjectMutationAutomaticAdmission {
    public static final String ACTOR="security-admission";
    public record Result(String state,String decisionId) {}
    private final MongoTemplate mongo;private final ReviewRepairWorkflow budget;private final RawReviewSnapshotReader reader;
    private final ProjectMutationReferenceReader history;private final ProjectMutationPriorWorkReader prior;
    private final ProjectMutationObservationProgress progress;
    private final ProjectMutationAdmissionAttempts attempts;private final ProjectMutationJobAccounting accounting;
    private final ProjectMutationAdmissionPreparation preparation;private final ProjectMutationActivator activator;private final ProjectMutationAdmissionReader admissions;
    public ProjectMutationAutomaticAdmission(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationReferenceReader history,ProjectMutationPriorWorkReader prior,
            ProjectMutationAdmissionAttempts attempts,ProjectMutationJobAccounting accounting,ProjectMutationAdmissionPreparation preparation,ProjectMutationActivator activator,ReviewSnapshotArchive archive) {
        this.mongo=Objects.requireNonNull(mongo);this.budget=Objects.requireNonNull(budget);this.history=Objects.requireNonNull(history);this.prior=Objects.requireNonNull(prior);
        this.attempts=Objects.requireNonNull(attempts);this.accounting=Objects.requireNonNull(accounting);this.preparation=Objects.requireNonNull(preparation);this.activator=Objects.requireNonNull(activator);
        progress=new ProjectMutationObservationProgress(mongo,accounting);reader=new RawReviewSnapshotReader(mongo);admissions=new ProjectMutationAdmissionReader(mongo,archive);
    }
    public Result advance(ProjectMutationDiscovery.Candidate candidate,BooleanSupplier running) {
        return budget.call(allowed->advanceWithinBudget(candidate,()->allowed.getAsBoolean() && activeProject(candidate.projectId())),running);
    }
    private Result advanceWithinBudget(ProjectMutationDiscovery.Candidate candidate,BooleanSupplier allowed) {
        permission(allowed);var scope=new ProjectMutationAdmissionAttempts.Scope(candidate.projectId(),candidate.versionId(),candidate.mutationId(),candidate.requestId(),candidate.attempt());
        var captured=reader.capture(candidate.projectId(),candidate.versionIndex(),candidate.versionId());var version=new RawBsonDocument(captured.versionBytes()).decode(new DocumentCodec());
        var pointer=version.get("versionMutation",Document.class);var scan=version.get("scanResult",Document.class);
        if(pointer==null || !candidate.mutationId().equals(pointer.get("operationId")) || !candidate.requestId().equals(pointer.get("requestId")))return new Result("NO_WORK",null);
        if(scan==null || !"MUTATION_HELD".equals(scan.get("scanState")))return reconcileAdmission(scope,version,allowed);
        if(!candidate.requestId().equals(scan.get("scanRequestId")) || !(scan.get("scanAttempt") instanceof Integer || scan.get("scanAttempt") instanceof Long)
                || ((Number)scan.get("scanAttempt")).longValue()!=candidate.attempt())return new Result("NO_WORK",null);
        history.requireHeldHeads(candidate.projectId(),bytes(new Document("_id",candidate.projectId()).append("versions",List.of(version))),allowed);
        var claim=attempts.beginWithinBudget(scope,captured.sha256(),allowed);
        if(claim==null){var state=attempts.statusWithinBudget(scope,allowed);return new Result(state.state(),state.current()==null?null:state.current().decisionId());}
        ProjectMutationPriorWorkReader.Inventory inventory;
        try{inventory=prior.readWithinBudget(candidate.projectId(),candidate.mutationId(),allowed);}
        catch(ProjectMutationPriorWorkReader.LimitExceeded limit){return finish(claim,ProjectMutationAdmissionAttempts.Outcome.ATTENTION,allowed);}
        if(inventory.work().stream().anyMatch(work->work.kind()==ProjectMutationPriorWorkReader.Kind.UNRESOLVED))return finish(claim,ProjectMutationAdmissionAttempts.Outcome.ATTENTION,allowed);
        var observations=new LinkedHashMap<String,String>();boolean waiting=false,attention=false,yielded=false,madeProgress=false;int reads=0;
        try(var receipts=accounting.readBatch(candidate.projectId(),allowed)){
        for(var work:inventory.work()) {
            if(work.kind()!=ProjectMutationPriorWorkReader.Kind.REMOTE_JOB)continue;
            String key=work.mutationId()+"/"+work.versionId();String id=UUID.nameUUIDFromBytes(("automatic-status-1:"+claim.decisionId()+":"+key).getBytes(StandardCharsets.UTF_8)).toString();
            var receipt=progress.completed(scope,work,allowed,receipts);
            if(receipt==null) {
                // Leave time to retain the result and finish bookkeeping before yielding this generation.
                if(reads>=1 || ReviewRepairIo.currentRemainingNanos()<TimeUnit.SECONDS.toNanos(10)){yielded=true;break;}
                receipt=accounting.checkWithinBudget(id,candidate.projectId(),work.mutationId(),work.versionId(),allowed,java.time.Duration.ofSeconds(5));reads++;
                if(ProjectMutationObservationProgress.isCompleted(receipt)){progress.retain(scope,work,receipt,allowed);madeProgress=true;}
            }
            observations.put(key,receipt.id());
            if(!Set.of("OBSERVED","UNKNOWN").contains(receipt.state()) || receipt.observation()==null){attention=true;continue;}
            var observation=receipt.observation();var status=observation.status();
            if("REMOTE_STATUS".equals(observation.kind()) && status!=null) {
                if("COMPLETED".equals(status.state()) && "COMPLETED".equals(status.workState()))continue;
                if(Set.of("QUEUED","RUNNING","HELD","UPLOADING","AWAITING_UPLOAD").contains(status.state()) || "COMPLETED".equals(status.state()) && "RUNNING".equals(status.workState()))waiting=true;
                else attention=true;
            } else if("UNKNOWN".equals(observation.kind()) || "UNAVAILABLE".equals(observation.kind()) && Integer.valueOf(429).equals(observation.httpStatus())) {
                // A later generation may perform a new read-only observation; never replay this observation ID.
                waiting=true;
            } else attention=true;
        }
        }
        if(attention)return finish(claim,ProjectMutationAdmissionAttempts.Outcome.ATTENTION,allowed);
        if(waiting)return finish(claim,ProjectMutationAdmissionAttempts.Outcome.WAITING,allowed);
        if(yielded || ReviewRepairIo.currentRemainingNanos()<TimeUnit.SECONDS.toNanos(10))return finish(claim,madeProgress?ProjectMutationAdmissionAttempts.Outcome.YIELDED:ProjectMutationAdmissionAttempts.Outcome.WAITING,allowed);
        ProjectMutationAdmissionPreparation.Prepared prepared;
        try {
            prepared=preparation.prepareWithinBudget(new ProjectMutationAdmissionPreparation.Request(claim.decisionId(),candidate.projectId(),candidate.versionIndex(),candidate.versionId(),captured.sha256(),candidate.mutationId(),ACTOR,observations,false),allowed);
        }catch(RemoteReviewClient.Unavailable retry){return finish(claim,ProjectMutationAdmissionAttempts.Outcome.WAITING,allowed);}
        catch(IllegalStateException | IllegalArgumentException changed){permission(allowed);return finish(claim,reader.isCurrent(captured)?ProjectMutationAdmissionAttempts.Outcome.ATTENTION:ProjectMutationAdmissionAttempts.Outcome.WAITING,allowed);}
        var activated=activator.activateWithinBudget(prepared,ACTOR,allowed);
        return finish(claim,"APPLIED".equals(activated.state())?ProjectMutationAdmissionAttempts.Outcome.ADMITTED:ProjectMutationAdmissionAttempts.Outcome.ATTENTION,allowed);
    }
    private Result reconcileAdmission(ProjectMutationAdmissionAttempts.Scope scope,Document version,BooleanSupplier allowed) {
        admissions.requireHead(scope.projectId(),version,allowed);var admitted=admissions.read(scope.projectId(),scope.requestId(),allowed);var status=attempts.statusWithinBudget(scope,allowed);
        if(status.current()!=null && status.current().decisionId().equals(admitted.decision().id())
                && Set.of("RUNNING","ADMITTED").contains(status.state()))return finish(status.current(),ProjectMutationAdmissionAttempts.Outcome.ADMITTED,allowed);
        return new Result("ADMITTED",admitted.decision().id());
    }
    private Result finish(ProjectMutationAdmissionAttempts.Claim claim,ProjectMutationAdmissionAttempts.Outcome outcome,BooleanSupplier allowed) {
        var status=attempts.finishWithinBudget(claim,outcome,allowed);return new Result(status.state(),claim.decisionId());
    }
    private boolean activeProject(Object id) {
        var project=ReviewRepairIo.collection(mongo.getCollection("projects").withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY))
                .find(new Document("_id",id)).projection(new Document("status",1)).maxTime(5,TimeUnit.SECONDS).first();
        return project!=null && project.get("status") instanceof String status && Set.of("PENDING","PUBLISHED","UNLISTED","PRIVATE").contains(status);
    }
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();var result=new byte[buffer.remaining()];buffer.get(result);return result;}
    private static void permission(BooleanSupplier allowed){if(!allowed.getAsBoolean())throw new SecurityException("Automatic admission is stopped or withdrawn");}
}
