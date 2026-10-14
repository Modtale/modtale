package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import net.modtale.service.admin.review.*;
import net.modtale.service.security.issue.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class RetainedRemoteReviewTest {
    RemoteReviewIsolationIntegrationTest base=new RemoteReviewIsolationIntegrationTest();
    RemoteReviewBinding binding;
    @BeforeEach void setup()throws Exception {base.setup();binding=base.fixture.attached(30000).binding();}
    @AfterEach void cleanup(){base.cleanup();}
    Project project(){return base.fixture.mongo.findById(base.fixture.project,Project.class);}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void manualApprovalWritersRetainTheOriginalBindingEvenWhenTheCallerPrunesIt(boolean projectWriter) {
        var f=base.fixture;var project=project();var version=project.getVersions().getFirst();
        if(projectWriter) {
            var writer=new ProjectReviewPersistence(f.mongo);var snapshot=writer.capture(f.project,ProjectReviewSnapshot.token(project));
            var reviewed=snapshot.project().getVersions().getFirst();reviewed.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);reviewed.setScanResult(null);
            assertTrue(writer.apply(snapshot,"v"));
        } else {
            var writer=new VersionReviewPersistence(f.mongo);var snapshot=writer.capture(f.project,"v",VersionReviewSnapshot.token(version));
            version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);version.setScanResult(null);
            assertTrue(writer.apply(snapshot,version));
        }
        var approved=project().getVersions().getFirst();assertNull(approved.getScanResult());assertEquals(binding,approved.getRetainedRemoteReview());
        assertEquals(binding,new ReviewRemoteTargetReader(f.mongo).capture(base.raw().get("_id"),0,"v").binding());
        var rescans=new VersionReviewPersistence(f.mongo);var captured=rescans.captureForRescan(f.project,"v",VersionReviewSnapshot.rescanToken(approved));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->rescans.queueRescan(captured,new ScanResult()));
        assertEquals(binding,project().getVersions().getFirst().getRetainedRemoteReview());
    }

    @Test void scheduledPublicationRetainsTheCompletedRemoteReference() {
        var f=base.fixture;var scan=f.cleanResult();scan.setRemoteReview(binding);
        f.change("scanResult",f.mongo.getConverter().convertToMongoType(scan));
        f.change("reviewStatus","SCHEDULED");f.change("scheduledPublishDate","2000-01-01T00:00:00");
        var release=new net.modtale.service.project.lifecycle.ScheduledReleaseExecutionService(f.mongo,
                mock(net.modtale.service.project.query.ProjectService.class),mock(net.modtale.service.communication.ProjectNotificationService.class),f.analysis,f.policy);
        assertEquals(1,release.publishDueVersions(project(),java.time.LocalDateTime.now()).size());
        var approved=project().getVersions().getFirst();assertEquals(ProjectVersion.ReviewStatus.APPROVED,approved.getReviewStatus());
        assertNull(approved.getScanResult());assertEquals(binding,approved.getRetainedRemoteReview());
    }

    @Test void pruningRetainsHistoryAndKeepsItPrivateAndSnapshotBound()throws Exception {
        var version=project().getVersions().getFirst();var service=new SecurityIssueApprovalService(mock(SecurityIssueClassificationService.class));
        version.getScanResult().setIssues(java.util.List.of());service.markIssuesAcceptedForApprovedVersion(version);
        assertNull(version.getScanResult());assertEquals(binding,version.getRetainedRemoteReview());
        String token=VersionReviewSnapshot.token(version),rescan=VersionReviewSnapshot.rescanToken(version);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();assertFalse(mapper.writeValueAsString(version).contains("retainedRemoteReview"));
        assertNull(mapper.readValue("{\"retainedRemoteReview\":"+mapper.writeValueAsString(binding)+"}",ProjectVersion.class).getRetainedRemoteReview());
        version.setRetainedRemoteReview(null);assertNotEquals(token,VersionReviewSnapshot.token(version));assertNotEquals(rescan,VersionReviewSnapshot.rescanToken(version));
    }

    @ParameterizedTest @ValueSource(strings={"attempt","job","origin","artifact","newScan"})
    void retainedReferencesMustRemainStrictAndCannotRetargetNewScans(String corruption) {
        var f=base.fixture;var raw=base.raw().getList("versions",Document.class).getFirst().get("scanResult",Document.class).get("remoteReview",Document.class);
        f.change("retainedRemoteReview",raw);f.change("scanResult",null);
        switch(corruption) {
            case "attempt" -> f.change("retainedRemoteReview.attempt",1.0);
            case "job" -> f.change("retainedRemoteReview.jobId",null);
            case "origin" -> f.change("retainedRemoteReview.origin",null);
            case "artifact" -> f.change("hash","d".repeat(64));
            case "newScan" -> f.change("scanResult",new Document("scanState","QUEUED"));
        }
        assertThrows(IllegalStateException.class,()->new ReviewRemoteTargetReader(f.mongo).capture(base.raw().get("_id"),0,"v"));
    }
}
