package net.modtale.service.admin.review;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ModerationQueuePageReaderTest {
    MongoClient client;MongoDatabase db;MongoCollection<Document> projects;ModerationQueuePageReader reader;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");db=client.getDatabase("warden_queue_page_"+UUID.randomUUID().toString().replace("-",""));projects=db.getCollection("projects");
        reader=new ModerationQueuePageReader(new MongoTemplate(client,db.getName()));
    }
    @AfterEach void cleanup(){db.drop();client.close();}
    Document version(String id,String status) {return new Document("_id",id).append("reviewStatus","PENDING").append("versionNumber",id)
            .append("scanResult",new Document("status",status).append("scanState","REMOTE_HELD").append("riskScore",0));}
    void insert(Object id,String status,List<Document> versions) {projects.insertOne(new Document("_id",id).append("status",status).append("title","title").append("versions",versions));}
    @Test void boundedPagesVisitVersionsAcrossMixedProjectIds() {
        var versions=new ArrayList<Document>();for(int i=0;i<71;i++)versions.add(version("v"+i,"FAILED"));insert("string","PUBLISHED",versions);
        var id=new ObjectId();insert(id,"PUBLISHED",List.of(version("last","SUSPICIOUS")));
        ModerationQueuePageReader.Cursor cursor=null;var ids=new HashSet<String>();int count=0;
        do {var page=reader.page(cursor,7);assertTrue(page.items().size()<=7);assertEquals(0,page.unavailableItems());
            for(var row:page.items()){assertTrue(ids.add(row.id()+":"+row.pendingVersion().id()));count++;}cursor=page.next();assertTrue(count<=72);
        } while(cursor!=null);
        assertEquals(72,count);assertTrue(ids.contains(id.toHexString()+":last"));
    }
    @Test void scanningSiblingsAreSkippedAndProjectOnlyRowsRemain() {
        insert("a","PUBLISHED",List.of(version("scan","SCANNING"),version("failed","FAILED")));
        insert("b","PENDING",List.of());insert("c","PENDING",List.of(version("approved","CLEAN").append("reviewStatus","APPROVED")));
        insert("d","DRAFT",List.of(version("hidden","FAILED")));
        var page=reader.page(null,50);assertEquals(3,page.items().size());assertEquals("failed",page.items().getFirst().pendingVersion().id());
        assertNull(page.items().get(1).pendingVersion());assertNull(page.items().get(2).pendingVersion());assertNull(page.next());
    }
    @Test void malformedAndDuplicateVersionIdsAreCountedWithoutLosingContinuation() {
        insert("a","PUBLISHED",List.of(version("duplicate","FAILED"),version("duplicate","FAILED"),version("x".repeat(200),"FAILED"),version("valid","FAILED")));
        var first=reader.page(null,2);assertTrue(first.items().isEmpty());assertEquals(2,first.unavailableItems());assertNotNull(first.next());
        var second=reader.page(first.next(),2);assertEquals(1,second.unavailableItems());assertEquals("valid",second.items().getFirst().pendingVersion().id());assertNull(second.next());
    }
    @Test void displayFieldsAreBoundedAndEvidenceIsNotReturned() {
        var value=version("v","FAILED").append("changelog","x".repeat(10000));value.get("scanResult",Document.class).append("issues",List.of(new Document("private","body")));
        insert("a","PUBLISHED",List.of(value));projects.updateOne(new Document("_id","a"),new Document("$set",new Document("title","t".repeat(10000))));
        var row=reader.page(null,1).items().getFirst();assertEquals(256,row.title().length());assertEquals(1024,row.pendingVersion().changelog().length());
        assertFalse(row.toString().contains("private"));assertEquals("REMOTE_HELD",row.pendingVersion().scan().scanState());
    }
    @Test void deletedCursorProjectContinuesAtNextRoot() {
        insert("a","PUBLISHED",List.of(version("one","FAILED"),version("two","FAILED")));insert("b","PUBLISHED",List.of(version("next","FAILED")));
        var page=reader.page(null,1);projects.deleteOne(new Document("_id","a"));
        assertEquals("next",reader.page(page.next(),1).items().getFirst().pendingVersion().id());
    }
    @Test void httpCursorRoundTripContinuesActualDatabasePage()throws Exception {
        insert("a","PUBLISHED",List.of(version("one","FAILED"),version("two","FAILED")));
        var controller=new net.modtale.controller.admin.ModerationQueuePageController(reader);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var first=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/queue/page").param("limit","1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getResponse().getContentAsString());
        assertEquals("one",body.get("items").get(0).get("pendingVersion").get("id").asText());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/queue/page").param("limit","1").param("cursor",body.get("nextCursor").asText()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].pendingVersion.id").value("two"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.nextCursor").isEmpty());
    }
    @Test void filtersApplyBeforeLimitsAndRetainOverlappingSecurityFailures() {
        var versions=new ArrayList<Document>();for(int i=0;i<60;i++)versions.add(version("ordinary"+i,"CLEAN"));
        versions.add(version("security","SUSPICIOUS"));versions.add(version("failure","FAILED"));
        var both=version("both","FAILED");both.get("scanResult",Document.class).put("newIssueCount",1);versions.add(both);
        insert("a","PUBLISHED",versions);
        var first=reader.page(null,1,ModerationQueuePageReader.Filter.SECURITY);assertEquals("security",first.items().getFirst().pendingVersion().id());
        assertEquals(ModerationQueuePageReader.Filter.SECURITY,first.next().filter());
        assertEquals("both",reader.page(first.next(),1,ModerationQueuePageReader.Filter.SECURITY).items().getFirst().pendingVersion().id());
        var failed=reader.page(null,50,ModerationQueuePageReader.Filter.OPERATIONS);
        assertEquals(List.of("failure","both"),failed.items().stream().map(row->row.pendingVersion().id()).toList());
        assertThrows(IllegalArgumentException.class,()->reader.page(first.next(),1,ModerationQueuePageReader.Filter.OPERATIONS));
    }
    @Test void knownCountsAloneDoNotPutCleanResultsInSecurityFilter() {
        var known=version("vetted","CLEAN");known.get("scanResult",Document.class).put("knownIssueCount",8);insert("a","PUBLISHED",List.of(known));
        assertTrue(reader.page(null,50,ModerationQueuePageReader.Filter.SECURITY).items().isEmpty());
        assertEquals(1,reader.page(null,50).items().size());
    }
    @Test void invalidLimitsAndCursorAreRejectedBeforeRead() {
        assertThrows(IllegalArgumentException.class,()->reader.page(null,0));assertThrows(IllegalArgumentException.class,()->reader.page(null,51));
        assertThrows(IllegalArgumentException.class,()->new ModerationQueuePageReader.Cursor(42,0));
        assertThrows(IllegalArgumentException.class,()->new ModerationQueuePageReader.Cursor("a",-1));
    }
}
