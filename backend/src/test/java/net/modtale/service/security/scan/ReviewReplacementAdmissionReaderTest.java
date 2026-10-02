package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewReplacementAdmissionReaderTest {
    ReviewReplacementPreparationTest fixture=new ReviewReplacementPreparationTest();
    ReviewReplacementAdmissionReader reader;
    @BeforeEach void setup()throws Exception {fixture.setup();reader=new ReviewReplacementAdmissionReader(fixture.fixture.fixture.mongo);}
    @AfterEach void cleanup(){fixture.cleanup();}
    com.mongodb.client.MongoCollection<Document> collection(){return fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS);}
    String id(int n){return "00000000-0000-0000-0000-"+String.format("%012d",n);}

    @Test void discoversStagedWorkAfterReaderReconstructionWithoutChangingOrDispatchingIt() {
        var prepared=fixture.preparation.prepare(fixture.request(),()->true);
        var executor=new ReviewReplacementExecutor(fixture.fixture.fixture.mongo,fixture.preparation,fixture.fixture.reader,
                new ReviewRepairJournal(fixture.fixture.fixture.mongo,fixture.fixture.archive));
        assertEquals("APPLIED",executor.stage(prepared,"new-moderator",()->true).state());var before=fixture.fixture.raw();
        var recovered=new ReviewReplacementAdmissionReader(fixture.fixture.fixture.mongo);
        assertEquals(List.of(prepared.id()),recovered.held(null,64,()->true).operationIds());
        assertTrue(recovered.hasRequest(prepared.replacement().requestId(),()->true));
        assertEquals(before,fixture.fixture.raw());assertEquals(0,fixture.fixture.fixture.gets.get());assertEquals(0,fixture.fixture.fixture.posts.get());
    }

    @Test void pagesByStableIdentityAndContinuesPastADeletedCursor() {
        for(int n:List.of(4,2,1,3))collection().insertOne(new Document("_id",id(n)).append("state","HELD"));
        var first=reader.held(null,2,()->true);assertEquals(List.of(id(1),id(2)),first.operationIds());assertEquals("r1."+id(2),first.nextCursor());
        collection().deleteOne(new Document("_id",id(2)));
        var second=reader.held(first.nextCursor(),2,()->true);assertEquals(List.of(id(3),id(4)),second.operationIds());assertNull(second.nextCursor());
        assertTrue(reader.held("r1."+id(4),64,()->true).operationIds().isEmpty());
    }

    @Test void arrayStatesMalformedIdsAndOtherStatesAreNotHeldCandidates() {
        collection().insertMany(List.of(new Document("_id",id(1)).append("state",List.of("HELD")),new Document("_id",id(2)).append("state","READY"),
                new Document("_id","bad").append("state","HELD"),new Document("_id",17).append("state","HELD"),new Document("_id",id(3)).append("state","HELD")));
        assertEquals(List.of(id(3)),reader.held(null,64,()->true).operationIds());
    }

    @Test void discoveryDoesNotAuthenticateForgedAdmissionReferences() {
        collection().insertOne(new Document("_id",id(1)).append("state","HELD"));
        assertEquals(List.of(id(1)),reader.held(null,64,()->true).operationIds());
        assertThrows(IllegalStateException.class,()->fixture.preparation.recover(id(1),"new-moderator",()->true));
    }

    @Test void evenMalformedRequestReferencesPreventAssumingHistoryIsAbsent() {
        String request=UUID.randomUUID().toString();assertFalse(reader.hasRequest(request,()->true));
        collection().insertOne(new Document("_id","damaged").append("requestId",List.of(request)));
        assertTrue(reader.hasRequest(request,()->true));
    }

    @Test void missingIndexFailsClosedInsteadOfFallingBackToAnUnboundedScan() {
        collection().dropIndex(ReviewReplacementAdmissionReader.REQUEST_INDEX);
        assertThrows(com.mongodb.MongoException.class,()->reader.hasRequest(UUID.randomUUID().toString(),()->true));
        var request=fixture.request();assertThrows(com.mongodb.MongoException.class,()->fixture.preparation.prepare(request,()->true));
        assertNull(fixture.fixture.archive.find(request.id()));
    }

    @Test void rejectsInvalidBoundsCursorsAndDeniedAccess() {
        for(int limit:List.of(0,65))assertThrows(IllegalArgumentException.class,()->reader.held(null,limit,()->true));
        for(String cursor:List.of("r2."+id(1),"r1.bad",id(1)))assertThrows(IllegalArgumentException.class,()->reader.held(cursor,2,()->true));
        assertThrows(SecurityException.class,()->reader.held(null,2,()->false));
        assertThrows(SecurityException.class,()->reader.hasRequest(id(1),()->false));
    }
}
