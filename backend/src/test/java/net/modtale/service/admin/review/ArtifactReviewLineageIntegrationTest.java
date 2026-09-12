package net.modtale.service.admin.review;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import java.util.*;
import net.modtale.config.db.*;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.*;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.*;
import net.modtale.service.security.scan.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ArtifactReviewLineageIntegrationTest {
    private MongoClient client; private MongoTemplate mongo; private String database;
    private final String id = "abcdefabcdefabcdefabcdef";
    private Project project; private ProjectVersion target; private ScanResult result;
    @BeforeEach void setup() throws Exception {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27029");
        if (!Set.of("27029","27030").contains(port)) throw new IllegalArgumentException("Unexpected port");
        database="warden_origin_test_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        var factory=new SimpleMongoClientDatabaseFactory(client,database);
        var conversions=new MongoConfig().mongoCustomConversions(new MongoArtifactManifestStore(factory));
        var context=new MongoMappingContext();context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        var converter=new MappingMongoConverter(new DefaultDbRefResolver(factory),context);converter.setCustomConversions(conversions);converter.afterPropertiesSet();
        mongo=spy(new MongoTemplate(factory,converter));
        var source=new ProjectVersion(); source.setId("source");source.setVersionNumber("1.0");
        result=ScanEvidenceFixtures.complete(false);
        source.setHash(result.getSecurityEvidence().artifactSha256());source.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        source.setApprovedSecurityEvidence(result.getSecurityEvidence());source.setApprovedSecurityContextSha256(ArtifactReviewContext.fingerprint(source));
        source.setSecurityApprovalProjectId(id);
        source.setSecurityApprovedAt(System.currentTimeMillis()-1000);source.setApprovedReviewOrigins(Map.of());
        target=new ProjectVersion();target.setId("target");target.setVersionNumber("2.0");target.setHash(source.getHash());
        var queued=new ScanResult();queued.setScanState("SCANNING");queued.setStatus(ScanStatus.SCANNING);queued.setScanAttempt(1);target.setScanResult(queued);
        project=new Project();project.setId(id);project.setVersions(List.of(source,target));mongo.insert(project);
        new ArtifactReviewReuseService().annotate(project,"target",result);
        result.setReviewedContextSha256(ArtifactReviewContext.fingerprint(target));
        assertNotNull(result.getReusedReviewOrigins());
        target.setScanResult(result); target.setSecurityApprovalProjectId(id);
        new SecurityIssueApprovalService(new SecurityIssueClassificationService(new AppSecurityProperties("test",60,120,2,12,15,120,25,2)))
                .markIssuesAcceptedForApprovedVersion(target);
    }
    @AfterEach void cleanup() { if(client!=null){client.getDatabase(database).drop();client.close();} }
    private boolean apply() {
        return new ScanPersistenceService(mongo,mock(ProjectRepository.class),mock(ProjectService.class)).applyScanOutcome(
                id,"target",1,result,new ScanRoutingService.RoutingDecision(ScanRoutingService.RoutingAction.APPROVE_NOW,0),target);
    }
    @Test void sourceGuardPublishesOnlyTargetAndRetainsOriginProof() {
        assertTrue(apply());var stored=mongo.findById(id,Project.class);
        assertEquals(ProjectVersion.ReviewStatus.APPROVED,stored.getVersions().get(1).getReviewStatus());
        assertEquals(Map.of(),stored.getVersions().getFirst().getApprovedReviewOrigins());
        assertEquals(Set.of("source"),stored.getVersions().get(1).getApprovedReviewOrigins().keySet());
        assertEquals("1.0",stored.getVersions().getFirst().getVersionNumber());
        assertNull(stored.getVersions().get(1).getScanResult());
    }
    @Test void proofCannotBeAttachedToDifferentContentsOrExtendedApprovalTime() {
        result.setReusedReviewApprovedAt(result.getReusedReviewApprovedAt()+1);
        assertFalse(apply());assertHeld();
    }
    @Test void proofForOtherContentsDoesNotAuthorizeCurrentResult() {
        var evidence=result.getSecurityEvidence();var entries=Map.of("Changed.class","f".repeat(64));
        result.setSecurityEvidence(new ScanResult.SecurityEvidence(evidence.policyVersion(),evidence.artifactSha256(),
                SecurityManifest.identity(entries),true,false,evidence.reviewState(),entries));
        assertFalse(apply());assertHeld();
    }
    @Test void objectIdBackedVersionIdentifiersRemainBoundToTheCorrectSource() {
        var sourceId=new org.bson.types.ObjectId();
        mongo.getCollection("projects").updateOne(new Document(),Updates.set("versions.0._id",sourceId));
        var snapshot=mongo.findById(id,Project.class);
        new ArtifactReviewReuseService().annotate(snapshot,"target",result);
        target.setApprovedReviewOrigins(result.getReusedReviewOrigins());
        assertEquals(Set.of(sourceId.toHexString()),result.getReusedReviewOrigins().keySet());
        assertTrue(apply());
        assertEquals(Set.of(sourceId.toHexString()),mongo.findById(id,Project.class).getVersions().get(1).getApprovedReviewOrigins().keySet());
    }
    @Test void rejectionBeforePersistenceHoldsInsteadOfPublishing() {
        mongo.getCollection("projects").updateOne(new Document(),Updates.set("versions.0.reviewStatus","REJECTED"));
        assertFalse(apply());assertHeld();
    }
    @Test void rejectionBetweenOriginReadAndWriteCannotPublishOrModifySource() {
        doAnswer(invocation -> {
            Query query=invocation.getArgument(0);
            if(query.getQueryObject().containsKey("$expr"))
                mongo.getCollection("projects").updateOne(new Document(),Updates.set("versions.0.reviewStatus","REJECTED"));
            return invocation.callRealMethod();
        }).when(mongo).updateFirst(any(Query.class),any(Update.class),eq(Project.class));
        assertFalse(apply());assertHeld();
        assertEquals(ProjectVersion.ReviewStatus.REJECTED,mongo.findById(id,Project.class).getVersions().getFirst().getReviewStatus());
    }
    private Project readyForManual() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.1.scanResult",result).set("status",ProjectStatus.PENDING),Project.class);
        return mongo.findById(id,Project.class);
    }
    private void accept(ProjectVersion version) {
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);version.setSecurityApprovalProjectId(id);
        new SecurityIssueApprovalService(new SecurityIssueClassificationService(new AppSecurityProperties("test",60,120,2,12,15,120,25,2)))
                .markIssuesAcceptedForApprovedVersion(version);
    }
    @Test void manualVersionApprovalRetainsProof() {
        var loaded=readyForManual();var version=loaded.getVersions().get(1);
        var persistence=new VersionReviewPersistence(mongo);
        var snapshot=persistence.capture(id,"target",VersionReviewSnapshot.token(version));accept(version);
        assertTrue(persistence.apply(snapshot,version));
        assertEquals(Set.of("source"),mongo.findById(id,Project.class).getVersions().get(1).getApprovedReviewOrigins().keySet());
    }
    @Test void manualVersionApprovalRejectsSourceMutationAtItsAtomicWrite() {
        var loaded=readyForManual();var version=loaded.getVersions().get(1);
        var persistence=new VersionReviewPersistence(mongo);
        var snapshot=persistence.capture(id,"target",VersionReviewSnapshot.token(version));accept(version);
        var realCollection=mongo.getCollection("projects");var intercepted=spy(realCollection);
        doReturn(intercepted).when(mongo).getCollection("projects");
        doAnswer(invocation -> {
            realCollection.updateOne(new Document(),Updates.set("versions.0.reviewStatus","REJECTED"));
            return invocation.callRealMethod();
        }).when(intercepted).updateOne(any(org.bson.conversions.Bson.class),any(org.bson.conversions.Bson.class));
        assertFalse(persistence.apply(snapshot,version));
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(id,Project.class).getVersions().get(1).getReviewStatus());
    }
    @Test void projectPublicationPreservesValidProof() {
        var loaded=readyForManual();var persistence=new ProjectReviewPersistence(mongo);
        var snapshot=persistence.capture(id,ProjectReviewSnapshot.token(loaded));
        accept(snapshot.project().getVersions().get(1));snapshot.project().setStatus(ProjectStatus.PUBLISHED);
        assertTrue(persistence.apply(snapshot,"target"));
        assertEquals(Set.of("source"),mongo.findById(id,Project.class).getVersions().get(1).getApprovedReviewOrigins().keySet());
    }
    @Test void freshlyOpenedProjectCannotPublishAnAlreadyRevokedReuse() {
        readyForManual();mongo.getCollection("projects").updateOne(new Document(),Updates.set("versions.0.reviewStatus","REJECTED"));
        var loaded=mongo.findById(id,Project.class);var persistence=new ProjectReviewPersistence(mongo);
        var snapshot=persistence.capture(id,ProjectReviewSnapshot.token(loaded));
        accept(snapshot.project().getVersions().get(1));snapshot.project().setStatus(ProjectStatus.PUBLISHED);
        assertFalse(persistence.apply(snapshot,"target"));
        assertNotEquals(ProjectStatus.PUBLISHED,mongo.findById(id,Project.class).getStatus());
    }
    @Test void legacyReusedScanCannotBypassOriginsThroughManualApproval() {
        result.setReusedReviewOrigins(null);var loaded=readyForManual();var version=loaded.getVersions().get(1);
        var persistence=new VersionReviewPersistence(mongo);
        var snapshot=persistence.capture(id,"target",VersionReviewSnapshot.token(version));accept(version);
        assertFalse(persistence.apply(snapshot,version));
    }
    private Project scheduled() {
        mongo.getCollection("projects").updateOne(new Document(),Updates.combine(
                Updates.set("versions.1.reviewStatus","SCHEDULED"),Updates.set("versions.1.scheduledPublishDate","2020-01-01T00:00:00")));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.1.scanResult",result),Project.class);
        return mongo.findById(id,Project.class);
    }
    private List<String> publishScheduled(Project snapshot, net.modtale.service.communication.ProjectNotificationService notifications) {
        var classification=new SecurityIssueClassificationService(new AppSecurityProperties("test",60,120,2,12,15,120,25,2));
        var analysis=new SecurityIssueAnalysisService(classification,new SecurityIssueApprovalService(classification));
        var warden=mock(WardenClientService.class);when(warden.currentPolicyVersion()).thenReturn(result.getSecurityEvidence().policyVersion());
        return new net.modtale.service.project.lifecycle.ScheduledReleaseExecutionService(mongo,mock(ProjectService.class),notifications,analysis,warden)
                .publishDueVersions(snapshot,java.time.LocalDateTime.now());
    }
    @Test void scheduledPublicationRetainsOriginsAndLeavesSourceUnchanged() {
        var notifications=mock(net.modtale.service.communication.ProjectNotificationService.class);
        assertEquals(List.of("2.0"),publishScheduled(scheduled(),notifications));
        var stored=mongo.findById(id,Project.class);
        assertEquals(Set.of("source"),stored.getVersions().get(1).getApprovedReviewOrigins().keySet());
        assertEquals(id,stored.getVersions().get(1).getSecurityApprovalProjectId());
        assertEquals(Map.of(),stored.getVersions().getFirst().getApprovedReviewOrigins());
    }
    @Test void scheduledPublicationRejectsSourceChangeAfterGuardWasBuilt() {
        var snapshot=scheduled();var notifications=mock(net.modtale.service.communication.ProjectNotificationService.class);
        doAnswer(invocation -> {
            Query query=invocation.getArgument(0);
            if(query.getQueryObject().containsKey("$expr")) mongo.getCollection("projects").updateOne(new Document(),Updates.set("versions.0.findingReviewHead","revocation"));
            return invocation.callRealMethod();
        }).when(mongo).updateFirst(any(Query.class),any(Update.class),eq(Project.class));
        assertTrue(publishScheduled(snapshot,notifications).isEmpty());assertHeld();verifyNoInteractions(notifications);
        assertNull(mongo.findById(id,Project.class).getVersions().get(1).getScheduledPublishDate());
    }
    @Test void scheduledPublicationRejectsAlreadyChangedSource() {
        var snapshot=scheduled();var notifications=mock(net.modtale.service.communication.ProjectNotificationService.class);
        mongo.getCollection("projects").updateOne(new Document(),Updates.set("versions.0.reviewStatus","REJECTED"));
        assertTrue(publishScheduled(snapshot,notifications).isEmpty());assertHeld();verifyNoInteractions(notifications);
    }
    private void assertHeld() {
        var stored=mongo.findById(id,Project.class).getVersions().get(1);
        assertEquals(ProjectVersion.ReviewStatus.PENDING,stored.getReviewStatus());
        assertEquals(ScanStatus.SUSPICIOUS,stored.getScanResult().getStatus());
        assertNull(stored.getScanResult().getReusedReviewVersion());
    }
}
