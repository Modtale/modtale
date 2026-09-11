package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.mockito.ArgumentCaptor;
import com.mongodb.client.result.UpdateResult;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ScanPersistenceServiceTest {
    @Test void completionIsBoundToAttemptStateAndArtifactHash() {
        MongoTemplate mongo=mock(MongoTemplate.class);
        when(mongo.updateFirst(any(Query.class),any(Update.class),eq(Project.class))).thenReturn(UpdateResult.acknowledged(1,1L,null));
        var service=new ScanPersistenceService(mongo,mock(ProjectRepository.class),mock(ProjectService.class));
        var result=ScanEvidenceFixtures.complete(true);
        var version = new ProjectVersion();
        result.setReviewedContextSha256(ArtifactReviewContext.fingerprint(version));
        assertTrue(service.applyScanOutcome("project","version",2,result,new ScanRoutingService.RoutingDecision(ScanRoutingService.RoutingAction.SCHEDULE,2),version));
        var capture=ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(capture.capture(),any(Update.class),eq(Project.class));
        String query=capture.getValue().getQueryObject().toString();
        assertTrue(query.contains(result.getSecurityEvidence().artifactSha256()));
        assertTrue(query.contains("scanAttempt=2"));
        assertTrue(query.contains("PENDING"));
        assertTrue(query.contains("SCANNING"));
    }
    @Test void incompleteVersionContextCannotBeScheduledEvenWithCleanArchive() {
        var mongo = mock(MongoTemplate.class);
        var service = new ScanPersistenceService(mongo, mock(ProjectRepository.class), mock(ProjectService.class));
        var result = ScanEvidenceFixtures.complete(true);
        var version = new ProjectVersion();
        result.setReviewedContextSha256(ArtifactReviewContext.fingerprint(version));
        version.setOverrideFileUrl("unreviewed.zip");
        assertFalse(service.applyScanOutcome("project", "version", 1, result,
                new ScanRoutingService.RoutingDecision(ScanRoutingService.RoutingAction.SCHEDULE, 1), version));
        verifyNoInteractions(mongo);
    }
    @Test void immediateApprovalStoresCompactEvidenceInTheSameConditionalUpdate() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Project.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var service = new ScanPersistenceService(mongo, mock(ProjectRepository.class), mock(ProjectService.class));
        var result = ScanEvidenceFixtures.complete(true);
        var version = new ProjectVersion();
        result.setReviewedContextSha256(ArtifactReviewContext.fingerprint(version));
        version.setApprovedSecurityEvidence(result.getSecurityEvidence());
        version.setSecurityApprovedAt(1234);
        assertTrue(service.applyScanOutcome("project", "version", 1, result,
                new ScanRoutingService.RoutingDecision(ScanRoutingService.RoutingAction.APPROVE_NOW, 0), version));
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Project.class));
        var fields = (org.bson.Document) update.getValue().getUpdateObject().get("$set");
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, fields.get("versions.$.reviewStatus"));
        assertEquals(1234L, fields.get("versions.$.securityApprovedAt"));
        assertTrue(fields.containsKey("versions.$.scanResult"));
        assertNull(fields.get("versions.$.scanResult"));
    }
    @Test void retryCanRecoverAnAbandonedQueuedAttempt() {
        MongoTemplate mongo=mock(MongoTemplate.class);
        when(mongo.updateFirst(any(Query.class),any(Update.class),eq(Project.class))).thenReturn(UpdateResult.acknowledged(1,1L,null));
        var service=new ScanPersistenceService(mongo,mock(ProjectRepository.class),mock(ProjectService.class));
        service.queueRetryAttempt("project","version",1,new ScanResult());
        var capture=ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(capture.capture(),any(Update.class),eq(Project.class));
        assertTrue(capture.getValue().getQueryObject().toString().contains("QUEUED"));
    }
}
