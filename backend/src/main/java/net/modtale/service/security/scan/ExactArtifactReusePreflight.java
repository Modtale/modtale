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
                || observed.getId()==null || observed.getHash()==null || observed.getFileUrl()==null
                || observed.getScanResult().getScanRequestId()==null) return false;
        Project project=projects.findById(projectId).orElse(null);
        if(!reuse.hasPotentialExactSource(project,observed.getId(),policyVersion))return false;
        String requestId=observed.getScanResult().getScanRequestId();int attempt=observed.getScanResult().getScanAttempt();
        if(attempt<1)return false;
        try {
            byte[] bytes=storage.downloadBounded(observed.getFileUrl(),StorageService.MAX_REVIEW_ARTIFACT_BYTES);
            var result=scanner.scanEvidenceFile(bytes,observed.getFileUrl());
            if(!ArtifactClearancePolicy.complete(result) || result.getSecurityEvidence().clearanceGranted()
                    || !"SCANNER_EVIDENCE_ONLY".equals(result.getSecurityEvidence().reviewState())
                    || !Objects.equals(policyVersion,result.getSecurityEvidence().policyVersion())
                    || !Objects.equals(observed.getHash(),result.getSecurityEvidence().artifactSha256()))return false;
            project=projects.findById(projectId).orElse(null);
            if(!reuse.hasPotentialExactSource(project,observed.getId(),policyVersion))return false;
            reuse.annotate(project,observed.getId(),result);
            if(result.getReusedReviewVersion()==null)return false;
            if(!persistence.markAttemptRunning(projectId,observed.getId(),attempt,requestId))return false;
            return completion.handleCompletedReuseScan(projectId,observed.getId(),attempt,
                    observed.getScanResult().isManualRescan(),result,requestId);
        } catch(RuntimeException unavailable) {
            return false;
        }
    }
}
