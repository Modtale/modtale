package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class RemoteReviewPersistence {
    private final MongoTemplate mongo;
    public RemoteReviewPersistence(MongoTemplate mongo) {
        this.mongo = new MongoTemplate(mongo.getMongoDatabaseFactory(),mongo.getConverter());
        this.mongo.setWriteConcern(com.mongodb.WriteConcern.MAJORITY.withJournal(true)
                .withWTimeout(10,java.util.concurrent.TimeUnit.SECONDS));
    }

    public boolean bind(ProjectVersion observed, RemoteReviewBinding binding) {
        if (binding == null || binding.jobId() != null || !matches(observed,binding)) return false;
        var version = target(observed,binding).and("scanResult.scanState").in("QUEUED","SCANNING","REMOTE_REVIEW")
                .orOperator(Criteria.where("scanResult.remoteReview").is(null),Criteria.where("scanResult.remoteReview").is(binding));
        return mongo.updateFirst(query(binding,version), new Update()
                .set("versions.$.scanResult.remoteReview",binding)
                .set("versions.$.scanResult.scanState","REMOTE_REVIEW"),Project.class).getMatchedCount() == 1;
    }

    public boolean attachJob(ProjectVersion observed, RemoteReviewBinding binding, String jobId) {
        if (binding == null || !matches(observed,binding)) return false;
        var attached = binding.withJobId(jobId);
        var version = target(observed,binding).and("scanResult.scanState").is("REMOTE_REVIEW").and("scanResult.remotePoll").exists(false)
                .orOperator(Criteria.where("scanResult.remoteReview").is(binding),Criteria.where("scanResult.remoteReview").is(attached));
        return mongo.updateFirst(query(binding,version),new Update()
                .set("versions.$.scanResult.remoteReview",attached),Project.class).getMatchedCount() == 1;
    }

    public boolean finishUnsupportedContext(String projectId,ProjectVersion observed) {
        if(projectId==null || observed==null || observed.getId()==null || observed.getScanResult()==null
                || observed.getReviewStatus()!=ProjectVersion.ReviewStatus.PENDING
                || ArtifactReviewContext.automaticallyReviewableFingerprint(observed)!=null)return false;
        var scan=observed.getScanResult();
        if(scan.getStatus()!=ScanStatus.SCANNING || !java.util.Set.of("QUEUED","SCANNING").contains(Objects.toString(scan.getScanState(),""))
                || scan.getRemoteReview()!=null || scan.getScanAttempt()<1 || scan.getScanRequestId()==null
                || !scan.getScanRequestId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))return false;
        var version=ArtifactReviewContext.bindSnapshot(Criteria.where("_id").is(observed.getId())
                .and("hash").is(observed.getHash()).and("fileUrl").is(observed.getFileUrl())
                .and("reviewStatus").is(ProjectVersion.ReviewStatus.PENDING)
                .and("scanResult.status").is(ScanStatus.SCANNING).and("scanResult.scanState").is(scan.getScanState()).and("scanResult.verdict").is(scan.getVerdict())
                .and("scanResult.scanRequestId").is(scan.getScanRequestId()).and("scanResult.scanAttempt").is(scan.getScanAttempt())
                .and("scanResult.manualRescan").in(scan.isManualRescan()?java.util.List.of(true):java.util.Arrays.asList(false,null))
                .and("scanResult.remoteReview").is(null).and("scanResult.remotePoll").exists(false),observed);
        return mongo.updateFirst(query(projectId,observed.getId(),version),new Update()
                .set("versions.$.scanResult.status",ScanStatus.FAILED).set("versions.$.scanResult.scanState","REMOTE_UNSUPPORTED_CONTEXT")
                .set("versions.$.scanResult.verdict","BLOCK".equals(scan.getVerdict())?"BLOCK":"REVIEW")
                .set("versions.$.scanResult.scanTimestamp",System.currentTimeMillis())
                .set("versions.$.scanResult.securityEvidence",null).set("versions.$.scanResult.artifactVerified",false)
                .set("versions.$.scanResult.reviewedContextSha256",null).set("versions.$.scanResult.reusedReviewVersion",null)
                .set("versions.$.scanResult.reusedReviewOrigins",null).set("versions.$.scanResult.holdUntilTimestamp",0)
                .set("versions.$.scanResult.reviewerNotes",java.util.List.of("Security review cannot cover this version's current dependencies, runtime metadata or supplemental content. Resolve the review context before requesting another scan. No security clearance was granted.")),Project.class)
                .getModifiedCount()==1;
    }

    private record Current(org.bson.Document projectQuery,org.bson.Document rawVersion,ProjectVersion version) {}
    public ProjectVersion current(String projectId,String versionId,int attempt,String requestId) {
        var snapshot=readCurrent(projectId,versionId,attempt,requestId);return snapshot==null?null:snapshot.version();
    }
    private Current readCurrent(String projectId,String versionId,int attempt,String requestId) {
        if(projectId==null || versionId==null || attempt<1 || requestId==null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))return null;
        var entity=mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var query=new org.springframework.data.mongodb.core.convert.QueryMapper(mongo.getConverter()).getMappedObject(new org.bson.Document("_id",projectId),entity);
        var raw=mongo.getCollection(mongo.getCollectionName(Project.class)).withReadPreference(com.mongodb.ReadPreference.primary()).withReadConcern(com.mongodb.ReadConcern.MAJORITY)
                .find(query).collation(com.mongodb.client.model.Collation.builder().locale("simple").build()).maxTime(5,java.util.concurrent.TimeUnit.SECONDS).first();
        if(raw==null)return null;var versions=raw.getList("versions",org.bson.Document.class);if(versions==null)return null;
        var selected=versions.stream().filter(v->versionId.equals(v.get("_id"))).toList();if(selected.size()!=1)return null;
        ProjectVersion version;try{version=mongo.getConverter().read(ProjectVersion.class,selected.getFirst());}catch(RuntimeException invalid){return null;}
        var scan=version.getScanResult();
        if(version.getReviewStatus()!=ProjectVersion.ReviewStatus.PENDING || scan==null || scan.getStatus()!=ScanStatus.SCANNING
                || !java.util.Set.of("QUEUED","SCANNING","REMOTE_REVIEW").contains(Objects.toString(scan.getScanState(),""))
                || !requestId.equals(scan.getScanRequestId()) || attempt!=scan.getScanAttempt())return null;
        return new Current(query,selected.getFirst(),version);
    }
    public String finishBrokenBinding(String projectId,String versionId,int attempt,String requestId) {
        var snapshot=readCurrent(projectId,versionId,attempt,requestId);if(snapshot==null)return null;
        var scan=snapshot.version().getScanResult();var binding=scan.getRemoteReview();
        String reason;
        if(binding==null) {
            if(!"REMOTE_REVIEW".equals(scan.getScanState()))return null;
            reason="REMOTE_BINDING_MISSING";
        } else {
            if(projectId.equals(binding.projectId()) && "REMOTE_REVIEW".equals(scan.getScanState()) && matches(snapshot.version(),binding))return null;
            reason="REMOTE_BINDING_MISMATCH";
        }
        var uniqueness=new org.bson.Document("$eq",java.util.List.of(new org.bson.Document("$size",new org.bson.Document("$filter",
                new org.bson.Document("input","$versions").append("as","v").append("cond",new org.bson.Document("$eq",java.util.List.of("$$v._id",new org.bson.Document("$literal",versionId)))))),1));
        var query=new org.bson.Document(snapshot.projectQuery()).append("versions",snapshot.rawVersion()).append("$expr",uniqueness);
        var fields=new org.bson.Document("status","FAILED").append("scanState",reason)
                .append("verdict","BLOCK".equals(scan.getVerdict())?"BLOCK":"REVIEW").append("scanTimestamp",new org.bson.Document("$toLong","$$NOW"))
                .append("securityEvidence",null).append("artifactVerified",false).append("reviewedContextSha256",null)
                .append("reusedReviewVersion",null).append("reusedReviewOrigins",null).append("holdUntilTimestamp",0)
                .append("remotePoll",null).append("reviewerNotes",java.util.List.of("The stored review job no longer matches this version or its binding is missing. Repair review state before requesting another scan. No security clearance was granted."));
        var updated=new org.bson.Document("$mergeObjects",java.util.List.of("$$v",new org.bson.Document("scanResult",new org.bson.Document("$mergeObjects",java.util.List.of("$$v.scanResult",fields)))));
        var patch=java.util.List.of(new org.bson.Document("$set",new org.bson.Document("versions",new org.bson.Document("$map",new org.bson.Document("input","$versions").append("as","v")
                .append("in",new org.bson.Document("$cond",java.util.List.of(new org.bson.Document("$eq",java.util.List.of("$$v",new org.bson.Document("$literal",snapshot.rawVersion()))),updated,"$$v")))))));
        var result=mongo.getCollection(mongo.getCollectionName(Project.class)).withWriteConcern(com.mongodb.WriteConcern.MAJORITY.withJournal(true)
                .withWTimeout(10,java.util.concurrent.TimeUnit.SECONDS)).updateOne(query,patch,new com.mongodb.client.model.UpdateOptions().collation(com.mongodb.client.model.Collation.builder().locale("simple").build()));
        return result.getModifiedCount()==1?reason:null;
    }
    public RemoteReviewBinding retained(String projectId,String versionId,int attempt,String requestId) {
        var current=current(projectId,versionId,attempt,requestId);if(current==null)return null;
        var binding=current.getScanResult().getRemoteReview();
        return binding!=null && projectId.equals(binding.projectId()) && "REMOTE_REVIEW".equals(current.getScanResult().getScanState()) && matches(current,binding)?binding:null;
    }

    private static boolean matches(ProjectVersion observed,RemoteReviewBinding binding) {
        if (observed == null || observed.getScanResult() == null) return false;
        var scan=observed.getScanResult();
        return Objects.equals(observed.getId(),binding.versionId()) && Objects.equals(observed.getHash(),binding.artifactSha256())
                && Objects.equals(observed.getFileUrl(),binding.filePath())
                && Objects.equals(ArtifactReviewContext.automaticallyReviewableFingerprint(observed),binding.contextSha256())
                && Objects.equals(scan.getScanRequestId(),binding.requestId()) && scan.getScanAttempt() == binding.attempt() && scan.isManualRescan()==binding.manualRescan();
    }
    private static Criteria target(ProjectVersion observed,RemoteReviewBinding binding) {
        return ArtifactReviewContext.bindSnapshot(Criteria.where("_id").is(binding.versionId())
                .and("hash").is(binding.artifactSha256()).and("fileUrl").is(binding.filePath())
                .and("reviewStatus").is(ProjectVersion.ReviewStatus.PENDING)
                .and("scanResult.status").is(ScanStatus.SCANNING)
                .and("scanResult.scanRequestId").is(binding.requestId())
                .and("scanResult.scanAttempt").is(binding.attempt())
                .and("scanResult.manualRescan").in(binding.manualRescan()?java.util.List.of(true):java.util.Arrays.asList(false,null)),observed);
    }
    private static Query query(RemoteReviewBinding binding,Criteria version) {
        return query(binding.projectId(),binding.versionId(),version);
    }
    private static Query query(String projectId,String versionId,Criteria version) {
        var query=Query.query(Criteria.where("_id").is(projectId).and("versions").elemMatch(version));
        query.addCriteria(Criteria.where("$expr").is(new org.bson.Document("$eq",java.util.List.of(new org.bson.Document("$size",new org.bson.Document("$filter",
                new org.bson.Document("input","$versions").append("as","v").append("cond",new org.bson.Document("$eq",java.util.List.of("$$v._id",new org.bson.Document("$literal",versionId)))))),1))));
        return query.collation(Collation.of("simple"));
    }
}
