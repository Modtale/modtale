package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationReferenceReaderTest {
    ProjectMutationExecutorTest base=new ProjectMutationExecutorTest();ProjectMutationReferenceReader reader;
    @BeforeEach void setup()throws Exception{base.setup();reader=create();reader.initialize();}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationReferenceReader create(){return new ProjectMutationReferenceReader(base.base.base.fixture.mongo,base.base.base.archive,base.base.service,base.executor());}
    Object project(){return base.base.base.raw().get("_id");}
    org.springframework.data.mongodb.core.MongoTemplate mongo(){return base.base.base.fixture.mongo;}
    @Test void appliedGroupRemainsReadableAfterVisibleProjectDeletion(){
        var id=project();var prepared=base.base.service.prepare(base.base.request(),()->true);base.executor().apply(prepared,"owner",()->true);
        var committed=VersionMutationPreparationTest.bytes(base.root());
        assertEquals(List.of(prepared.id()),reader.page(id,null,64,()->true).operationIds());mongo().getCollection("projects").deleteMany(new Document());
        var history=create().read(id,prepared.id(),()->true);assertEquals(prepared,history.evidence().prepared());assertEquals("APPLIED",history.receipt().state());
        assertArrayEquals(committed,history.applied().versionBytes());assertEquals(0,base.base.base.fixture.posts.get());assertEquals(0,base.base.base.fixture.gets.get());
    }
    @Test void paginationIsExclusiveAndBoundToExactProjectBsonIdentity(){
        var id=project();var refs=mongo().getCollection(ProjectMutationExecutor.REFERENCES);var ids=new ArrayList<String>();for(int i=0;i<3;i++)ids.add(UUID.randomUUID().toString());Collections.sort(ids);
        for(var op:ids)refs.insertOne(new Document("_id",op).append("projectId",id).append("state","HELD"));
        refs.insertOne(new Document("_id",UUID.randomUUID().toString()).append("projectId",id.toString()).append("state","HELD"));
        var first=reader.page(id,null,2,()->true);assertEquals(ids.subList(0,2),first.operationIds());assertNotNull(first.nextCursor());
        var second=reader.page(id,first.nextCursor(),2,()->true);assertEquals(List.of(ids.getLast()),second.operationIds());assertNull(second.nextCursor());
        assertThrows(IllegalArgumentException.class,()->reader.page(id.toString(),first.nextCursor(),2,()->true));
        assertThrows(RuntimeException.class,()->reader.read(id,ids.getFirst(),()->true));
    }
    @ParameterizedTest @ValueSource(strings={"actor","state","digest","extra","receipt","child","archive"})
    void inventoryDoesNotAuthenticateTamperedHistory(String change){
        var id=project();var prepared=base.base.service.prepare(base.base.request(),()->true);base.executor().apply(prepared,"owner",()->true);
        var collection=change.equals("receipt")?ReviewRepairJournal.COLLECTION:ProjectMutationExecutor.REFERENCES;
        var field=switch(change){case "actor"->"actor";case "state","receipt"->"state";case "digest"->"beforeSha256";case "child"->"versions.0.afterSha256";case "archive"->"afterArchiveId";default->"extra";};
        mongo().getCollection(collection).updateOne(new Document("_id",prepared.id()),new Document("$set",new Document(field,"changed")));
        assertThrows(RuntimeException.class,()->reader.read(id,prepared.id(),()->true));
    }
    @Test void missingIndexAndRevokedPermissionFailClosed(){
        var id=project();assertThrows(SecurityException.class,()->reader.page(id,null,1,()->false));
        mongo().getCollection(ProjectMutationExecutor.REFERENCES).dropIndex(ProjectMutationReferenceReader.INDEX);
        assertThrows(com.mongodb.MongoException.class,()->reader.page(id,null,1,()->true));
        assertThrows(SecurityException.class,()->reader.read(id,UUID.randomUUID().toString(),()->false));
    }
    @Test void malformedInventoryCannotMasqueradeAsAValidScopedRecord(){
        var id=project();var refs=mongo().getCollection(ProjectMutationExecutor.REFERENCES);
        refs.insertOne(new Document("_id",UUID.randomUUID().toString()).append("projectId",List.of(id)).append("state","HELD"));
        refs.insertOne(new Document("_id","invalid-id").append("projectId",id).append("state","HELD"));
        assertTrue(reader.page(id,null,64,()->true).operationIds().isEmpty());
        assertThrows(IllegalArgumentException.class,()->reader.page(id,null,65,()->true));
    }
    @Test void accessRevokedDuringPageReadPreventsReturningInventory(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();var id=project();
        assertThrows(SecurityException.class,()->reader.page(id,null,1,()->calls.getAndIncrement()==0));assertEquals(2,calls.get());
    }

    @ParameterizedTest @ValueSource(strings={"childState","indexType","missingChild","archiveIdentity"})
    void signedRootsRejectAlteredChildSummariesAndArchiveSubstitution(String change){
        var id=project();var prepared=base.base.service.prepare(base.base.request(),()->true);base.executor().apply(prepared,"owner",()->true);
        var refs=mongo().getCollection(ProjectMutationExecutor.REFERENCES);var ref=refs.find(new Document("_id",prepared.id())).first();
        switch(change){
            case "childState" -> ref.getList("versions",Document.class).getFirst().put("state","RETAINED");
            case "indexType" -> ref.getList("versions",Document.class).getFirst().put("beforeIndex",0L);
            case "missingChild" -> ref.getList("versions",Document.class).removeFirst();
            case "archiveIdentity" -> {
                var original=base.base.base.archive.load(ref.getString("afterArchiveId"));var substitute=UUID.randomUUID().toString();
                base.base.base.archive.retain(new ReviewSnapshotArchive.Snapshot(substitute,id,0,original.actorId(),original.action(),original.createdAt(),original.expiresAt(),original.versionBytes()));
                ref.put("afterArchiveId",substitute);
            }
        }
        refs.replaceOne(new Document("_id",prepared.id()),ref);
        assertThrows(RuntimeException.class,()->reader.read(id,prepared.id(),()->true));
    }

    @Test void heldHeadAllowsMetadataAndPositionChangesWithoutNewReview(){
        var prepared=base.base.service.prepare(base.base.request(),()->true);base.executor().apply(prepared,"owner",()->true);
        var current=base.root();var versions=current.getList("versions",Document.class);versions.get(1).put("changelog","Updated description");
        Collections.reverse(versions);reader.requireHeldHeads(project(),VersionMutationPreparationTest.bytes(current),()->true);
    }
    @ParameterizedTest @ValueSource(strings={"missingPointer","request","attempt","context","artifact","hold","state","unknown","pointer"})
    void heldHeadRejectsAlteredSecurityProjection(String change){
        var prepared=base.base.service.prepare(base.base.request(),()->true);base.executor().apply(prepared,"owner",()->true);
        var current=base.root();var version=current.getList("versions",Document.class).get(1);var scan=version.get("scanResult",Document.class);
        switch(change){
            case "missingPointer" -> version.remove("versionMutation");
            case "request" -> scan.put("scanRequestId",UUID.randomUUID().toString());
            case "attempt" -> scan.put("scanAttempt",1L);
            case "context" -> version.put("gameVersions",List.of("altered"));
            case "artifact" -> version.put("fileUrl","altered.jar");
            case "hold" -> version.put("replacementSecurityHold",UUID.randomUUID().toString());
            case "state" -> scan.put("scanState","COMPLETE");
            case "unknown" -> version.put("futureAuthority",true);
            case "pointer" -> version.get("versionMutation",Document.class).put("beforeSha256","0".repeat(64));
        }
        assertThrows(RuntimeException.class,()->reader.requireHeldHeads(project(),VersionMutationPreparationTest.bytes(current),()->true));
    }
    @Test void workflowCanApplySecondContextChangeWhileAuthenticatingPriorGroup(){
        var first=base.base.service.prepare(base.base.request(),()->true);base.executor().apply(first,"owner",()->true);
        var budget=new ReviewRepairWorkflow(org.mockito.Mockito.mock(ReviewRepairPreparation.class),org.mockito.Mockito.mock(ReviewIsolationExecutor.class),1);
        var workflow=new ProjectMutationWorkflow(budget,base.base.service,base.executor(),reader);
        var current=workflow.capture(project(),()->true);var proposed=new RawBsonDocument(current.bytes()).decode(new org.bson.codecs.DocumentCodec());
        proposed.getList("versions",Document.class).get(1).put("gameVersions",List.of("new-runtime"));
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),project(),current.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(proposed));
        var second=workflow.prepare(request,()->true);assertEquals("APPLIED",workflow.apply(second,"owner",()->true).state());
        reader.requireHeldHeads(project(),VersionMutationPreparationTest.bytes(base.root()),()->true);
        assertEquals(2,base.root().getList("versions",Document.class).get(1).get("scanResult",Document.class).get("scanAttempt"));
        assertEquals(0,budget.status().active());budget.close();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void workflowRejectsLostPriorReferenceBeforePreparationOrApplication(boolean afterPreparation){
        var first=base.base.service.prepare(base.base.request(),()->true);base.executor().apply(first,"owner",()->true);
        var budget=new ReviewRepairWorkflow(org.mockito.Mockito.mock(ReviewRepairPreparation.class),org.mockito.Mockito.mock(ReviewIsolationExecutor.class),1);
        var workflow=new ProjectMutationWorkflow(budget,base.base.service,base.executor(),reader);var current=workflow.capture(project(),()->true);
        var proposed=new RawBsonDocument(current.bytes()).decode(new org.bson.codecs.DocumentCodec());proposed.getList("versions",Document.class).get(1).put("gameVersions",List.of("changed"));
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),project(),current.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(proposed));
        var prepared=afterPreparation?workflow.prepare(request,()->true):null;
        mongo().getCollection(ProjectMutationExecutor.REFERENCES).deleteOne(new Document("_id",first.id()));
        if(afterPreparation)assertThrows(RuntimeException.class,()->workflow.apply(prepared,"owner",()->true));
        else assertThrows(RuntimeException.class,()->workflow.prepare(request,()->true));
        assertArrayEquals(current.bytes(),VersionMutationPreparationTest.bytes(base.root()));
        assertNull(mongo().getCollection(ReviewRepairJournal.COLLECTION).find(new Document("_id",request.id())).first());
        assertEquals(0,budget.status().active());budget.close();
    }

}
