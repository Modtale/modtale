package net.modtale.service.security.scan;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import net.modtale.model.project.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public final class RemoteReviewPollStore {
    public record Claim(RemoteReviewBinding binding,String token) {
        public Claim { if(binding==null || token==null || !token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid poll claim"); }
    }
    private record Snapshot(Document project,Document version,Document scan,Document poll) {}
    private static final com.mongodb.client.model.Collation BINARY=com.mongodb.client.model.Collation.builder().locale("simple").build();
    private final MongoTemplate mongo;
    private final MongoCollection<Document> projects;
    public RemoteReviewPollStore(MongoTemplate mongo) {
        this.mongo=mongo;
        projects=mongo.getCollection(mongo.getCollectionName(Project.class)).withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.MAJORITY).withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(10,TimeUnit.SECONDS));
    }
    public Claim claim(RemoteReviewBinding binding,long leaseMillis) {
        if(leaseMillis<1000 || leaseMillis>120000)throw new IllegalArgumentException("Invalid poll lease");
        if(binding.origin()==null)return null;
        var snapshot=read(binding);if(snapshot==null)return null;
        String token=UUID.randomUUID().toString();Date lease=date(snapshot.poll(),"leaseUntil"),next=date(snapshot.poll(),"nextPollAt");
        if(lease==null || next==null)return null;
        var query=query(snapshot,binding,List.of(new Document("$lte",List.of(lease,"$$NOW")),new Document("$lte",List.of(next,"$$NOW"))));
        var poll=new Document("token",token).append("leaseUntil",new Document("$add",List.of("$$NOW",leaseMillis))).append("nextPollAt",literal(next));
        if(projects.updateOne(query,patch(snapshot,new Document("remotePoll",poll)),new com.mongodb.client.model.UpdateOptions().collation(BINARY)).getModifiedCount()!=1)return null;
        return new Claim(binding,token);
    }
    public boolean isCurrent(Claim claim) {
        var snapshot=liveSnapshot(claim);if(snapshot==null)return false;
        return projects.find(liveQuery(snapshot,claim)).collation(BINARY).projection(new Document("_id",1)).maxTime(5,TimeUnit.SECONDS).first()!=null;
    }
    public Claim attachJob(Claim claim,String jobId) {
        var attached=claim.binding().withJobId(jobId);var snapshot=liveSnapshot(claim);if(snapshot==null)return null;
        var binding=new Document(snapshot.scan().get("remoteReview",Document.class));binding.put("jobId",jobId);
        if(projects.updateOne(liveQuery(snapshot,claim),patch(snapshot,new Document("remoteReview",literal(binding))),new com.mongodb.client.model.UpdateOptions().collation(BINARY)).getMatchedCount()!=1)return null;
        return new Claim(attached,claim.token());
    }
    public boolean release(Claim claim,long delayMillis) {
        if(delayMillis<100 || delayMillis>3600000)throw new IllegalArgumentException("Invalid poll delay");
        var snapshot=liveSnapshot(claim);if(snapshot==null)return false;
        var poll=new Document("token",null).append("leaseUntil",literal(new Date(0))).append("nextPollAt",new Document("$add",List.of("$$NOW",delayMillis)));
        return projects.updateOne(liveQuery(snapshot,claim),patch(snapshot,new Document("remotePoll",poll)),new com.mongodb.client.model.UpdateOptions().collation(BINARY)).getModifiedCount()==1;
    }
    public boolean recordStatusAndRelease(Claim claim,RemoteReviewClient.Status status,long delayMillis) {
        if(delayMillis<100 || delayMillis>3600000)throw new IllegalArgumentException("Invalid poll delay");
        if(status==null || !Objects.equals(claim.binding().jobId(),status.jobId()) || status.jobId()==null
                || !Set.of("QUEUED","RUNNING","COMPLETED","UPLOADING","AWAITING_UPLOAD").contains(status.state())
                || status.createdAt()<=0 || status.expiresAt()<=status.createdAt() || status.workState()!=null && status.workState().length()>128
                || Set.of("QUEUED","RUNNING","COMPLETED","HELD").contains(status.state()) && !status.artifactRetained())return false;
        var snapshot=liveSnapshot(claim);if(snapshot==null)return false;
        var value=new Document("jobId",status.jobId()).append("state",status.state()).append("artifactRetained",status.artifactRetained())
                .append("createdAt",status.createdAt()).append("expiresAt",status.expiresAt()).append("workState",status.workState());
        var poll=new Document("token",null).append("leaseUntil",literal(new Date(0))).append("nextPollAt",new Document("$add",List.of("$$NOW",delayMillis)));
        return projects.updateOne(liveQuery(snapshot,claim),patch(snapshot,new Document("remoteStatus",literal(value)).append("remotePoll",poll)),
                new com.mongodb.client.model.UpdateOptions().collation(BINARY)).getModifiedCount()==1;
    }
    public boolean finishUnavailable(Claim claim,RemoteReviewClient.Status status) {
        if(status==null || status.state()==null || !Set.of("CANCELLED","EXPIRED","HELD").contains(status.state())
                || status.jobId()==null || !status.jobId().equals(claim.binding().jobId())
                || status.createdAt()<=0 || status.expiresAt()<=status.createdAt()
                || status.workState()!=null && status.workState().length()>128
                || "HELD".equals(status.state()) && !status.artifactRetained())return false;
        var snapshot=liveSnapshot(claim);if(snapshot==null)return false;
        var value=new Document("jobId",status.jobId()).append("state",status.state()).append("artifactRetained",status.artifactRetained())
                .append("createdAt",status.createdAt()).append("expiresAt",status.expiresAt()).append("workState",status.workState());
        var fields=new Document("remoteStatus",literal(value)).append("remotePoll",literal(null))
                .append("status","FAILED").append("scanState","REMOTE_"+status.state()).append("verdict","BLOCK".equals(snapshot.scan().get("verdict"))?"BLOCK":"REVIEW")
                .append("scanTimestamp",new Document("$toLong","$$NOW"))
                .append("securityEvidence",literal(null)).append("artifactVerified",false)
                .append("reviewedContextSha256",literal(null)).append("reusedReviewVersion",literal(null))
                .append("reusedReviewOrigins",literal(null)).append("holdUntilTimestamp",0)
                .append("reviewerNotes",literal(List.of("Security review did not complete ("+status.state().toLowerCase(Locale.ROOT)+"). No security clearance was granted.")));
        return projects.updateOne(liveQuery(snapshot,claim),patch(snapshot,fields),new com.mongodb.client.model.UpdateOptions().collation(BINARY)).getModifiedCount()==1;
    }
    public boolean finishContextConflict(Claim claim) {
        var snapshot=liveSnapshot(claim);if(snapshot==null)return false;
        var fields=new Document("remotePoll",literal(null)).append("status","FAILED").append("scanState","REMOTE_CONTEXT_CONFLICT")
                .append("verdict","BLOCK".equals(snapshot.scan().get("verdict"))?"BLOCK":"REVIEW")
                .append("scanTimestamp",new Document("$toLong","$$NOW")).append("securityEvidence",literal(null)).append("artifactVerified",false)
                .append("reviewedContextSha256",literal(null)).append("reusedReviewVersion",literal(null)).append("reusedReviewOrigins",literal(null)).append("holdUntilTimestamp",0)
                .append("reviewerNotes",literal(List.of("The review service rejected the stored request context or service identity. Reconcile the original job before requesting another scan. No security clearance was granted.")));
        return projects.updateOne(liveQuery(snapshot,claim),patch(snapshot,fields),new com.mongodb.client.model.UpdateOptions().collation(BINARY)).getModifiedCount()==1;
    }
    private Snapshot liveSnapshot(Claim claim) {
        var snapshot=read(claim.binding());return snapshot!=null && snapshot.poll()!=null && claim.token().equals(snapshot.poll().getString("token"))?snapshot:null;
    }
    private Document liveQuery(Snapshot snapshot,Claim claim) {
        return query(snapshot,claim.binding(),List.of(new Document("$gt",List.of(date(snapshot.poll(),"leaseUntil"),"$$NOW"))));
    }
    private Snapshot read(RemoteReviewBinding binding) {
        Objects.requireNonNull(binding);
        var entity=mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var id=new QueryMapper(mongo.getConverter()).getMappedObject(new Document("_id",binding.projectId()),entity);
        var project=projects.find(id).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();if(project==null)return null;
        var versions=project.getList("versions",Document.class);if(versions==null)return null;
        var candidates=versions.stream().filter(v->binding.versionId().equals(v.get("_id"))).toList();if(candidates.size()!=1)return null;
        var raw=candidates.getFirst();ProjectVersion version;
        try {version=mongo.getConverter().read(ProjectVersion.class,raw);}catch(RuntimeException invalid){return null;}
        var scan=version.getScanResult();
        if(version.getReviewStatus()!=ProjectVersion.ReviewStatus.PENDING || scan==null || scan.getStatus()!=ScanStatus.SCANNING
                || !"REMOTE_REVIEW".equals(scan.getScanState()) || !binding.equals(scan.getRemoteReview())
                || !binding.requestId().equals(scan.getScanRequestId()) || binding.attempt()!=scan.getScanAttempt() || binding.manualRescan()!=scan.isManualRescan()
                || !binding.artifactSha256().equals(version.getHash()) || !binding.filePath().equals(version.getFileUrl())
                || !binding.contextSha256().equals(ArtifactReviewContext.automaticallyReviewableFingerprint(version)))return null;
        var rawScan=raw.get("scanResult",Document.class);Document poll;
        try {poll=rawScan.get("remotePoll",Document.class);}catch(ClassCastException invalid){return null;}
        if(poll!=null && (poll.size()!=3 || !poll.containsKey("token") || date(poll,"leaseUntil")==null || date(poll,"nextPollAt")==null
                || poll.get("token")!=null && !(poll.get("token") instanceof String token && token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))))return null;
        if(rawScan.containsKey("remotePoll") && poll==null)return null;
        return new Snapshot(id,raw,rawScan,poll);
    }
    private static Date date(Document poll,String key) {
        if(poll==null)return new Date(0);return poll.get(key) instanceof Date value?value:null;
    }
    private static Document query(Snapshot snapshot,RemoteReviewBinding binding,List<Document> timing) {
        var clauses=new ArrayList<Document>(timing);
        clauses.add(new Document("$eq",List.of(new Document("$size",new Document("$filter",new Document("input","$versions").append("as","v")
                .append("cond",new Document("$eq",List.of("$$v._id",literal(binding.versionId())))))),1)));
        return new Document(snapshot.project()).append("versions",snapshot.version()).append("$expr",new Document("$and",clauses));
    }
    private static List<Document> patch(Snapshot snapshot,Document scanFields) {
        var updated=new Document("$mergeObjects",List.of("$$v",new Document("scanResult",new Document("$mergeObjects",List.of("$$v.scanResult",scanFields)))));
        return List.of(new Document("$set",new Document("versions",new Document("$map",new Document("input","$versions").append("as","v")
                .append("in",new Document("$cond",List.of(new Document("$eq",List.of("$$v",literal(snapshot.version()))),updated,"$$v")))))));
    }
    private static Document literal(Object value){return new Document("$literal",value);}
}
