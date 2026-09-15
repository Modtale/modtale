package net.modtale.service.admin.review;

import net.modtale.model.project.*;
import net.modtale.service.security.scan.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.bson.types.Binary;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Signed, expiring admission evidence only. No queue activation or security clearance. */
public final class ProjectMutationAdmissionPreparation {
    public record Request(String id,Object projectId,int versionIndex,String versionId,String heldSha256,String mutationId,
                          String actor,Map<String,String> observations,boolean acknowledgeUncertainty) {
        public Request {
            if(observations==null || observations.size()>256)throw invalid();observations=Map.copyOf(observations);
        }
    }
    public record Prepared(String id,String decisionSha256,String mutationId,String versionId,String heldSha256,
                           RemoteReviewBinding binding,String rule,long createdAt,long expiresAt) {}
    private final MongoTemplate mongo;private final ReviewRepairWorkflow budget;private final ReviewSnapshotArchive archive;
    private final ProjectMutationPriorWorkReader prior;private final ProjectMutationJobAccounting accounting;
    private final ProjectMutationReferenceReader history;private final RawReviewSnapshotReader reader;private final RemoteReviewClient client;
    private final Clock clock;private final long lifetimeMillis;
    public ProjectMutationAdmissionPreparation(MongoTemplate mongo,ReviewRepairWorkflow budget,ReviewSnapshotArchive archive,
            ProjectMutationReferenceReader history,ProjectMutationPriorWorkReader prior,ProjectMutationJobAccounting accounting,
            RemoteReviewClient client,Clock clock,long lifetimeMillis) {
        if(lifetimeMillis<1000 || lifetimeMillis>120000)throw invalid();this.mongo=Objects.requireNonNull(mongo);this.budget=Objects.requireNonNull(budget);
        this.archive=Objects.requireNonNull(archive);this.history=Objects.requireNonNull(history);this.prior=Objects.requireNonNull(prior);
        this.accounting=Objects.requireNonNull(accounting);this.client=Objects.requireNonNull(client);this.clock=Objects.requireNonNull(clock);
        this.lifetimeMillis=lifetimeMillis;reader=new RawReviewSnapshotReader(mongo);
    }
    public Prepared prepare(Request request,BooleanSupplier permitted) {return budget.call(allowed->prepareWithinBudget(request,allowed),permitted);}
    Prepared prepareWithinBudget(Request request,BooleanSupplier allowed) {
        if(request==null || !uuid(request.id()) || !uuid(request.mutationId()) || !digestValue(request.heldSha256())
                || request.actor()==null || request.actor().isBlank() || request.actor().length()>256)throw invalid();
        permission(allowed);var captured=reader.capture(request.projectId(),request.versionIndex(),request.versionId());
        if(!captured.sha256().equals(request.heldSha256()))throw invalid();var held=decode(captured.versionBytes());
        var pointer=held.get("versionMutation",Document.class);var scan=held.get("scanResult",Document.class);
        if(pointer==null || !request.mutationId().equals(pointer.get("operationId")) || scan==null
                || !"MUTATION_HELD".equals(scan.get("scanState")) || !"SCANNING".equals(scan.get("status"))
                || !"PENDING".equals(held.get("reviewStatus")) || !Boolean.FALSE.equals(scan.get("manualRescan")))throw invalid();
        history.requireHeldHeads(request.projectId(),bytes(new Document("_id",request.projectId()).append("versions",List.of(held))),allowed);
        var inventory=prior.readWithinBudget(request.projectId(),request.mutationId(),allowed);var evidence=new ArrayList<Document>();
        var used=new HashSet<String>();boolean uncertain=false;
        for(var work:inventory.work()) {
            permission(allowed);var item=new Document("mutationId",work.mutationId()).append("versionId",work.versionId()).append("beforeSha256",work.beforeSha256())
                    .append("kind",work.kind().name()).append("reason",work.reason());
            if(work.kind()==ProjectMutationPriorWorkReader.Kind.REMOTE_JOB) {
                String key=work.mutationId()+"/"+work.versionId(),id=request.observations().get(key);if(!uuid(id))throw invalid();used.add(key);
                var observation=accounting.receiptWithinBudget(id,request.projectId(),work.mutationId(),work.versionId(),allowed);
                if(!Set.of("OBSERVED","UNKNOWN").contains(observation.state()))throw invalid();
                ReviewOrphanCancellationJournal.validateObservation(observation.observation(),work.binding());
                var status=observation.observation().status();boolean completed="OBSERVED".equals(observation.state())
                        && "REMOTE_STATUS".equals(observation.observation().kind()) && status!=null
                        && "COMPLETED".equals(status.state()) && "COMPLETED".equals(status.workState());
                uncertain|=!completed;item.append("observation",new Document("id",id).append("state",observation.state())
                        .append("receivedAt",observation.receivedAt()).append("body",ReviewOrphanCancellationJournal.observationDocument(observation.observation())));
            } else if(work.kind()==ProjectMutationPriorWorkReader.Kind.UNRESOLVED)uncertain=true;
            evidence.add(item);
        }
        if(!used.equals(request.observations().keySet()) || uncertain && !request.acknowledgeUncertainty())throw invalid();
        var configuration=client.configuration(allowed,ReviewRepairIo::currentRemainingNanos);permission(allowed);
        var version=mongo.getConverter().read(ProjectVersion.class,held);
        String context=ArtifactReviewContext.automaticallyReviewableFingerprint(version);
        if(context==null || !(scan.get("scanAttempt") instanceof Integer || scan.get("scanAttempt") instanceof Long))throw invalid();
        long attempt=((Number)scan.get("scanAttempt")).longValue();if(attempt<1 || attempt>Integer.MAX_VALUE)throw invalid();
        var binding=new RemoteReviewBinding(request.projectId().toString(),request.versionId(),scan.getString("scanRequestId"),(int)attempt,
                held.getString("fileUrl"),held.getString("hash"),context,configuration.policyVersion(),configuration.reviewConfigSha256(),null,false,configuration.origin());
        var rawBinding=new Document();mongo.getConverter().write(binding,rawBinding);
        String rule=uncertain?"ACKNOWLEDGED_UNCERTAINTY":"PRIOR_WORK_ACCOUNTED";
        var observationIds=new Document();request.observations().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->observationIds.append(e.getKey(),e.getValue()));
        var payload=new Document("schema",1).append("mutationId",request.mutationId()).append("versionId",request.versionId()).append("heldSha256",captured.sha256())
                .append("heldVersion",new Binary(captured.versionBytes())).append("binding",rawBinding).append("groups",inventory.groups())
                .append("evidence",evidence).append("observations",observationIds).append("acknowledgedUncertainty",request.acknowledgeUncertainty()).append("rule",rule);
        var existing=archive.find(request.id());long created=existing==null?clock.millis():existing.createdAt(),expires=existing==null?Math.addExact(created,lifetimeMillis):existing.expiresAt();live(created,expires);
        var candidate=new ReviewSnapshotArchive.Snapshot(request.id(),request.projectId(),request.versionIndex(),request.actor(),ReviewSnapshotArchive.Action.PROJECT_MUTATION_ADMISSION,created,expires,bytes(payload));
        permission(allowed);
        try{archive.retain(candidate);}catch(RuntimeException uncertainWrite){var found=archive.find(candidate.id());if(found==null || !same(candidate,found))throw uncertainWrite;}
        permission(allowed);if(!reader.isCurrent(captured))throw invalid();
        var result=recoverWithinBudget(request.id(),request.actor(),allowed);live(result.createdAt(),result.expiresAt());return result;
    }
    public Prepared recover(String id,String actor,BooleanSupplier permitted) {return budget.call(allowed->recoverWithinBudget(id,actor,allowed),permitted);}
    Prepared recoverWithinBudget(String id,String actor,BooleanSupplier allowed) {
        permission(allowed);var stored=archive.load(id);
        if(!stored.actorId().equals(actor))throw invalid();
        var result=decodeStored(mongo,stored);permission(allowed);return result;
    }
    static Prepared decodeStored(MongoTemplate mongo,ReviewSnapshotArchive.Snapshot stored) {
        if(stored.action()!=ReviewSnapshotArchive.Action.PROJECT_MUTATION_ADMISSION || stored.expiresAt()-stored.createdAt()>120000)throw invalid();
        var payload=decode(stored.versionBytes());
        if(payload.size()!=11 || !Integer.valueOf(1).equals(payload.get("schema")) || !uuid(payload.getString("mutationId"))
                || !(payload.get("heldVersion") instanceof Binary original) || !digest(original.getData()).equals(payload.get("heldSha256"))
                || !(payload.get("acknowledgedUncertainty") instanceof Boolean ack)
                || !Set.of("PRIOR_WORK_ACCOUNTED","ACKNOWLEDGED_UNCERTAINTY").contains(Objects.toString(payload.get("rule"),""))
                || "ACKNOWLEDGED_UNCERTAINTY".equals(payload.get("rule")) && !ack)throw invalid();
        var held=decode(original.getData());var binding=mongo.getConverter().read(RemoteReviewBinding.class,payload.get("binding",Document.class));
        var scan=held.get("scanResult",Document.class);
        if(binding.jobId()!=null || binding.origin()==null || binding.manualRescan() || !binding.projectId().equals(stored.projectId().toString())
                || !binding.versionId().equals(payload.get("versionId")) || !binding.versionId().equals(held.get("_id"))
                || !binding.requestId().equals(scan.get("scanRequestId")) || binding.attempt()!=((Number)scan.get("scanAttempt")).longValue()
                || !binding.filePath().equals(held.get("fileUrl")) || !binding.artifactSha256().equals(held.get("hash"))
                || !binding.contextSha256().equals(ArtifactReviewContext.automaticallyReviewableFingerprint(mongo.getConverter().read(ProjectVersion.class,held))))throw invalid();
        return new Prepared(stored.id(),digest(stored.versionBytes()),payload.getString("mutationId"),payload.getString("versionId"),payload.getString("heldSha256"),binding,payload.getString("rule"),stored.createdAt(),stored.expiresAt());
    }
    Prepared verifyCurrent(Prepared expected,String actor,BooleanSupplier allowed) {
        if(!expected.equals(recoverWithinBudget(expected.id(),actor,allowed)))throw invalid();
        var stored=archive.load(expected.id());var payload=decode(stored.versionBytes());var ids=new HashMap<String,String>();
        payload.get("observations",Document.class).forEach((key,value)->{if(!(value instanceof String id))throw invalid();ids.put(key,id);});
        var request=new Request(expected.id(),stored.projectId(),stored.versionIndex(),expected.versionId(),expected.heldSha256(),expected.mutationId(),actor,ids,payload.getBoolean("acknowledgedUncertainty"));
        var found=prepareWithinBudget(request,allowed);if(!expected.equals(found))throw invalid();return found;
    }
    private void live(long created,long expires){long now=clock.millis();if(now<created || now>=expires)throw invalid();}
    private static boolean same(ReviewSnapshotArchive.Snapshot a,ReviewSnapshotArchive.Snapshot b){return a.id().equals(b.id()) && a.projectId().equals(b.projectId()) && a.versionIndex()==b.versionIndex() && a.actorId().equals(b.actorId()) && a.action()==b.action() && a.createdAt()==b.createdAt() && a.expiresAt()==b.expiresAt() && Arrays.equals(a.versionBytes(),b.versionBytes());}
    private static Document decode(byte[] value){return new RawBsonDocument(value).decode(new DocumentCodec());}
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();var result=new byte[buffer.remaining()];buffer.get(result);return result;}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static boolean digestValue(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    private static boolean uuid(String value){return value!=null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static void permission(BooleanSupplier allowed){if(!allowed.getAsBoolean())throw new SecurityException("Mutation admission is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Mutation admission evidence changed or is unavailable");}
}
