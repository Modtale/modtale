package net.modtale.service.security.scan;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static net.modtale.service.security.scan.DependencyReviewGraph.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class DependencyReviewSourceTest {
    MongoClient client;MongoTemplate mongo;
    @BeforeEach void setup() throws Exception {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if(!Set.of("27029","27030").contains(port))throw new IllegalStateException("Unexpected test database port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?directConnection=true&serverSelectionTimeoutMS=2000");
        var factory=new org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory(client,"dependency_inventory_test_"+UUID.randomUUID().toString().replace("-",""));
        var conversions=new net.modtale.config.db.MongoConfig().mongoCustomConversions(new net.modtale.config.db.MongoArtifactManifestStore(factory));
        var context=new org.springframework.data.mongodb.core.mapping.MongoMappingContext();context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        var converter=new org.springframework.data.mongodb.core.convert.MappingMongoConverter(new org.springframework.data.mongodb.core.convert.DefaultDbRefResolver(factory),context);
        converter.setCustomConversions(conversions);converter.afterPropertiesSet();mongo=new MongoTemplate(factory,converter);
    }
    @AfterEach void cleanup(){if(mongo!=null)mongo.getDb().drop();if(client!=null)client.close();}
    Document version(String id,String label) {
        return new Document("_id",id).append("versionNumber",label).append("fileUrl","files/"+id).append("hash","a".repeat(64))
                .append("gameVersions",List.of("1")).append("dependencies",List.of());
    }
    void project(Object id,Document... versions){mongo.getCollection("projects").insertOne(new Document("_id",id).append("versions",List.of(versions)));}
    @Test void realGraphResolvesPinnedLabelsAndRevalidatesEveryRecord() {
        var child=version("child-id","Release-A");project("child",child);
        var root=version("root-id","1").append("dependencies",List.of(new Document("projectId","child").append("versionNumber","release-a")));
        project("root",root);
        var source=new DependencyReviewSource(mongo);var selected=source.readRoot("root","root-id");
        assertEquals(State.FOUND,selected.state());
        var graph=DependencyReviewGraph.inspect(selected.snapshot(),source,Limits.defaults());
        assertTrue(graph.resolved(),graph.gaps().toString());assertEquals(2,graph.nodes().size());
        assertEquals("child-id",graph.edges().getFirst().target().versionId());
    }
    @Test void applicationStoredVersionsPreserveContextAndRecordedScanIdentity() {
        var version=new net.modtale.model.project.ProjectVersion();version.setId("v");version.setVersionNumber("1");
        version.setFileUrl("files/v");version.setGameVersions(List.of("1"));version.setManifestId("example");version.setManifestVersion("1");
        var result=ScanEvidenceFixtures.complete(false);version.setHash(result.getSecurityEvidence().artifactSha256());version.setScanResult(result);
        var project=new net.modtale.model.project.Project();project.setId("project");project.setVersions(List.of(version));mongo.save(project);
        var lookup=new DependencyReviewSource(mongo).readRoot("project","v");assertEquals(State.FOUND,lookup.state());
        assertEquals(ArtifactReviewContext.fingerprint(version),lookup.snapshot().contextSha256());
        assertEquals(result.getSecurityEvidence().contentSha256(),lookup.snapshot().recordedScan().contentSha256());
        assertEquals(result.getSecurityEvidence().policyVersion(),lookup.snapshot().recordedScan().policyVersion());
    }
    @Test void externalAndSupplementalInputsRemainVisibleWithoutClearance() {
        var root=version("v","1").append("overrideFileUrl","overrides.zip").append("dependencies",List.of(
                new Document("projectId","external:example").append("versionNumber","1").append("source","WEBSITE").append("dependencyType","OPTIONAL")));
        project("root",root);var source=new DependencyReviewSource(mongo);var selected=source.readRoot("root","v");
        assertEquals(State.FOUND,selected.state());var graph=DependencyReviewGraph.inspect(selected.snapshot(),source,Limits.defaults());
        assertFalse(graph.resolved());
        assertTrue(graph.gaps().stream().anyMatch(g->g.reason()==Reason.EXTERNAL));
        assertTrue(graph.gaps().stream().anyMatch(g->g.reason()==Reason.SUPPLEMENTAL_CONTENT));
    }
    @Test void duplicatePinsDuplicateIdsAndMixedProjectStorageIdentitiesAreAmbiguous() {
        project("labels",version("one","V1"),version("two","v1"));
        assertEquals(State.AMBIGUOUS,new DependencyReviewSource(mongo).read(new Reference("labels","v1")).state());
        project("ids",version("same","1"),version("same","2"));
        assertEquals(State.AMBIGUOUS,new DependencyReviewSource(mongo).readRoot("ids","same").state());
        String id=new ObjectId().toHexString();project(id,version("v","1"));project(new ObjectId(id),version("v","1"));
        assertEquals(State.AMBIGUOUS,new DependencyReviewSource(mongo).read(new Reference(id,"1")).state());
    }
    @Test void objectIdProjectsAndUnicodeLabelsUseActualJavaPinSemantics() {
        var id=new ObjectId();project(id,version("v","\u212aelvin"));
        assertEquals(State.FOUND,new DependencyReviewSource(mongo).read(new Reference(id.toHexString(),"kelvin")).state());
        assertEquals(State.MISSING,new DependencyReviewSource(mongo).read(new Reference(id.toHexString(),"other")).state());
    }
    @Test void oversizedVersionAndOversizedIndexNeverResolve() {
        project("big",version("v","1").append("payload","x".repeat(270000)));
        assertEquals(State.UNAVAILABLE,new DependencyReviewSource(mongo).readRoot("big","v").state());
        var versions=new ArrayList<Document>();for(int i=0;i<4097;i++)versions.add(version("v"+i,"v"+i));
        project("many",versions.toArray(Document[]::new));
        assertEquals(State.UNAVAILABLE,new DependencyReviewSource(mongo).readRoot("many","v0").state());
    }
    @Test void malformedRecordsCannotAcquireDefaultIdentitiesOrContexts() {
        for(String field:List.of("_id","versionNumber","hash","fileUrl")) {
            var v=version("v","1");v.remove(field);String id="missing-"+field;project(id,v);
            assertNotEquals(State.FOUND,new DependencyReviewSource(mongo).read(new Reference(id,"1")).state());
        }
        project("bad",version("v","1").append("dependencies",List.of(new Document("projectId","child"))));
        assertEquals(State.UNAVAILABLE,new DependencyReviewSource(mongo).readRoot("bad","v").state());
    }
    @Test void sharedDeadlineExpiresBeforeFurtherDatabaseQueries() {
        project("root",version("v","1"));var clock=new AtomicLong();var source=new DependencyReviewSource(mongo,clock::get,1_000_000_000L);
        assertEquals(State.FOUND,source.readRoot("root","v").state());clock.set(1_000_000_000L);
        assertEquals(State.UNAVAILABLE,source.readRoot("root","v").state());
    }
    @Test void versionReorderingBetweenIndexAndRecordReadsFailsClosed() {
        project("root",version("first","1"),version("second","2"));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var source=new DependencyReviewSource(mongo,()->{
            if(calls.incrementAndGet()==3)mongo.getCollection("projects").updateOne(new Document("_id","root"),
                    new Document("$set",new Document("versions",List.of(version("second","2"),version("first","1")))));
            return 0;
        },1_000_000_000L);
        assertEquals(State.UNAVAILABLE,source.readRoot("root","first").state());
    }
    @Test void byteEndpointRevalidatesRealDatabaseDependenciesAfterReadingStorage() throws Exception {
        byte[] bytes={1,2,3};String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        project("root",version("v","1").append("hash",hash).append("dependencies",List.of(new Document("projectId","child").append("versionNumber","1"))));
        project("child",version("child-v","1").append("hash",hash));
        mongo.save(mongo.findById("root",net.modtale.model.project.Project.class));
        var projects=org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class);
        org.mockito.Mockito.when(projects.getRawProjectById("root")).thenAnswer(i->mongo.findById("root",net.modtale.model.project.Project.class));
        var storage=org.mockito.Mockito.mock(net.modtale.service.storage.StorageService.class);
        org.mockito.Mockito.when(storage.getStream(org.mockito.ArgumentMatchers.anyString())).thenAnswer(i->new java.io.ByteArrayInputStream(bytes));
        var controller=new net.modtale.controller.admin.DependencyInspectionController(projects,mongo,storage);
        String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(projects.getRawProjectById("root"));
        String identity=controller.inspect("root","v",token).getBody().inventory().identity();
        var verified=controller.verifyBytes("root","v",identity,token).getBody();
        assertTrue(verified.verification().matched());assertEquals(6,verified.verification().bytes());
        org.mockito.Mockito.when(storage.getStream("files/child-v")).thenAnswer(i->new java.io.ByteArrayInputStream(bytes){
            public void close(){mongo.getCollection("projects").updateOne(new Document("_id","child"),new Document("$set",new Document("versions.0.hash","d".repeat(64))));}
        });
        assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.verifyBytes("root","v",identity,token)).getStatusCode().value());
    }
    @Test void controllerComposesRealDatabaseGraphWithTheOpenedReviewSnapshot() {
        project("root",version("v","1").append("dependencies",List.of(new Document("projectId","child").append("versionNumber","1"))));
        project("child",version("child-v","1"));
        mongo.save(mongo.findById("root",net.modtale.model.project.Project.class));
        var projects=org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class);
        org.mockito.Mockito.when(projects.getRawProjectById("root")).thenAnswer(i->mongo.findById("root",net.modtale.model.project.Project.class));
        var controller=new net.modtale.controller.admin.DependencyInspectionController(projects,mongo,org.mockito.Mockito.mock(net.modtale.service.storage.StorageService.class));
        String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(projects.getRawProjectById("root"));
        var response=controller.inspect("root","v",token).getBody();
        assertFalse(response.artifactBytesVerified());assertEquals(2,response.inventory().nodes().size());assertTrue(response.inventory().resolved());
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(projects.getRawProjectById("root")).thenAnswer(i->{
            if(calls.incrementAndGet()==2)mongo.getCollection("projects").updateOne(new Document("_id","root"),
                    new Document("$set",new Document("versions.0.findingReviewHead","changed")));
            return mongo.findById("root",net.modtale.model.project.Project.class);
        });
        assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.inspect("root","v",token)).getStatusCode().value());
    }
    @Test void graphRejectsRealStoredReplacementAfterRootCapture() {
        project("root",version("v","1"));var source=new DependencyReviewSource(mongo);var root=source.readRoot("root","v").snapshot();
        mongo.getCollection("projects").updateOne(new Document("_id","root"),new Document("$set",new Document("versions.0.hash","b".repeat(64))));
        var graph=DependencyReviewGraph.inspect(root,source,Limits.defaults());
        assertFalse(graph.resolved());assertTrue(graph.gaps().stream().anyMatch(g->g.reason()==Reason.CHANGED));
    }
}
