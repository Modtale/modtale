package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationDiscoveryTest {
    ProjectMutationAdmissionPreparationTest base=new ProjectMutationAdmissionPreparationTest();ProjectMutationDiscovery discovery;
    @BeforeEach void setup()throws Exception{base.setup();discovery=new ProjectMutationDiscovery(base.base.base.base.base.fixture.mongo);}
    @AfterEach void cleanup(){base.cleanup();}
    Document root(){return base.base.base.base.base.fixture.mongo.getCollection("projects").find().first();}
    void save(Document root){base.base.base.base.base.fixture.mongo.getCollection("projects").replaceOne(new Document("_id",root.get("_id")),root);}
    @Test void emptySliceContinuesToExactHeldPositionWithoutActivatingAnything(){
        var first=discovery.page(null,1);assertEquals(1,first.examined());assertTrue(first.candidates().isEmpty());assertNotNull(first.next());
        var second=discovery.page(first.next(),1);assertEquals(1,second.candidates().size());var candidate=second.candidates().getFirst();
        assertEquals(root().get("_id"),candidate.projectId());assertInstanceOf(ObjectId.class,candidate.projectId());assertEquals(1,candidate.versionIndex());assertEquals("new-upload",candidate.versionId());assertEquals(base.base.prepared.id(),candidate.mutationId());
        assertTrue(discovery.page(second.next(),1).candidates().isEmpty());assertEquals(0,base.base.base.base.base.fixture.posts.get());
    }
    @ParameterizedTest @ValueSource(strings={"DRAFT","ARCHIVED","DELETED"})
    void withdrawnProjectsAreNotAdmissionCandidates(String status){var root=root();root.put("status",status);save(root);assertEquals(0,discovery.page(null,64).examined());}
    @ParameterizedTest @ValueSource(strings={"scanRequestId","scanAttempt","manualRescan","reviewStatus","pointer","pointerRequest","pointerExtra","versionId"})
    void malformedHeldIdentityIsReportedWithoutBecomingEligible(String field){
        var root=root();var version=root.getList("versions",Document.class).get(1);var scan=version.get("scanResult",Document.class);var pointer=version.get("versionMutation",Document.class);
        switch(field){case "scanRequestId"->scan.put(field,List.of(UUID.randomUUID().toString()));case "scanAttempt"->scan.put(field,1.0);case "manualRescan"->scan.put(field,true);case "reviewStatus"->version.put(field,"APPROVED");case "pointer"->version.put("versionMutation","bad");case "pointerRequest"->pointer.put("requestId",UUID.randomUUID().toString());case "pointerExtra"->pointer.put("extra",true);case "versionId"->version.put("_id","bad\n");}
        save(root);var page=discovery.page(null,64);assertTrue(page.candidates().isEmpty());assertEquals(1,page.unavailable());
    }
    @Test void boundedPagesTraverseLargeVersionListsWithoutReturningArtifactPayloads(){
        var root=root();var template=root.getList("versions",Document.class).get(1);var versions=new ArrayList<Document>();
        for(int i=0;i<130;i++){var copy=Document.parse(template.toJson());copy.put("_id","v"+i);copy.put("payload","x".repeat(10000));versions.add(copy);}root.put("versions",versions);save(root);
        var cursor=(ProjectMutationDiscovery.Cursor)null;var positions=new ArrayList<Integer>();int examined=0;
        for(int i=0;i<5;i++){var page=discovery.page(cursor,64);assertTrue(page.candidates().size()<=64);examined+=page.examined();page.candidates().forEach(c->positions.add(c.versionIndex()));cursor=page.next();if(cursor==null)break;}
        assertEquals(130,examined);assertEquals(java.util.stream.IntStream.range(0,130).boxed().toList(),positions);
    }
    @Test void textAndObjectIdsWithIdenticalDisplayRemainDifferentScopes(){
        var original=root();var copy=Document.parse(original.toJson());copy.put("_id",original.get("_id").toString());base.base.base.base.base.fixture.mongo.getCollection("projects").insertOne(copy);
        var first=discovery.page(null,64);var second=discovery.page(first.next(),64);assertEquals(1,first.candidates().size());assertEquals(1,second.candidates().size());
        assertInstanceOf(String.class,first.candidates().getFirst().projectId());assertInstanceOf(ObjectId.class,second.candidates().getFirst().projectId());
    }
    @Test void unsupportedLongUtf16ProjectIdDoesNotPoisonContinuation(){
        var copy=Document.parse(root().toJson());copy.put("_id","😀".repeat(100));base.base.base.base.base.fixture.mongo.getCollection("projects").insertOne(copy);
        var first=discovery.page(null,64);assertTrue(first.candidates().isEmpty());assertEquals(1,first.unavailable());
        assertEquals(1,discovery.page(first.next(),64).candidates().size());
    }
    @Test void limitsRejectBeforeQuery(){assertThrows(IllegalArgumentException.class,()->discovery.page(null,0));assertThrows(IllegalArgumentException.class,()->discovery.page(null,65));assertThrows(IllegalArgumentException.class,()->new ProjectMutationDiscovery.Cursor("p",-1,false));}
}
