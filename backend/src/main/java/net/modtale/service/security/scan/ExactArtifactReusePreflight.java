package net.modtale.service.security.scan;

import java.util.Objects;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.storage.StorageService;
import org.springframework.stereotype.Service;

@Service
public final class ExactArtifactReusePreflight {
    private final ProjectRepository projects;
    private final StorageService storage;
    private final WardenClientService scanner;
    private final ScanPersistenceService persistence;
    private final ScanCompletionService completion;
    private final ArtifactReviewReuseService reuse;

    public ExactArtifactReusePreflight(ProjectRepository projects, StorageService storage, WardenClientService scanner,
            ScanPersistenceService persistence, ScanCompletionService completion, ArtifactReviewReuseService reuse) {
        this.projects=projects;this.storage=storage;this.scanner=scanner;this.persistence=persistence;
        this.completion=completion;this.reuse=reuse;
    }

    public boolean tryComplete(String projectId, ProjectVersion observed, String policyVersion) {
        if(observed==null || observed.getScanResult()==null || observed.getScanResult().getStatus()!=ScanStatus.SCANNING
                || !"QUEUED".equals(observed.getScanResult().getScanState()) || observed.getScanResult().getRemoteReview()!=null
                || observed.getScanResult().getRemotePoll()!=null
                || observed.getId()==null || observed.getHash()==null || observed.getFileUrl()==null
                || observed.getScanResult().getScanRequestId()==null) return false;
        Project project=projects.findById(projectId).orElse(null);
        if(!reuse.hasPotentialExactSource(project,observed.getId(),policyVersion))return false;
        String requestId=observed.getScanResult().getScanRequestId();int attempt=observed.getScanResult().getScanAttempt();
        if(attempt<1)return false;
        try {
            byte[] bytes=storage.downloadBounded(observed.getFileUrl(),StorageService.MAX_REVIEW_ARTIFACT_BYTES);
            return inspectAndComplete(projectId,observed,policyVersion,bytes,true);
        } catch(RuntimeException unavailable) {
            return false;
        }
    }

    public boolean tryCompleteRunning(String projectId,String versionId,int attempt,String requestId,
            String filePath,boolean manualRescan,byte[] bytes) {
        try {
            Project project=projects.findById(projectId).orElse(null);
            if(project==null || project.getVersions()==null || versionId==null || attempt<1 || filePath==null)return false;
            var targets=project.getVersions().stream().filter(Objects::nonNull)
                    .filter(version->versionId.equals(version.getId())).toList();
            if(targets.size()!=1)return false;
            var observed=targets.getFirst();var scan=observed.getScanResult();
            if(scan==null || observed.getReviewStatus()!=ProjectVersion.ReviewStatus.PENDING
                    || scan.getStatus()!=ScanStatus.SCANNING || !"SCANNING".equals(scan.getScanState())
                    || scan.getRemoteReview()!=null || scan.getRemotePoll()!=null
                    || scan.getScanAttempt()!=attempt || !Objects.equals(scan.getScanRequestId(),requestId)
                    || scan.isManualRescan()!=manualRescan || !filePath.equals(observed.getFileUrl())
                    || observed.getHash()==null || project.getVersions().stream().noneMatch(source->source!=null
                        && !versionId.equals(source.getId()) && source.getReviewStatus()==ProjectVersion.ReviewStatus.APPROVED
                        && observed.getHash().equals(source.getHash())))return false;
            String policyVersion=scanner.currentPolicyVersion();
            if(!reuse.hasPotentialExactSource(project,versionId,policyVersion))return false;
            return inspectAndComplete(projectId,observed,policyVersion,bytes,false);
        }catch(RuntimeException unavailable){return false;}
    }

    private boolean inspectAndComplete(String projectId,ProjectVersion observed,String policyVersion,byte[] bytes,boolean queued) {
        var result=scanner.scanEvidenceFile(bytes,observed.getFileUrl());
        if(!ArtifactClearancePolicy.complete(result) || result.getSecurityEvidence().clearanceGranted()
                || !"SCANNER_EVIDENCE_ONLY".equals(result.getSecurityEvidence().reviewState())
                || !Objects.equals(policyVersion,result.getSecurityEvidence().policyVersion())
                || !Objects.equals(observed.getHash(),result.getSecurityEvidence().artifactSha256()))return false;
        var project=projects.findById(projectId).orElse(null);
        if(!reuse.hasPotentialExactSource(project,observed.getId(),policyVersion))return false;
        reuse.annotate(project,observed.getId(),result);
        if(result.getReusedReviewVersion()==null)return false;
        var scan=observed.getScanResult();
        if(queued && !persistence.markAttemptRunning(projectId,observed.getId(),scan.getScanAttempt(),scan.getScanRequestId()))return false;
        return completion.handleCompletedReuseScan(projectId,observed.getId(),scan.getScanAttempt(),
                scan.isManualRescan(),result,scan.getScanRequestId());
    }
}
