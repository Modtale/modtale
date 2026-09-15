package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewObservationReaderTest {
    ReviewCancellationReconcilerTest base=new ReviewCancellationReconcilerTest();
    @BeforeEach void setup()throws Exception{base.setup();base.reconciler.initializeDiscovery();}
    @AfterEach void cleanup(){base.cleanup();}
    String id(int value){return String.format("00000000-0000-0000-0000-%012d",value);}
    Document reference(int value,String actor,String state) {
        var p=base.original;
        return new Document("_id",id(value)).append("intent",new Document("actor",actor).append("isolationId",p.isolationId()).append("targetSha256",p.targetSha256()).append("createdAt",p.createdAt()).append("expiresAt",p.expiresAt()))
                .append("state",state).append("privatePayload","not for discovery");
    }
    ReviewObservationReader.Page page(String cursor,int limit){return base.reconciler.history(base.original,"actor",cursor,limit,()->true);}
    @Test void pagesOriginalActorAndIntentWithoutRemoteCallsOrReturningPayloads() {
        for(int i=1;i<=4;i++)base.observations().insertOne(reference(i,"actor","READING"));
        base.observations().insertOne(reference(5,"other","OBSERVED"));var mismatch=reference(6,"actor","OBSERVED");mismatch.get("intent",Document.class).put("targetSha256","f".repeat(64));base.observations().insertOne(mismatch);
        var before=base.observations().find().into(new ArrayList<>());var first=page(null,2);assertEquals(List.of(id(1),id(2)),first.items().stream().map(ReviewObservationReader.Item::id).toList());
        assertEquals("OBSERVATION_ID",first.order());assertEquals("c1."+base.original.isolationId()+"."+id(2),first.nextCursor());
        var second=page(first.nextCursor(),2);assertEquals(List.of(id(3),id(4)),second.items().stream().map(ReviewObservationReader.Item::id).toList());assertNull(second.nextCursor());
        assertEquals(before,base.observations().find().into(new ArrayList<>()));assertEquals(0,base.reads.get());assertEquals(0,base.f.identityGets.get());
        // A discovery reference is not a valid receipt: these fixtures deliberately lack its token and dates.
        assertThrows(IllegalStateException.class,()->base.reconciler.receipt(id(1),base.original,"actor",()->true));
    }
    @Test void rejectsCrossIsolationCursorsInvalidLimitsAndUnauthorizedActors() {
        assertThrows(IllegalArgumentException.class,()->page("r1."+id(1),2));assertThrows(IllegalArgumentException.class,()->page("c1."+id(9)+"."+id(1),2));
        assertThrows(IllegalArgumentException.class,()->page(null,0));assertThrows(IllegalArgumentException.class,()->page(null,26));
        assertThrows(SecurityException.class,()->base.reconciler.history(base.original,"other",null,2,()->true));
        assertThrows(SecurityException.class,()->base.reconciler.history(base.original,"actor",null,2,()->false));assertEquals(0,base.observations().countDocuments());
    }
    @Test void malformedStateIsOnlyAnUnknownReferenceAndArrayOrNumericAliasesAreExcluded() {
        base.observations().insertOne(reference(1,"actor","APPROVED"));var array=reference(2,"actor","OBSERVED");array.get("intent",Document.class).put("actor",List.of("actor"));base.observations().insertOne(array);
        var numeric=reference(3,"actor","OBSERVED");numeric.get("intent",Document.class).put("createdAt",(double)base.original.createdAt());base.observations().insertOne(numeric);
        var invalidId=reference(4,"actor","OBSERVED");invalidId.put("_id","not-a-uuid");base.observations().insertOne(invalidId);
        var result=page(null,25);assertEquals(List.of(new ReviewObservationReader.Item(id(1),"UNKNOWN")),result.items());
    }
    @Test void continuationDoesNotRepeatDeletedRowsAndRefreshFindsNewEarlierRows() {
        base.observations().insertMany(List.of(reference(2,"actor","READING"),reference(4,"actor","UNKNOWN")));
        var first=page(null,1);base.observations().deleteOne(new Document("_id",id(2)));base.observations().insertOne(reference(1,"actor","RESERVED"));
        assertEquals(id(4),page(first.nextCursor(),1).items().getFirst().id());assertEquals(id(1),page(null,1).items().getFirst().id());
    }
    @Test void losingOriginalCancellationAuthorityPreventsDiscoveryEvenWithRetainedReferences() {
        base.observations().insertOne(reference(1,"actor","OBSERVED"));
        base.base.operations().updateOne(new Document("_id",base.original.isolationId()),new Document("$set",new Document("intent.target.binding.jobId",id(8))));
        assertThrows(IllegalStateException.class,()->page(null,25));assertEquals(1,base.observations().countDocuments());
    }
}
