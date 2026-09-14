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
        var version = target(observed,binding).and("scanResult.scanState").in("SCANNING","REMOTE_REVIEW")
                .orOperator(Criteria.where("scanResult.remoteReview").is(null),Criteria.where("scanResult.remoteReview").is(binding));
        return mongo.updateFirst(query(binding,version), new Update()
                .set("versions.$.scanResult.remoteReview",binding)
                .set("versions.$.scanResult.scanState","REMOTE_REVIEW"),Project.class).getMatchedCount() == 1;
    }

    public boolean attachJob(ProjectVersion observed, RemoteReviewBinding binding, String jobId) {
        if (binding == null || !matches(observed,binding)) return false;
        var attached = binding.withJobId(jobId);
        var version = target(observed,binding).and("scanResult.scanState").is("REMOTE_REVIEW")
                .orOperator(Criteria.where("scanResult.remoteReview").is(binding),Criteria.where("scanResult.remoteReview").is(attached));
        return mongo.updateFirst(query(binding,version),new Update()
                .set("versions.$.scanResult.remoteReview",attached),Project.class).getMatchedCount() == 1;
    }

    private static boolean matches(ProjectVersion observed,RemoteReviewBinding binding) {
        if (observed == null || observed.getScanResult() == null) return false;
        var scan=observed.getScanResult();
        return Objects.equals(observed.getId(),binding.versionId()) && Objects.equals(observed.getHash(),binding.artifactSha256())
                && Objects.equals(observed.getFileUrl(),binding.filePath())
                && Objects.equals(ArtifactReviewContext.automaticallyReviewableFingerprint(observed),binding.contextSha256())
                && Objects.equals(scan.getScanRequestId(),binding.requestId()) && scan.getScanAttempt() == binding.attempt();
    }
    private static Criteria target(ProjectVersion observed,RemoteReviewBinding binding) {
        return ArtifactReviewContext.bindSnapshot(Criteria.where("_id").is(binding.versionId())
                .and("hash").is(binding.artifactSha256()).and("fileUrl").is(binding.filePath())
                .and("reviewStatus").is(ProjectVersion.ReviewStatus.PENDING)
                .and("scanResult.status").is(ScanStatus.SCANNING)
                .and("scanResult.scanRequestId").is(binding.requestId())
                .and("scanResult.scanAttempt").is(binding.attempt()),observed);
    }
    private static Query query(RemoteReviewBinding binding,Criteria version) {
        return Query.query(Criteria.where("_id").is(binding.projectId()).and("versions").elemMatch(version));
    }
}
