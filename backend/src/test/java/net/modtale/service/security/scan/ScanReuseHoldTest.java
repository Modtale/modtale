package net.modtale.service.security.scan;

import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.*;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.communication.*;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScanReuseHoldTest {
    private void verifyOutcome(boolean dependencies, boolean currentPolicy) {
        var result=ScanEvidenceFixtures.complete(false);
        var issue=new ScanResult.ScanIssue();issue.setType("RuntimeExec");issue.setSeverity("HIGH");
        issue.setDescription("Process execution evidence");issue.setFilePath("Mod.class");issue.setLineStart(1);issue.setLineEnd(1);
        result.setIssues(new ArrayList<>(List.of(issue)));
        var prior=new ProjectVersion();prior.setId("prior");prior.setVersionNumber("1.0");
        prior.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);prior.setHash(result.getSecurityEvidence().artifactSha256());
        prior.setApprovedSecurityEvidence(result.getSecurityEvidence());prior.setApprovedReviewOrigins(Map.of());
        prior.setSecurityApprovalProjectId("p");prior.setSecurityApprovedAt(System.currentTimeMillis()-1000);
        var target=new ProjectVersion();target.setId("current");target.setVersionNumber("1.1");target.setHash(prior.getHash());
        if(dependencies) for(var version:List.of(prior,target)) version.setDependencies(List.of(new ProjectDependency("library","Library","1.0")));
        prior.setApprovedSecurityContextSha256(ArtifactReviewContext.fingerprint(prior));
        var project=new Project();project.setId("p");project.setVersions(List.of(prior,target));
        var repository=mock(ProjectRepository.class);when(repository.findById("p")).thenReturn(Optional.of(project));
        var persistence=mock(ScanPersistenceService.class);
        var properties=new AppSecurityProperties("fixture",60,120,2,4,15,20,25,2);
        var classification=new SecurityIssueClassificationService(properties);
        var analysis=new SecurityIssueAnalysisService(classification,new SecurityIssueApprovalService(classification));
        var policy=mock(WardenClientService.class);when(policy.currentPolicyVersion()).thenReturn(currentPolicy?result.getSecurityEvidence().policyVersion():"older-policy");
        var completion=new ScanCompletionService(repository,mock(ProjectService.class),mock(ProjectNotificationService.class),
                mock(WebhookService.class),analysis,new ScanRoutingService(properties),persistence,new ProjectVersionAccessService(null),policy);
        completion.handleCompletedScan("p","current",1,false,result);
        var route=ArgumentCaptor.forClass(ScanRoutingService.RoutingDecision.class);
        verify(persistence).applyScanOutcome(eq("p"),eq("current"),eq(1),same(result),route.capture(),same(target));
        if(dependencies || !currentPolicy) {
            assertEquals(ScanRoutingService.RoutingAction.REQUIRE_REVIEW,route.getValue().action());
            assertNull(result.getReusedReviewVersion());assertEquals(0,result.getReusedReviewApprovedAt());
            assertTrue(result.getReusedReviewOrigins()==null || result.getReusedReviewOrigins().isEmpty());
            assertFalse(issue.isResolved());assertFalse(issue.isKnownIssue());
            assertEquals(1,result.getNewIssueCount());assertEquals(0,result.getKnownIssueCount());
        } else {
            assertEquals(ScanRoutingService.RoutingAction.SCHEDULE,route.getValue().action());
            assertEquals("1.0",result.getReusedReviewVersion());assertTrue(issue.isResolved());assertTrue(issue.isKnownIssue());
        }
    }
    @Test void equalPinnedDeclarationsDoNotResolveFindingsWithoutDependencyArtifactProof(){verifyOutcome(true,true);}
    @Test void policyMismatchRestoresCurrentFindingClassification(){verifyOutcome(false,false);}
    @Test void validReuseStillResolvesIdenticalArtifactFindings(){verifyOutcome(false,true);}
}
