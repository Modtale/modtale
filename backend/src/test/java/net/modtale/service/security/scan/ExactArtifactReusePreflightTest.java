package net.modtale.service.security.scan;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExactArtifactReusePreflightTest {
    private final ProjectRepository projects=mock(ProjectRepository.class);
    private final StorageService storage=mock(StorageService.class);
    private final WardenClientService scanner=mock(WardenClientService.class);
    private final ScanPersistenceService persistence=mock(ScanPersistenceService.class);
    private final ScanCompletionService completion=mock(ScanCompletionService.class);
    private final ArtifactReviewReuseService reuse=new ArtifactReviewReuseService();
    private final ExactArtifactReusePreflight preflight=new ExactArtifactReusePreflight(projects,storage,scanner,persistence,completion,reuse);
    private final String requestId=UUID.randomUUID().toString();

    private Project fixture() {
        var evidence=ScanEvidenceFixtures.complete(false).getSecurityEvidence();
        var source=new ProjectVersion();source.setId("prior");source.setVersionNumber("1.0");source.setHash(evidence.artifactSha256());
        source.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);source.setApprovedSecurityEvidence(evidence);
        source.setApprovedReviewOrigins(Map.of());source.setSecurityApprovalProjectId("p");
        source.setSecurityApprovedAt(System.currentTimeMillis()-1000);
        source.setApprovedSecurityContextSha256(ArtifactReviewContext.fingerprint(source));
        var target=new ProjectVersion();target.setId("target");target.setHash(evidence.artifactSha256());target.setFileUrl("original.zip");
        var queued=new ScanResult();queued.setStatus(ScanStatus.SCANNING);queued.setScanState("QUEUED");
        queued.setScanRequestId(requestId);queued.setScanAttempt(1);target.setScanResult(queued);
        var project=new Project();project.setId("p");project.setVersions(List.of(source,target));return project;
    }
    private ScanResult scannerEvidence() {
        var result=ScanEvidenceFixtures.complete(false);var evidence=result.getSecurityEvidence();
        result.setSecurityEvidence(new ScanResult.SecurityEvidence(evidence.policyVersion(),evidence.artifactSha256(),
                evidence.contentSha256(),true,false,"SCANNER_EVIDENCE_ONLY",evidence.entryHashes()));
        return result;
    }
    @Test void exactCurrentApprovalUsesScannerEvidenceBeforeAnyReviewJob() {
        var project=fixture();var target=project.getVersions().get(1);var evidence=scannerEvidence();
        when(projects.findById("p")).thenReturn(Optional.of(project));
        when(storage.downloadBounded(eq("original.zip"),anyInt())).thenReturn("artifact".getBytes());
        when(scanner.scanEvidenceFile(any(),eq("original.zip"))).thenReturn(evidence);
        when(persistence.markAttemptRunning("p","target",1,requestId)).thenReturn(true);
        when(completion.handleCompletedReuseScan(eq("p"),eq("target"),eq(1),eq(false),same(evidence),eq(requestId))).thenReturn(true);

        assertTrue(preflight.tryComplete("p",target,evidence.getSecurityEvidence().policyVersion()));
        assertEquals("1.0",evidence.getReusedReviewVersion());
        verify(scanner).scanEvidenceFile(any(),eq("original.zip"));
        verify(completion).handleCompletedReuseScan(eq("p"),eq("target"),eq(1),eq(false),same(evidence),eq(requestId));
    }
    @Test void changedOrUnverifiedScannerEvidenceFallsThroughWithoutCompletion() {
        for(String scenario:List.of("changed-content","unverified","wrong-policy","wrong-artifact","wrong-route","forged-clearance","incomplete")) {
            clearInvocations(projects,storage,scanner,persistence,completion);
            var project=fixture();var target=project.getVersions().get(1);var evidence=scannerEvidence();
            var old=evidence.getSecurityEvidence();
            switch(scenario) {
                case "changed-content" -> evidence.setSecurityEvidence(new ScanResult.SecurityEvidence(old.policyVersion(),old.artifactSha256(),
                        "f".repeat(64),true,false,old.reviewState(),Map.of("Mod.class","f".repeat(64))));
                case "unverified" -> evidence.setArtifactVerified(false);
                case "wrong-policy" -> evidence.setSecurityEvidence(new ScanResult.SecurityEvidence("older",old.artifactSha256(),
                        old.contentSha256(),true,false,old.reviewState(),old.entryHashes()));
                case "wrong-artifact" -> evidence.setSecurityEvidence(new ScanResult.SecurityEvidence(old.policyVersion(),"f".repeat(64),
                        old.contentSha256(),true,false,old.reviewState(),old.entryHashes()));
                case "wrong-route" -> evidence.setSecurityEvidence(new ScanResult.SecurityEvidence(old.policyVersion(),old.artifactSha256(),
                        old.contentSha256(),true,false,"COMPLETED",old.entryHashes()));
                case "forged-clearance" -> evidence.setSecurityEvidence(new ScanResult.SecurityEvidence(old.policyVersion(),old.artifactSha256(),
                        old.contentSha256(),true,true,old.reviewState(),old.entryHashes()));
                case "incomplete" -> evidence.getSummary().setRecoverableErrors(1);
            }
            when(projects.findById("p")).thenReturn(Optional.of(project));
            when(storage.downloadBounded(eq("original.zip"),anyInt())).thenReturn("artifact".getBytes());
            when(scanner.scanEvidenceFile(any(),eq("original.zip"))).thenReturn(evidence);
            assertFalse(preflight.tryComplete("p",target,ScanEvidenceFixtures.complete(false).getSecurityEvidence().policyVersion()),scenario);
            verifyNoInteractions(persistence,completion);
        }
    }
    @Test void noApprovedSourceSkipsDownloadAndProvider() {
        var project=fixture();project.getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);
        when(projects.findById("p")).thenReturn(Optional.of(project));
        assertFalse(preflight.tryComplete("p",project.getVersions().get(1),scannerEvidence().getSecurityEvidence().policyVersion()));
        verifyNoInteractions(storage,scanner,persistence,completion);
    }
    @Test void alreadyClaimedSynchronousAttemptCanReuseWithoutAnotherDownloadOrClaim() {
        var project=fixture();var target=project.getVersions().get(1);
        target.getScanResult().setScanState("SCANNING");var evidence=scannerEvidence();byte[] bytes={1,2,3};
        when(projects.findById("p")).thenReturn(Optional.of(project));
        when(scanner.currentPolicyVersion()).thenReturn(evidence.getSecurityEvidence().policyVersion());
        when(scanner.scanEvidenceFile(bytes,"original.zip")).thenReturn(evidence);
        when(completion.handleCompletedReuseScan("p","target",1,false,evidence,requestId)).thenReturn(true);

        assertTrue(preflight.tryCompleteRunning("p","target",1,requestId,"original.zip",false,bytes));
        assertEquals("1.0",evidence.getReusedReviewVersion());
        verifyNoInteractions(storage,persistence);
        verify(scanner).scanEvidenceFile(bytes,"original.zip");
    }
    @Test void alreadyClaimedAttemptWithChangedStoragePathNeverContactsScanner() {
        var project=fixture();project.getVersions().get(1).getScanResult().setScanState("SCANNING");
        when(projects.findById("p")).thenReturn(Optional.of(project));
        assertFalse(preflight.tryCompleteRunning("p","target",1,requestId,"other.zip",false,new byte[]{1}));
        verifyNoInteractions(scanner,storage,persistence,completion);
    }
    @Test void claimedAttemptWithRemoteOwnershipCannotUseSynchronousReuse() {
        var project=fixture();var target=project.getVersions().get(1);
        target.getScanResult().setScanState("SCANNING");
        target.getScanResult().setRemotePoll(new ScanResult.RemoteReviewPoll(UUID.randomUUID().toString(),
                new Date(System.currentTimeMillis()+1000),new Date(0)));
        when(projects.findById("p")).thenReturn(Optional.of(project));
        assertFalse(preflight.tryCompleteRunning("p","target",1,requestId,"original.zip",false,new byte[]{1}));
        verifyNoInteractions(scanner,storage,persistence,completion);
    }
}
