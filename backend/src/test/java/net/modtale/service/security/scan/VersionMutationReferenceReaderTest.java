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
class VersionMutationReferenceReaderTest {
    VersionMutationExecutorTest base=new VersionMutationExecutorTest();VersionMutationReferenceReader reader;
    @BeforeEach void setup()throws Exception{base.setup();reader=create();reader.initialize();}
    @AfterEach void cleanup(){base.cleanup();}
    VersionMutationReferenceReader create(){return new VersionMutationReferenceReader(base.base.base.fixture.mongo,base.base.base.archive,base.base.service,base.executor());}
    Object project(){return base.base.base.raw().get("_id");}
    org.springframework.data.mongodb.core.MongoTemplate mongo(){return base.base.base.fixture.mongo;}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void appliedHistoryRemainsReadableAfterVisibleProjectDeletion(boolean removal){
        var id=project();if(removal)mongo().getCollection("projects").updateOne(new Document("_id",id),new Document("$set",new Document("status","PRIVATE")));
        var prepared=base.base.service.prepare(base.base.request(removal),()->true);base.executor().apply(prepared,"owner",()->true);
        assertEquals(List.of(prepared.id()),reader.page(id,null,64,()->true).operationIds());mongo().getCollection("projects").deleteMany(new Document());
        var history=create().read(id,prepared.id(),()->true);assertEquals(prepared,history.evidence().prepared());assertEquals("APPLIED",history.receipt().state());assertEquals(removal?"REMOVED":"HELD",history.state());
        assertEquals(0,base.base.base.fixture.posts.get());assertEquals(0,base.base.base.fixture.gets.get());
    }
    @Test void paginationIsExclusiveAndBoundToExactProjectBsonIdentity(){
        var id=project();var refs=mongo().getCollection(VersionMutationExecutor.REFERENCES);var ids=new ArrayList<String>();for(int i=0;i<3;i++)ids.add(UUID.randomUUID().toString());Collections.sort(ids);
        for(var op:ids)refs.insertOne(new Document("_id",op).append("projectId",id).append("state","HELD"));
        refs.insertOne(new Document("_id",UUID.randomUUID().toString()).append("projectId",id.toString()).append("state","HELD"));
        var first=reader.page(id,null,2,()->true);assertEquals(ids.subList(0,2),first.operationIds());assertNotNull(first.nextCursor());
        var second=reader.page(id,first.nextCursor(),2,()->true);assertEquals(List.of(ids.getLast()),second.operationIds());assertNull(second.nextCursor());
        assertThrows(IllegalArgumentException.class,()->reader.page(id.toString(),first.nextCursor(),2,()->true));
        assertThrows(RuntimeException.class,()->reader.read(id,ids.getFirst(),()->true));
    }
    @ParameterizedTest @ValueSource(strings={"actor","state","digest","extra","receipt"})
    void inventoryDoesNotAuthenticateTamperedHistory(String change){
        var id=project();var prepared=base.base.service.prepare(base.base.request(false),()->true);base.executor().apply(prepared,"owner",()->true);
        var collection=change.equals("receipt")?ReviewRepairJournal.COLLECTION:VersionMutationExecutor.REFERENCES;
        var field=switch(change){case "actor"->"actor";case "state","receipt"->"state";case "digest"->"beforeSha256";default->"extra";};
        mongo().getCollection(collection).updateOne(new Document("_id",prepared.id()),new Document("$set",new Document(field,"changed")));
        assertThrows(RuntimeException.class,()->reader.read(id,prepared.id(),()->true));
    }
    @Test void missingIndexAndRevokedPermissionFailClosed(){
        var id=project();assertThrows(SecurityException.class,()->reader.page(id,null,1,()->false));
        mongo().getCollection(VersionMutationExecutor.REFERENCES).dropIndex(VersionMutationReferenceReader.INDEX);
        assertThrows(com.mongodb.MongoException.class,()->reader.page(id,null,1,()->true));
        assertThrows(SecurityException.class,()->reader.read(id,UUID.randomUUID().toString(),()->false));
    }
    @Test void malformedInventoryCannotMasqueradeAsAValidScopedRecord(){
        var id=project();var refs=mongo().getCollection(VersionMutationExecutor.REFERENCES);
        refs.insertOne(new Document("_id",UUID.randomUUID().toString()).append("projectId",List.of(id)).append("state","HELD"));
        refs.insertOne(new Document("_id",UUID.randomUUID().toString()).append("projectId",id).append("state",List.of("HELD")));
        refs.insertOne(new Document("_id","invalid-id").append("projectId",id).append("state","HELD"));
        assertTrue(reader.page(id,null,64,()->true).operationIds().isEmpty());
        assertThrows(IllegalArgumentException.class,()->reader.page(id,null,65,()->true));
    }
    @Test void accessRevokedDuringPageReadPreventsReturningInventory(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();var id=project();
        assertThrows(SecurityException.class,()->reader.page(id,null,1,()->calls.getAndIncrement()==0));assertEquals(2,calls.get());
    }

}
