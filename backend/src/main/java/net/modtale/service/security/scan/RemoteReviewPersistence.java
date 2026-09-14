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

    public ProjectVersion current(String projectId,String versionId,int attempt,String requestId) {
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
        return version;
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
        var query=Query.query(Criteria.where("_id").is(binding.projectId()).and("versions").elemMatch(version));
        query.addCriteria(Criteria.where("$expr").is(new org.bson.Document("$eq",java.util.List.of(new org.bson.Document("$size",new org.bson.Document("$filter",
                new org.bson.Document("input","$versions").append("as","v").append("cond",new org.bson.Document("$eq",java.util.List.of("$$v._id",new org.bson.Document("$literal",binding.versionId())))))),1))));
        return query.collation(Collation.of("simple"));
    }
}
