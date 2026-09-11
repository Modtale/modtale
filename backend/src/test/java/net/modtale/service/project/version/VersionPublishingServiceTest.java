package net.modtale.service.project.version;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.project.lifecycle.*;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import net.modtale.service.security.scan.ScanEvidenceFixtures;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.mockito.ArgumentCaptor;
import com.mongodb.client.result.UpdateResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VersionPublishingServiceTest {
    private MongoTemplate mongo;
    private ProjectNotificationService notifications;
    private VersionPublishingService service;
    @BeforeEach void setup() {
        mongo = mock(MongoTemplate.class);
        notifications = mock(ProjectNotificationService.class);
        service = new VersionPublishingService(new ScheduledReleaseQueryService(mongo),
                new ScheduledReleaseExecutionService(mongo, mock(ProjectService.class), notifications, mock(SecurityIssueAnalysisService.class)));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Project.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }
    @Test void publishesOnlyDueVersionsWithVerifiedClearance() {
        Project project = project(true);
        var future = version("2.0", "2999-01-01T00:00:00", true);
        project.setVersions(List.of(project.getVersions().getFirst(), future));
        when(mongo.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project));
        service.processScheduledReleases();
        verify(notifications).notifyUpdates(project, "1.0");
        verify(notifications, never()).notifyUpdates(project, "2.0");
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(query.capture(), any(Update.class), eq(Project.class));
        String filter = query.getValue().getQueryObject().toString();
        assertTrue(filter.contains("SCHEDULED"));
        assertTrue(filter.contains("scanResult.scanAttempt"));
        assertTrue(filter.contains("artifactSha256"));
    }
    @Test void refusesLegacyScheduledApprovalWithoutEvidence() {
        Project project = project(false);
        when(mongo.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project));
        service.processScheduledReleases();
        verifyNoInteractions(notifications);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Project.class));
        assertEquals(ProjectVersion.ReviewStatus.PENDING, ((org.bson.Document)update.getValue().getUpdateObject().get("$set")).get("versions.$.reviewStatus"));
    }
    @Test void concurrentRescanOrRejectionPreventsReleaseAndNotification() {
        Project project = project(true);
        when(mongo.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Project.class))).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        service.processScheduledReleases();
        verifyNoInteractions(notifications);
    }
    @Test void changedContextOrSupplementalFilesCannotInheritScheduledClearance() {
        for (String scenario : List.of("override", "game", "manifest", "missing-context", "future-scan")) {
            reset(mongo, notifications);
            when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Project.class)))
                    .thenReturn(UpdateResult.acknowledged(1, 1L, null));
            Project project = project(true);
            var version = project.getVersions().getFirst();
            switch (scenario) {
                case "override" -> version.setOverrideFileUrl("separate.zip");
                case "game" -> version.setGameVersions(List.of("changed-runtime"));
                case "manifest" -> version.setManifestId("changed-entrypoint");
                case "missing-context" -> version.getScanResult().setReviewedContextSha256(null);
                case "future-scan" -> version.getScanResult().setScanTimestamp(System.currentTimeMillis() + 60_000);
            }
            when(mongo.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project));
            service.processScheduledReleases();
            verifyNoInteractions(notifications);
            var update = ArgumentCaptor.forClass(Update.class);
            verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Project.class));
            assertEquals(ProjectVersion.ReviewStatus.PENDING,
                    ((org.bson.Document) update.getValue().getUpdateObject().get("$set")).get("versions.$.reviewStatus"), scenario);
        }
    }
    private Project project(boolean verified) {
        Project p = new Project(); p.setId("project"); p.setVersions(List.of(version("1.0", "2000-01-01T00:00:00", verified))); return p;
    }
    private ProjectVersion version(String number, String date, boolean verified) {
        ProjectVersion v = new ProjectVersion(); v.setId(number); v.setVersionNumber(number);
        v.setReviewStatus(ProjectVersion.ReviewStatus.SCHEDULED); v.setScheduledPublishDate(date);
        if (verified) { v.setScanResult(ScanEvidenceFixtures.complete(true)); v.setHash(v.getScanResult().getSecurityEvidence().artifactSha256()); v.getScanResult().setReviewedContextSha256(net.modtale.service.security.scan.ArtifactReviewContext.fingerprint(v)); }
        return v;
    }
}
