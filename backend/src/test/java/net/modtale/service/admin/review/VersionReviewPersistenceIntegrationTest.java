package net.modtale.service.admin.review;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import net.modtale.config.db.MongoConfig;
import net.modtale.model.project.*;
import net.modtale.service.security.scan.ScanEvidenceFixtures;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class VersionReviewPersistenceIntegrationTest {
    @Test void metadataRepairPreservesVersionsEvidenceAndUnknownFields() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("futureField", "retain"), Project.class);
        var repairs = new ProjectReviewPersistence(mongo);
        var project = mongo.findById(id, Project.class);
        var snapshot = repairs.capture(id, ProjectReviewSnapshot.token(project));
        var originalVersions = snapshot.raw().get("versions");
        assertTrue(repairs.applyMetadataRepair(snapshot, Map.of("title", "Metadata repaired", "links", Map.of("source", "https://example.com"))));
        var raw = mongo.getCollection("projects").find().first();
        assertEquals(originalVersions, raw.get("versions"));
        assertEquals("retain", raw.getString("futureField"));
        assertEquals("Metadata repaired", raw.getString("title"));
        assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
    }
    @Test void metadataRepairRejectsForgedStateAndConcurrentDecisions() {
        var repairs = new ProjectReviewPersistence(mongo);
        var project = mongo.findById(id, Project.class);
        var snapshot = repairs.capture(id, ProjectReviewSnapshot.token(project));
        assertThrows(ResponseStatusException.class, () -> repairs.applyMetadataRepair(snapshot, Map.of("status", "PUBLISHED")));
        assertEquals("original", mongo.findById(id, Project.class).getTitle());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.REJECTED), Project.class);
        assertFalse(repairs.applyMetadataRepair(snapshot, Map.of("title", "Stale title")));
        assertEquals("original", mongo.findById(id, Project.class).getTitle());
        assertEquals(ProjectVersion.ReviewStatus.REJECTED, mongo.findById(id, Project.class).getVersions().getFirst().getReviewStatus());
    }
    private ScanResult queued() {
        var scan = new ScanResult(); scan.setStatus(ScanStatus.SCANNING);
        scan.setScanState("QUEUED"); scan.setScanAttempt(2); return scan;
    }
    @Test void missingPriorManifestDoesNotPreventRescanning() {
        mongo.getCollection(net.modtale.config.db.MongoArtifactManifestStore.COLLECTION).deleteMany(new Document());
        var current = mongo.findById(id, Project.class).getVersions().getFirst();
        var snapshot = persistence.captureForRescan(id, current.getId(), VersionReviewSnapshot.rescanToken(current));
        assertTrue(persistence.queueRescan(snapshot, queued()));
        var result = mongo.findById(id, Project.class).getVersions().getFirst();
        assertEquals(ProjectVersion.ReviewStatus.PENDING, result.getReviewStatus());
        assertEquals("QUEUED", result.getScanResult().getScanState());
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.cleared(result.getScanResult()));
    }
    @Test void rescanPreservesConcurrentSiblingAndUnknownFieldsAndOnlyOneRequestWins() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("versions.0.legacyMarker", "retain"), Project.class);
        var snapshot = persistence.captureForRescan(id, version.getId(), VersionReviewSnapshot.rescanToken(version));
        var competing = persistence.captureForRescan(id, version.getId(), VersionReviewSnapshot.rescanToken(version));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("title", "concurrent title").set("versions.1.reviewStatus", ProjectVersion.ReviewStatus.REJECTED), Project.class);
        assertTrue(persistence.queueRescan(snapshot, queued()));
        assertFalse(persistence.queueRescan(competing, queued()));
        var saved = mongo.findById(id, Project.class);
        assertEquals("concurrent title", saved.getTitle());
        assertEquals(ProjectVersion.ReviewStatus.REJECTED, saved.getVersions().get(1).getReviewStatus());
        assertEquals("QUEUED", saved.getVersions().getFirst().getScanResult().getScanState());
        assertEquals(2, saved.getVersions().getFirst().getScanResult().getScanAttempt());
        assertEquals("retain", mongo.getCollection("projects").find().first().getList("versions", Document.class).getFirst().getString("legacyMarker"));
    }
    @Test void rescanRejectsChangesToArtifactContextOrReviewDecision() {
        var changes = List.of(new Update().set("versions.0.hash", "f".repeat(64)),
                new Update().set("versions.0.gameVersions", List.of("changed")),
                new Update().set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.APPROVED),
                new Update().set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.REJECTED));
        for (var change : changes) {
            var current = mongo.findById(id, Project.class).getVersions().getFirst();
            var snapshot = persistence.captureForRescan(id, current.getId(), VersionReviewSnapshot.rescanToken(current));
            mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), change, Project.class);
            assertFalse(persistence.queueRescan(snapshot, queued()));
            assertThrows(ResponseStatusException.class, () -> persistence.captureForRescan(id, current.getId(), VersionReviewSnapshot.rescanToken(current)));
            assertNotEquals("QUEUED", mongo.findById(id, Project.class).getVersions().getFirst().getScanResult().getScanState());
        }
    }
    private MongoClient client;
    private MongoTemplate mongo;
    private VersionReviewPersistence persistence;
    private String database;
    private ProjectVersion version;
    private final String id="abcdefabcdefabcdefabcdef";
    @BeforeEach void setup() throws Exception {
        database="warden_review_test_"+UUID.randomUUID().toString().replace("-","");
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27029");
        if(!Set.of("27029","27030").contains(port)) throw new IllegalArgumentException("Unexpected test database port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        var factory=new SimpleMongoClientDatabaseFactory(client,database);
        var conversions=new MongoConfig().mongoCustomConversions(new net.modtale.config.db.MongoArtifactManifestStore(factory));
        var context=new MongoMappingContext();context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        var converter=new MappingMongoConverter(new DefaultDbRefResolver(factory),context);
        converter.setCustomConversions(conversions);converter.afterPropertiesSet();
        mongo=new MongoTemplate(factory,converter);persistence=new VersionReviewPersistence(mongo);
        var project=new Project();project.setId(id);project.setTitle("original");
        version=new ProjectVersion();version.setId("version-a");version.setVersionNumber("1.0");
        version.setHash("a".repeat(64));version.setScanResult(ScanEvidenceFixtures.complete(true));
        var sibling=new ProjectVersion();sibling.setId("version-b");sibling.setVersionNumber("2.0");
        project.setVersions(List.of(version,sibling));mongo.insert(project);
    }
    @Test void projectPublicationPreservesUnreviewedVersionsAndUnknownFields() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("versions.0.legacyMarker", "preserve").set("unmodeledProjectField", "preserve"), Project.class);
        var project = mongo.findById(id, Project.class);
        var projectPersistence = new ProjectReviewPersistence(mongo);
        var snapshot = projectPersistence.capture(id, ProjectReviewSnapshot.token(project));
        snapshot.project().setStatus(ProjectStatus.PUBLISHED);
        snapshot.project().getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertTrue(projectPersistence.apply(snapshot, "version-a"));
        var saved = mongo.findById(id, Project.class);
        assertEquals(ProjectStatus.PUBLISHED, saved.getStatus());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, saved.getVersions().getFirst().getReviewStatus());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, saved.getVersions().get(1).getReviewStatus());
        var raw = mongo.getCollection("projects").find().first();
        assertEquals("preserve", raw.getString("unmodeledProjectField"));
        assertEquals("preserve", raw.getList("versions", Document.class).getFirst().getString("legacyMarker"));
    }
    @Test void concurrentProjectChangePreventsPublication() {
        var project = mongo.findById(id, Project.class);
        var projectPersistence = new ProjectReviewPersistence(mongo);
        var snapshot = projectPersistence.capture(id, ProjectReviewSnapshot.token(project));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("title", "changed"), Project.class);
        snapshot.project().setStatus(ProjectStatus.PUBLISHED);
        snapshot.project().getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertFalse(projectPersistence.apply(snapshot, "version-a"));
        assertEquals("changed", mongo.findById(id, Project.class).getTitle());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, mongo.findById(id, Project.class).getVersions().getFirst().getReviewStatus());
    }
    @Test void changedVersionInvalidatesProjectReviewInBrowser() {
        var project = mongo.findById(id, Project.class);
        String token = ProjectReviewSnapshot.token(project);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("versions.0.hash", "f".repeat(64)), Project.class);
        assertThrows(ResponseStatusException.class, () -> new ProjectReviewPersistence(mongo).capture(id, token));
    }
    @Test void referencedManifestsAreVerifiedAndMissingOrCorruptRecordsCannotClear() {
        var raw = mongo.getCollection("projects").find().first();
        var evidence = raw.getList("versions", Document.class).getFirst().get("scanResult", Document.class).get("securityEvidence", Document.class);
        String reference = evidence.getString("manifestRef");
        assertNotNull(reference); assertFalse(evidence.containsKey("entryHashes"));
        assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
        var manifests = mongo.getCollection(net.modtale.config.db.MongoArtifactManifestStore.COLLECTION);
        manifests.updateOne(new Document("_id", reference), new Document("$set", new Document("entries", List.of(new Document("path", "substituted").append("sha256", "f".repeat(64))))));
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
        manifests.deleteOne(new Document("_id", reference));
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
    }
    @Test void legacyEmbeddedEvidenceMigratesWithoutChangingTheBrowserReviewToken() {
        var scan = ScanEvidenceFixtures.complete(true);
        var hashes = new LinkedHashMap<String,String>(); hashes.put("z/nested.class", "b".repeat(64)); hashes.put("a.$manifest", "c".repeat(64));
        var evidence = scan.getSecurityEvidence();
        var updated = new ScanResult.SecurityEvidence(evidence.policyVersion(), evidence.artifactSha256(), SecurityManifest.identity(hashes), true, true, "COMPLETED", hashes);
        var legacy = new net.modtale.config.db.SecurityEvidenceConverters.Write().convert(updated);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("versions.0.scanResult.securityEvidence", legacy), Project.class);
        var before = mongo.findById(id, Project.class);
        assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(before.getVersions().getFirst().getScanResult()));
        String token = VersionReviewSnapshot.token(before.getVersions().getFirst());
        mongo.save(before);
        var after = mongo.findById(id, Project.class);
        assertEquals(token, VersionReviewSnapshot.token(after.getVersions().getFirst()));
        assertEquals(hashes, after.getVersions().getFirst().getScanResult().getSecurityEvidence().entryHashes());
        var raw = mongo.getCollection("projects").find().first().getList("versions", Document.class).getFirst()
                .get("scanResult", Document.class).get("securityEvidence", Document.class);
        assertTrue(raw.containsKey("manifestRef")); assertFalse(raw.containsKey("entryHashes"));
    }
    @Test void sixLargeManifestsNoLongerExceedTheProjectDocumentLimit() {
        var hashes = new LinkedHashMap<String,String>();
        for (int i = 0; i < 20_000; i++) hashes.put(String.format("p/%05d/", i) + "a".repeat(30) + ".class", "a".repeat(64));
        String identity = SecurityManifest.identity(hashes);
        var project = new Project(); project.setId("large-manifest-fixture");
        var versions = new ArrayList<ProjectVersion>();
        for (int i = 0; i < 6; i++) {
            var item = new ProjectVersion(); item.setId("v" + i);
            var scan = ScanEvidenceFixtures.complete(true);
            var prior = scan.getSecurityEvidence();
            scan.setSecurityEvidence(new ScanResult.SecurityEvidence(prior.policyVersion(), prior.artifactSha256(), identity, true, true, "COMPLETED", hashes));
            item.setScanResult(scan); versions.add(item);
        }
        project.setVersions(versions); mongo.insert(project);
        var raw = mongo.getCollection("projects").find(new Document("_id", "large-manifest-fixture")).first();
        assertNotNull(raw);
        assertTrue(new org.bson.RawBsonDocument(raw, new org.bson.codecs.DocumentCodec()).getByteBuffer().remaining() < 64_000);
        assertEquals(1, mongo.getCollection(net.modtale.config.db.MongoArtifactManifestStore.COLLECTION).countDocuments(new Document("_id", identity)));
        var restored = mongo.findById("large-manifest-fixture", Project.class);
        for (var item : restored.getVersions()) assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(item.getScanResult()));
    }
    @AfterEach void cleanup() {if(client!=null) {client.getDatabase(database).drop();client.close();}}
    @Test void conditionalApprovalPreservesConcurrentSiblingAndProjectChanges() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.legacyMarker","preserve"),Project.class);
        var snapshot=persistence.capture(id,version.getId(),VersionReviewSnapshot.token(version));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("title","concurrent title")
                .set("versions.1.rejectionReason","independent sibling edit"),Project.class);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);version.setScanResult(null);
        assertTrue(persistence.apply(snapshot,version));
        var result=mongo.findById(id,Project.class);
        assertEquals("concurrent title",result.getTitle());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED,result.getVersions().getFirst().getReviewStatus());
        assertEquals("independent sibling edit",result.getVersions().get(1).getRejectionReason());
        var raw=mongo.getCollection("projects").find().first();
        assertEquals("preserve",raw.getList("versions",Document.class).getFirst().getString("legacyMarker"));
    }
    @Test void artifactReplacementDuringDecisionPreventsApproval() {
        var snapshot=persistence.capture(id,version.getId(),VersionReviewSnapshot.token(version));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.hash","b".repeat(64)),Project.class);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertFalse(persistence.apply(snapshot,version));
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(id,Project.class).getVersions().getFirst().getReviewStatus());
    }
    @Test void staleBrowserEvidenceIsRejectedBeforeCapturingDecision() {
        String token=VersionReviewSnapshot.token(version);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.scanResult.scanTimestamp",1234L),Project.class);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->persistence.capture(id,version.getId(),token)).getStatusCode().value());
    }
}
