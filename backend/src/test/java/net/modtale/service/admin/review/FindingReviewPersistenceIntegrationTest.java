package net.modtale.service.admin.review;

import com.mongodb.client.*;
import java.util.*;
import net.modtale.config.db.*;
import net.modtale.model.project.*;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.FindingReviewService;
import net.modtale.service.security.scan.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST", matches="true")
class FindingReviewPersistenceIntegrationTest {
    private MongoClient client;
    private MongoTemplate mongo;
    private VersionReviewPersistence persistence;
    private FindingReviewService service;
    private ProjectService projects;
    private WardenClientService warden;
    private String database;
    private final String projectId = "abcdefabcdefabcdefabcdef";

    @BeforeEach void setup() throws Exception {
        String port = System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT", "27029");
        if (!Set.of("27029", "27030").contains(port)) throw new IllegalArgumentException("Unexpected test database port");
        database = "warden_finding_test_" + UUID.randomUUID().toString().replace("-", "");
        client = MongoClients.create("mongodb://127.0.0.1:" + port + "/?serverSelectionTimeoutMS=3000");
        var factory = new SimpleMongoClientDatabaseFactory(client, database);
        var conversions = new MongoConfig().mongoCustomConversions(new MongoArtifactManifestStore(factory));
        var context = new MongoMappingContext(); context.setSimpleTypeHolder(conversions.getSimpleTypeHolder()); context.afterPropertiesSet();
        var converter = new MappingMongoConverter(new DefaultDbRefResolver(factory), context);
        converter.setCustomConversions(conversions); converter.afterPropertiesSet();
        mongo = new MongoTemplate(factory, converter); persistence = spy(new VersionReviewPersistence(mongo));
        projects = mock(ProjectService.class); warden = mock(WardenClientService.class);
        when(warden.currentPolicyVersion()).thenReturn(ScanEvidenceFixtures.complete(false).getSecurityEvidence().policyVersion());
        service = new FindingReviewService(mongo, persistence, projects, warden);
        var project = new Project(); project.setId(projectId);
        var version = new ProjectVersion(); version.setId("v1"); version.setVersionNumber("1.0");
        var scan = ScanEvidenceFixtures.complete(false);
        version.setHash(scan.getSecurityEvidence().artifactSha256());
        scan.setReviewedContextSha256(ArtifactReviewContext.fingerprint(version));
        var issue = new ScanResult.ScanIssue(); issue.setFilePath("Mod.class"); issue.setType("Network");
        issue.setDescription("Connects to a service"); issue.setSeverity("LOW");
        scan.setIssues(new ArrayList<>(List.of(issue))); version.setScanResult(scan);
        project.setVersions(List.of(version)); mongo.insert(project);
        when(projects.getRawProjectById(projectId)).thenReturn(project);
    }
    @AfterEach void cleanup() { if (client != null) { client.getDatabase(database).drop(); client.close(); } }
    private ProjectVersion version() { return mongo.findById(projectId, Project.class).getVersions().getFirst(); }
    private String token() { return VersionReviewSnapshot.token(version()); }
    private FindingReviewService.Event record(String token) {
        return service.record(projectId, "v1", token, "moderator", new FindingReviewService.Request(0,
                FindingReviewService.Disposition.ACCEPT, "Verified documented service integration"));
    }

    @Test void persistsActorRationaleWholeArtifactScopeAndInvalidatesBrowserSnapshot() {
        String before = token(); var event = record(before);
        assertEquals("moderator", event.actorId()); assertEquals("WHOLE_ARTIFACT", event.scope());
        assertEquals(30L * 86400000, event.expiresAt() - event.createdAt());
        assertEquals(version().getHash(), event.artifactSha256());
        assertEquals(ArtifactReviewContext.fingerprint(version()), event.contextSha256());
        assertTrue(event.finding().identity().matches("ie1:[0-9a-f]{64}"));
        assertNotEquals(before, token()); assertEquals(event.id(), version().getFindingReviewHead());
        assertEquals(event, service.history(projectId, "v1", token(), 0).events().getFirst());
        assertFalse(version().getScanResult().getIssues().getFirst().isResolved());
        assertFalse(ArtifactClearancePolicy.cleared(version().getScanResult()));
        verify(projects).evictProjectCache(any(Project.class));
    }
    @Test void decisionCancelsSchedulingAndCannotBeBypassedByAStaleScanOutcome() {
        var previous = version();
        var scan = ScanEvidenceFixtures.complete(true);
        scan.setReviewedContextSha256(ArtifactReviewContext.fingerprint(previous));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update()
                .set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.SCHEDULED)
                .set("versions.0.scheduledPublishDate", "2020-01-01T00:00:00"), Project.class);
        record(token());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, version().getReviewStatus());
        assertNull(version().getScheduledPublishDate());
        var current = version(); current.setScanResult(scan);
        assertFalse(ArtifactClearancePolicy.boundToVersion(current));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update()
                .set("versions.0.scanResult.status", ScanStatus.SCANNING)
                .set("versions.0.scanResult.scanState", "SCANNING"), Project.class);
        var scans = new ScanPersistenceService(mongo, mock(net.modtale.repository.project.ProjectRepository.class), projects);
        var approve = new ScanRoutingService.RoutingDecision(ScanRoutingService.RoutingAction.APPROVE_NOW, 0);
        assertFalse(scans.applyScanOutcome(projectId, "v1", 1, scan, approve, previous));
        assertFalse(scans.applyScanOutcome(projectId, "v1", 1, scan, approve, current));
        assertEquals(ProjectVersion.ReviewStatus.PENDING, version().getReviewStatus());
    }
    @Test void subsequentConclusionSupersedesOnlyTheSameReviewedScope() {
        var first = record(token());
        var second = service.record(projectId, "v1", token(), "other-reviewer", new FindingReviewService.Request(0,
                FindingReviewService.Disposition.REQUIRE_REVIEW, "The caller needs further investigation"));
        assertEquals(first.id(), second.supersedesDecisionId());
        assertEquals(0, second.expiresAt());
        assertEquals(first, mongo.findById(first.id(), FindingReviewService.Event.class, FindingReviewService.COLLECTION));
        assertEquals(2, service.history(projectId, "v1", token(), 0).events().size());
    }
    @Test void historyExplainsScopeChangesAndUnavailablePolicy() {
        var event = record(token());
        assertEquals("APPLICABLE", service.history(projectId, "v1", token(), 0).assessments().get(event.id()).state());
        when(warden.currentPolicyVersion()).thenReturn(null);
        assertEquals("POLICY_UNAVAILABLE", service.history(projectId, "v1", token(), 0).assessments().get(event.id()).state());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update()
                .set("versions.0.gameVersions", List.of("different-runtime")), Project.class);
        assertEquals("CONTEXT_CHANGED", service.history(projectId, "v1", token(), 0).assessments().get(event.id()).state());
    }
    @Test void historyRejectsVersionChangeDuringPolicyLookup() {
        record(token());
        when(warden.currentPolicyVersion()).thenAnswer(invocation -> {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update()
                    .set("versions.0.hash", "f".repeat(64)), Project.class);
            return ScanEvidenceFixtures.complete(false).getSecurityEvidence().policyVersion();
        });
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.history(projectId, "v1", token(), 0)).getStatusCode().value());
    }
    @Test void staleConcurrentRecordCannotOverwriteHead() {
        String before = token(); var winner = record(before);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> record(before)).getStatusCode().value());
        assertEquals(winner.id(), version().getFindingReviewHead());
        assertEquals(1, mongo.getCollection(FindingReviewService.COLLECTION).countDocuments());
    }
    @Test void artifactChangeBetweenInsertAndCasLeavesOnlyUnreachableEvent() {
        doAnswer(invocation -> {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)),
                    new Update().set("versions.0.hash", "f".repeat(64)), Project.class);
            return invocation.callRealMethod();
        }).when(persistence).appendFindingReview(any(), anyString());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> record(token())).getStatusCode().value());
        assertNull(version().getFindingReviewHead());
        assertEquals(1, mongo.getCollection(FindingReviewService.COLLECTION).countDocuments());
        assertTrue(service.history(projectId, "v1", token(), 0).events().isEmpty());
        verifyNoInteractions(projects);
    }
    @Test void revocationSurvivesPrunedScanAndRetainsOriginalDecision() {
        var original = record(token());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update().set("versions.0.scanResult", null), Project.class);
        var revoked = service.revoke(projectId, "v1", token(), "second-moderator", original.id(), "New information invalidates the earlier review");
        assertEquals(original.id(), revoked.revokedDecisionId());
        assertEquals("second-moderator", revoked.actorId());
        assertEquals(original, mongo.findById(original.id(), FindingReviewService.Event.class, FindingReviewService.COLLECTION));
        assertEquals(List.of(revoked, original), service.history(projectId, "v1", token(), 0).events());
        assertThrows(ResponseStatusException.class, () -> service.revoke(projectId, "v1", token(), "reviewer", original.id(), "Duplicate revocation should fail"));
    }
    @Test void cannotRevokeAnOrphanOrReadCrossVersionHistory() {
        var original = record(token());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update().set("versions.0.findingReviewHead", null), Project.class);
        assertThrows(ResponseStatusException.class, () -> service.revoke(projectId, "v1", token(), "reviewer", original.id(), "An orphan is not active history"));
        var foreign = new FindingReviewService.Event("foreign", projectId, "another-version", null, 1, "actor", 0, 0,
                FindingReviewService.Disposition.ACCEPT, "foreign decision", "WHOLE_ARTIFACT", null, null, null, null, null, null, null);
        mongo.insert(foreign, FindingReviewService.COLLECTION);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update().set("versions.0.findingReviewHead", "foreign"), Project.class);
        assertThrows(ResponseStatusException.class, () -> service.history(projectId, "v1", token(), 0));
    }
    @Test void historyPagesAreStableAndMissingPredecessorsFailExplicitly() {
        var first = record(token()); var previous = first;
        for (int i = 2; i <= 51; i++) {
            var next = new FindingReviewService.Event("event-" + i, projectId, "v1", previous.id(), i,
                    first.actorId(), first.createdAt() + i, first.expiresAt(), first.disposition(), first.rationale(),
                    first.scope(), first.artifactSha256(), first.contentSha256(), first.policyVersion(),
                    first.contextSha256(), first.finding(), null, null);
            mongo.insert(next, FindingReviewService.COLLECTION); previous = next;
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update().set("versions.0.findingReviewHead", previous.id()), Project.class);
        var page = service.history(projectId, "v1", token(), 0);
        assertEquals(50, page.events().size()); assertEquals(50, page.nextOffset());
        assertEquals("event-51", page.events().getFirst().id());
        var older = service.history(projectId, "v1", token(), page.nextOffset());
        assertEquals(List.of(first), older.events()); assertNull(older.nextOffset());
        assertThrows(ResponseStatusException.class, () -> service.history(projectId, "v1", token(), -1));
        mongo.getCollection(FindingReviewService.COLLECTION).deleteOne(new Document("_id", "event-10"));
        assertThrows(ResponseStatusException.class, () -> service.history(projectId, "v1", token(), 0));
        assertThrows(ResponseStatusException.class, () -> record(token()));
    }
    @Test void missingHistoryFailsExplicitlyInsteadOfAppearingEmpty() {
        var event = record(token());
        mongo.getCollection(FindingReviewService.COLLECTION).deleteOne(new Document("_id", event.id()));
        assertThrows(ResponseStatusException.class, () -> service.history(projectId, "v1", token(), 0));
        assertThrows(ResponseStatusException.class, () -> record(token()));
    }
    @Test void invalidRationaleOrUnboundEvidenceCannotCreateRecords() {
        for (String rationale : Arrays.asList(null, "short", "x".repeat(4001)))
            assertThrows(ResponseStatusException.class, () -> service.record(projectId, "v1", token(), "reviewer",
                    new FindingReviewService.Request(0, FindingReviewService.Disposition.ACCEPT, rationale)));
        assertThrows(ResponseStatusException.class, () -> service.record(projectId, "v1", token(), "reviewer",
                new FindingReviewService.Request(null, FindingReviewService.Disposition.ACCEPT, "No finding was selected")));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)), new Update().set("versions.0.gameVersions", List.of("changed-runtime")), Project.class);
        assertThrows(ResponseStatusException.class, () -> record(token()));
        assertEquals(0, mongo.getCollection(FindingReviewService.COLLECTION).countDocuments());
    }
}
