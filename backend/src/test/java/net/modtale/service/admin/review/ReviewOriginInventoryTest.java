package net.modtale.service.admin.review;

import com.mongodb.client.*;
import org.bson.*;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static net.modtale.service.admin.review.ReviewOriginInventory.OriginState.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ReviewOriginInventoryTest {
    MongoClient client;MongoDatabase db;MongoCollection<Document> projects;ReviewOriginInventory inventory;
    String request=UUID.randomUUID().toString(),job=UUID.randomUUID().toString();
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");db=client.getDatabase("warden_origin_inventory_"+UUID.randomUUID().toString().replace("-",""));
        projects=db.getCollection("projects");inventory=new ReviewOriginInventory(new MongoTemplate(client,db.getName()));
    }
    @AfterEach void cleanup(){db.drop();client.close();}
    Document binding(){return new Document("requestId",request).append("jobId",job);}
    Document origin(){return new Document("deploymentId","11111111-1111-1111-1111-111111111111").append("callerScope","a".repeat(64));}
    Document version(Object id,Object binding){return new Document("_id",id).append("scanResult",new Document("remoteReview",binding));}
    void insert(Object id,List<?> versions){projects.insertOne(new Document("_id",id).append("versions",versions));}
    @ParameterizedTest @ValueSource(strings={"SCANNING","FAILED","COMPLETED","CANCELLED","malformed"})
    void retainedBindingsRemainVisibleIndependentOfCurrentReviewOrScanState(String status) {
        var v=version("v",binding());v.put("reviewStatus","APPROVED");v.get("scanResult",Document.class).put("status",status);insert("a",List.of(v));
        var item=inventory.page(null,64).items().getFirst();assertEquals(MISSING,item.originState());assertEquals(request,item.requestId());assertEquals(job,item.jobId());assertFalse(item.ambiguousVersion());
    }
    @Test void recordedMissingAndInvalidOriginsStayDistinctWithoutClaimingAuthority() {
        insert("a",List.of(version("missing",binding()),version("null",binding().append("origin",null)),version("recorded",binding().append("origin",origin())),
                version("bad",binding().append("origin",new Document("deploymentId","not-a-uuid"))),version("array",List.of(binding()))));
        var page=inventory.page(null,64);assertEquals(List.of(MISSING,MISSING,RECORDED,INVALID,UNREADABLE_BINDING),page.items().stream().map(ReviewOriginInventory.Item::originState).toList());
        assertNull(page.items().getLast().requestId());assertNull(page.items().getLast().jobId());
    }
    @Test void oversizedMalformedPayloadsAreNotEchoedOrDeserialized() {
        String payload="sensitive-payload".repeat(50000);
        insert("a",List.of(version("v",binding().append("origin",new Document("deploymentId",payload).append("callerScope",List.of(payload))).append("requestId",payload).append("jobId",List.of(payload))).append("unrelated",payload)));
        var page=inventory.page(null,1);assertEquals(INVALID,page.items().getFirst().originState());assertNull(page.items().getFirst().requestId());assertNull(page.items().getFirst().jobId());assertFalse(page.toString().contains("sensitive-payload"));
    }
    @Test void duplicateInvalidAndPhysicalPositionsArePreserved() {
        insert("a",List.of(version("same",binding()),version("same",binding()),version(List.of("bad"),binding()),version("bad\n",binding())));
        var page=inventory.page(null,64);assertEquals(List.of(0,1,2,3),page.items().stream().map(ReviewOriginInventory.Item::versionIndex).toList());
        assertTrue(page.items().stream().allMatch(ReviewOriginInventory.Item::ambiguousVersion));assertNull(page.items().get(2).versionId());assertNull(page.items().get(3).versionId());
    }
    @Test void emptySlicesContinueWithoutSkippingLaterBindings() {
        insert("a",List.of(new Document("_id","one"),version("two",null),version("three",binding())));
        var first=inventory.page(null,1);assertTrue(first.items().isEmpty());assertEquals(1,first.examined());
        var second=inventory.page(first.next(),1);assertTrue(second.items().isEmpty());var third=inventory.page(second.next(),1);assertEquals(2,third.items().getFirst().versionIndex());
        assertNull(inventory.page(third.next(),1).next());
    }
    @Test void mixedBsonProjectIdentitiesAndDeletedCursorRootContinueInOrder() {
        var oid=new ObjectId("abcdefabcdefabcdefabcdef");insert("a",List.of(version("a",binding())));insert(oid,List.of(version("o",binding())));
        var first=inventory.page(null,1);assertEquals("a",first.items().getFirst().projectId());projects.deleteOne(new Document("_id","a"));
        var second=inventory.page(first.next(),1);assertEquals(oid,second.items().getFirst().projectId());assertNull(inventory.page(second.next(),1).next());
    }
    @Test void binaryOrderingDoesNotConflateProjectNames() {
        insert("A",List.of(version("v",binding())));insert("a",List.of(version("v",binding())));
        var first=inventory.page(null,1);assertEquals("A",first.items().getFirst().projectId());assertEquals("a",inventory.page(first.next(),1).items().getFirst().projectId());
    }
    @Test void malformedSiblingsAndRootsDoNotForceTypedProjectReads() {
        projects.insertOne(new Document("_id",32).append("versions",List.of(version("v",binding()))));
        projects.insertOne(new Document("_id","bad").append("versions","malformed"));
        insert("a",List.of(42,new Document("scanResult",List.of(binding())),version("v",binding())));
        var page=inventory.page(null,64);assertEquals(3,page.examined());assertEquals(2,page.items().size());assertEquals(UNREADABLE_BINDING,page.items().getFirst().originState());assertTrue(page.items().getFirst().ambiguousVersion());assertEquals(2,page.items().getLast().versionIndex());assertEquals(MISSING,page.items().getLast().originState());assertNull(inventory.page(page.next(),64).next());
    }
    @Test void inventoryPreservesExactStoredBytesAndDoesNotBackfillOrigin() {
        insert("a",List.of(version("v",binding()).append("unmodeled",new Document("int",1).append("long",1L))));
        var raw=projects.withDocumentClass(RawBsonDocument.class);var before=raw.find().first();byte[] bytes=new byte[before.getByteBuffer().remaining()];before.getByteBuffer().get(bytes);
        inventory.page(null,64);var after=raw.find().first();byte[] actual=new byte[after.getByteBuffer().remaining()];after.getByteBuffer().get(actual);assertArrayEquals(bytes,actual);
    }
    @Test void limitsRejectBeforeDatabaseWorkAndResponsesAreImmutable() {
        assertThrows(IllegalArgumentException.class,()->inventory.page(null,0));assertThrows(IllegalArgumentException.class,()->inventory.page(null,65));
        insert("a",List.of(version("v",binding())));var page=inventory.page(null,1);assertThrows(UnsupportedOperationException.class,()->page.items().clear());
    }
    @Test void realInventoryToHttpPreservesEmptyContinuationAndFailedVersionReference() throws Exception {
        insert("a",List.of(new Document("_id","empty"),version("v",binding()).append("reviewStatus","APPROVED")));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new net.modtale.controller.admin.ReviewOriginController(inventory)).build();
        var first=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/origins/page").param("limit","1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items").isEmpty()).andReturn();
        String cursor=new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getResponse().getContentAsString()).path("nextCursor").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/origins/page").param("limit","1").param("cursor",cursor))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].jobId").value(job))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].position.versionIndex").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].originState").value("MISSING"));
    }

}
