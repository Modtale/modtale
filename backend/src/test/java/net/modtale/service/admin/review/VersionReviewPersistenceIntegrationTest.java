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
        var conversions=new MongoConfig().mongoCustomConversions();
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
