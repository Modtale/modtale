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
        assertTrue(service.applyScanOutcome("project","version",2,result,new ScanRoutingService.RoutingDecision(ScanRoutingService.RoutingAction.SCHEDULE,2)));
        var capture=ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(capture.capture(),any(Update.class),eq(Project.class));
        String query=capture.getValue().getQueryObject().toString();
        assertTrue(query.contains(result.getSecurityEvidence().artifactSha256()));
        assertTrue(query.contains("scanAttempt=2"));
        assertTrue(query.contains("PENDING"));
        assertTrue(query.contains("SCANNING"));
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
