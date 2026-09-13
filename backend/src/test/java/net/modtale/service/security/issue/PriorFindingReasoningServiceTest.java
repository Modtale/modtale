package net.modtale.service.security.issue;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriorFindingReasoningServiceTest {
    static class Fixture {
        final ProjectService projects=mock(ProjectService.class);
        final MongoTemplate mongo=mock(MongoTemplate.class);
        final PriorFindingReasoningService service=new PriorFindingReasoningService(projects,mongo);
        final Project project=new Project();final ProjectVersion source=new ProjectVersion(),target=new ProjectVersion();
        final FindingReviewService.Event event;
        Fixture() {
            project.setId("project");source.setId("source");source.setVersionNumber("1");target.setId("target");target.setVersionNumber("2");
            var scan=ScanEvidenceFixtures.complete(false);target.setScanResult(scan);target.setHash(scan.getSecurityEvidence().artifactSha256());
            var issue=new ScanResult.ScanIssue();issue.setFilePath("Mod.class");issue.setType("Network");issue.setDescription("Connects to documented service");issue.setSeverity("LOW");issue.setLineStart(9);issue.setLineEnd(11);scan.setIssues(new ArrayList<>(List.of(issue)));
            scan.setReviewedContextSha256(ArtifactReviewContext.fingerprint(target));
            source.setHash(target.getHash());source.setApprovedSecurityEvidence(scan.getSecurityEvidence());
            source.setApprovedSecurityContextSha256(ArtifactReviewContext.fingerprint(source));
            source.setSecurityApprovedAt(System.currentTimeMillis()-1000);source.setSecurityApprovalProjectId("project");
            source.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);source.setApprovedReviewOrigins(Map.of());
            source.setFindingReviewHead("event");source.setApprovedFindingReviewHead("event");
            String identity=IssueEvidenceIdentity.from(scan).identify(issue);var baseline=new ProjectVersion.ApprovedIssueBaseline();baseline.setEvidenceIdentity(identity);source.setApprovedIssueBaselines(List.of(baseline));
            var evidence=scan.getSecurityEvidence();
            event=new FindingReviewService.Event("event","project","source",null,1,"reviewer",source.getSecurityApprovedAt()-1000,System.currentTimeMillis()+86400000,
                    FindingReviewService.Disposition.ACCEPT,"Verified the documented service integration","WHOLE_ARTIFACT",evidence.artifactSha256(),evidence.contentSha256(),evidence.policyVersion(),source.getApprovedSecurityContextSha256(),
                    new FindingReviewService.Finding(identity,issue.getFilePath(),issue.getType(),issue.getDescription(),9,11),null,null);
            project.setVersions(List.of(source,target));when(projects.getRawProjectById("project")).thenReturn(project);
            when(mongo.findById("event",FindingReviewService.Event.class,FindingReviewService.COLLECTION)).thenReturn(event);
        }
        PriorFindingReasoningService.Reasoning read(){return service.read("project","target","source",0,ProjectReviewSnapshot.token(project));}
    }
    @Test void returnsExactApprovedReasoningAcrossChangedCallerAndRuntimeWithoutResolving() {
        var f=new Fixture();var scan=f.target.getScanResult();var e=scan.getSecurityEvidence();var entries=new HashMap<>(e.entryHashes());entries.put("Caller.class","c".repeat(64));
        scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(),e.artifactSha256(),SecurityManifest.identity(entries),true,false,e.reviewState(),entries));
        f.target.setGameVersions(List.of("new-runtime"));scan.setReviewedContextSha256(ArtifactReviewContext.fingerprint(f.target));
        var result=f.read();assertEquals(List.of(f.event),result.decisions());
        assertTrue(result.reviewReasons().stream().anyMatch(v->v.contains("Artifact contents changed")));
        assertTrue(result.reviewReasons().stream().anyMatch(v->v.contains("context changed")));
        assertFalse(scan.getIssues().getFirst().isResolved());assertFalse(ArtifactClearancePolicy.cleared(scan));
        verify(f.mongo,never()).insert(any(),anyString());
    }
    @Test void changedFindingFileOrDescriptionCannotBorrowIdenticalLookingReasoning() {
        for(boolean content:new boolean[]{true,false}) {
            var f=new Fixture();var scan=f.target.getScanResult();var e=scan.getSecurityEvidence();
            if(content){var entries=Map.of("Mod.class","c".repeat(64));scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(),e.artifactSha256(),SecurityManifest.identity(entries),true,false,e.reviewState(),entries));}
            else scan.getIssues().getFirst().setDescription("Changed endpoint reasoning");
            assertTrue(f.read().decisions().isEmpty());
        }
    }
    @Test void staleOrIncompleteCurrentEvidenceCannotReadAsVerifiedMatch() {
        var f=new Fixture();assertThrows(ResponseStatusException.class,()->f.service.read("project","target","source",0,"old"));verifyNoInteractions(f.mongo);
        f.target.getScanResult().setArtifactVerified(false);assertThrows(ResponseStatusException.class,f::read);verifyNoInteractions(f.mongo);
    }
    @Test void unapprovedCopiedExpiredOrEditedSourceCannotSupplyVerifiedReasoning() {
        for(int scenario=0;scenario<5;scenario++) {
            var f=new Fixture();switch(scenario){case 0->f.source.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);case 1->f.source.setSecurityApprovalProjectId("another");case 2->f.source.setSecurityApprovedAt(1);case 3->f.source.setFindingReviewHead("changed");case 4->f.source.setGameVersions(List.of("edited"));}
            assertThrows(ResponseStatusException.class,f::read);verifyNoInteractions(f.mongo);
        }
    }
    @Test void historyChangesDuringReadRejectTheWholeResponse() {
        var f=new Fixture();doAnswer(call->{f.source.setFindingReviewHead("revoked");return f.event;})
                .when(f.mongo).findById("event",FindingReviewService.Event.class,FindingReviewService.COLLECTION);
        assertThrows(ResponseStatusException.class,f::read);
    }
    @Test void missingAndCrossVersionHistoryFailExplicitly() {
        var f=new Fixture();when(f.mongo.findById("event",FindingReviewService.Event.class,FindingReviewService.COLLECTION)).thenReturn(null);
        assertThrows(ResponseStatusException.class,f::read);
        when(f.mongo.findById("event",FindingReviewService.Event.class,FindingReviewService.COLLECTION)).thenReturn(new FindingReviewService.Event("event","other","source",null,1,"reviewer",1,2,FindingReviewService.Disposition.ACCEPT,"Reasoning for another project","WHOLE_ARTIFACT",null,null,null,null,f.event.finding(),null,null));
        assertThrows(ResponseStatusException.class,f::read);
    }
    @Test void revokedAndSupersededAcceptancesNeverReappear() {
        for(boolean revoke:new boolean[]{true,false}) {
            var f=new Fixture();var e=f.event;
            var next=new FindingReviewService.Event("next","project","source","event",2,e.actorId(),e.createdAt()+1,e.expiresAt(),
                    revoke?FindingReviewService.Disposition.REVOKE:FindingReviewService.Disposition.ACCEPT,"Corrected earlier review conclusion",e.scope(),e.artifactSha256(),e.contentSha256(),e.policyVersion(),e.contextSha256(),e.finding(),revoke?e.id():null,revoke?null:e.id());
            f.source.setFindingReviewHead("next");f.source.setApprovedFindingReviewHead("next");when(f.mongo.findById("next",FindingReviewService.Event.class,FindingReviewService.COLLECTION)).thenReturn(next);
            var result=f.read();assertFalse(result.decisions().contains(e));assertEquals(revoke?List.of():List.of(next),result.decisions());
        }
    }
    @Test void absentApprovedOccurrenceNeverManufacturesRecordedApproval() {
        var f=new Fixture();f.source.setApprovedIssueBaselines(List.of());assertTrue(f.read().decisions().isEmpty());
    }
}
