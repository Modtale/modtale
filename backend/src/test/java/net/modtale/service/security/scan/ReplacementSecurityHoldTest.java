package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import net.modtale.service.admin.review.*;
import net.modtale.service.security.issue.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReplacementSecurityHoldTest {
    RemoteReviewStepIntegrationTest f=new RemoteReviewStepIntegrationTest();String hold=UUID.randomUUID().toString();
    @BeforeEach void setup()throws Exception {f.setup(System.getenv().getOrDefault("WARDEN_REPAIR_TX_DB_PORT","27031"),true);}
    @AfterEach void cleanup(){f.cleanup();}
    ProjectVersion version(){return f.mongo.findById(f.project,Project.class).getVersions().getFirst();}

    @Test void aCleanRemoteCompletionCannotSilentlyClearARetainedBlock() {
        f.change("replacementSecurityHold",hold);var claim=f.attached(30000);
        assertTrue(f.completion.handleRemoteCompletedScan(claim,f.cleanResult()));
        var version=version();assertEquals(hold,version.getReplacementSecurityHold());
        assertEquals(ProjectVersion.ReviewStatus.PENDING,version.getReviewStatus());assertEquals("BLOCK",version.getScanResult().getVerdict());
        assertNull(version.getScheduledPublishDate());assertFalse(ArtifactClearancePolicy.boundToVersion(version));
    }

    @Test void directAutomaticApprovalPersistenceCannotBypassTheHold() {
        f.change("replacementSecurityHold",hold);var claim=f.attached(30000);var version=version();
        var persistence=new ScanPersistenceService(f.mongo,mock(net.modtale.repository.project.ProjectRepository.class),mock(net.modtale.service.project.query.ProjectService.class));
        for(var action:List.of(ScanRoutingService.RoutingAction.SCHEDULE,ScanRoutingService.RoutingAction.APPROVE_NOW))
            assertFalse(persistence.applyRemoteScanOutcome(claim,version.getScanResult().getRemotePoll(),f.cleanResult(),new ScanRoutingService.RoutingDecision(action,1),version));
        assertEquals("REMOTE_REVIEW",f.saved().getScanState());
    }

    @Test void holdAddedAfterCompletionReadFencesTheStaleWrite() {
        var claim=f.attached(30000);
        when(f.policy.currentPolicyVersion()).thenAnswer(call->{f.change("replacementSecurityHold",hold);return f.binding.policyVersion();});
        assertFalse(f.completion.handleRemoteCompletedScan(claim,f.cleanResult()));
        assertEquals(hold,version().getReplacementSecurityHold());assertEquals("REMOTE_REVIEW",f.saved().getScanState());
    }

    @Test void manualApprovalAndPruningCannotEraseTheHeldEvidence() {
        f.change("replacementSecurityHold",hold);var version=version();var before=f.saved().getScanRequestId();
        var persistence=new VersionReviewPersistence(f.mongo);var snapshot=persistence.capture(f.project,"v",VersionReviewSnapshot.token(version));
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);version.setScanResult(null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->persistence.apply(snapshot,version));
        var approval=new SecurityIssueApprovalService(mock(SecurityIssueClassificationService.class));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->approval.markIssuesAcceptedForApprovedVersion(version()));
        var project=f.mongo.findById(f.project,Project.class);project.getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertEquals(0,approval.pruneApprovedScanResults(project));assertNotNull(project.getVersions().getFirst().getScanResult());
        assertEquals(before,f.saved().getScanRequestId());
    }

    @Test void tokensBindTheHoldAndPointerButNetworkPayloadsCannotSetThem()throws Exception {
        var version=version();String before=VersionReviewSnapshot.token(version),rescan=VersionReviewSnapshot.rescanToken(version);
        version.setReplacementSecurityHold(hold);assertNotEquals(before,VersionReviewSnapshot.token(version));assertNotEquals(rescan,VersionReviewSnapshot.rescanToken(version));
        String held=VersionReviewSnapshot.token(version);version.setReviewReplacement(new ProjectVersion.ReviewReplacement(UUID.randomUUID().toString(),"a".repeat(64),UUID.randomUUID().toString()));
        assertNotEquals(held,VersionReviewSnapshot.token(version));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var decoded=mapper.readValue("{\"replacementSecurityHold\":\""+hold+"\"}",ProjectVersion.class);assertNull(decoded.getReplacementSecurityHold());
        assertFalse(mapper.writeValueAsString(version).contains("replacementSecurityHold"));
    }
}
