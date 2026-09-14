package net.modtale.service.admin.review;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static net.modtale.service.admin.review.ReviewStateDiagnosticReader.Reason.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ReviewStateDiagnosticReaderTest {
    MongoClient client;MongoDatabase db;MongoCollection<Document> projects;ReviewStateDiagnosticReader reader;
    String request=UUID.randomUUID().toString();
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");db=client.getDatabase("warden_diagnostic_"+UUID.randomUUID().toString().replace("-",""));
        projects=db.getCollection("projects");reader=new ReviewStateDiagnosticReader(new MongoTemplate(client,db.getName()));
    }
    @AfterEach void cleanup(){db.drop();client.close();}
    Document version(String id) {return new Document("_id",id).append("reviewStatus","PENDING").append("fileUrl","file.zip").append("hash","a".repeat(64))
            .append("scanResult",new Document("status","SCANNING").append("scanState","QUEUED").append("scanAttempt",1).append("scanRequestId",request));}
    Document binding(String project,String id) {return new Document("projectId",project).append("versionId",id).append("requestId",request).append("attempt",1)
            .append("filePath","file.zip").append("artifactSha256","a".repeat(64)).append("contextSha256","b".repeat(64)).append("policyVersion","warden-3.0.0:"+"c".repeat(64))
            .append("reviewConfigSha256","d".repeat(64)).append("manualRescan",false);}
    Document scan(Document version){return version.get("scanResult",Document.class);}
    void insert(Object id,List<?> versions){projects.insertOne(new Document("_id",id).append("versions",versions));}
    @Test void validWaitingRecordsAreNotDiagnosedAsBroken() {
        var queued=version("q");var remote=version("r");scan(remote).put("scanState","REMOTE_REVIEW");scan(remote).put("remoteReview",binding("a","r"));
        scan(remote).put("remotePoll",new Document("token",UUID.randomUUID().toString()).append("leaseUntil",new Date(Long.MAX_VALUE)).append("nextPollAt",new Date(Long.MAX_VALUE)));
        insert("a",List.of(queued,remote));var before=projects.find().first();var page=reader.page(null,64);
        assertEquals(2,page.examined());assertTrue(page.items().isEmpty());assertNotNull(page.next());assertNull(reader.page(page.next(),64).next());assertEquals(before,projects.find().first());
    }
    @ParameterizedTest @ValueSource(strings={"missing","false"})
    void legacyDefaultManualModeAgreesWithTypedPersistence(String representation) {
        var v=version("v");scan(v).put("scanState","REMOTE_REVIEW");var b=binding("a","v");
        if(!representation.equals("missing"))scan(v).put("manualRescan",false);
        scan(v).put("remoteReview",b);insert("a",List.of(v));
        var mongo=new MongoTemplate(client,db.getName());
        var typed=mongo.findById("a",net.modtale.model.project.Project.class).getVersions().getFirst();
        assertFalse(typed.getScanResult().isManualRescan());assertFalse(typed.getScanResult().getRemoteReview().manualRescan());
        assertTrue(reader.page(null,1).items().isEmpty());
    }
    @Test void explicitNullManualModeIsNotSilentlyTreatedAsDefault() {
        var v=version("v");scan(v).put("manualRescan",null);insert("a",List.of(v));
        assertThrows(RuntimeException.class,()->new MongoTemplate(client,db.getName()).findById("a",net.modtale.model.project.Project.class));
        assertEquals(List.of(INVALID_MANUAL_MODE),reader.page(null,1).items().getFirst().reasons());
    }
    @ParameterizedTest @ValueSource(strings={"null","scalar","array","missingKey","badToken","extraKey","badDate"})
    void invalidPollsSkippedByDiscoveryRemainDiagnosable(String mutation) {
        var v=version("v");scan(v).put("scanState","REMOTE_REVIEW");scan(v).put("remoteReview",binding("a","v"));
        var poll=new Document("token",null).append("leaseUntil",new Date()).append("nextPollAt",new Date());Object stored=poll;
        switch(mutation) {
            case "null" -> stored=null;case "scalar" -> stored="unexpected";case "array" -> stored=List.of(poll);
            case "missingKey" -> poll.remove("token");case "badToken" -> poll.put("token","not-a-uuid");
            case "extraKey" -> poll.put("extra",1);case "badDate" -> poll.put("leaseUntil",0);
        }
        scan(v).put("remotePoll",stored);insert("a",List.of(v));
        assertEquals(List.of(INVALID_POLL),reader.page(null,1).items().getFirst().reasons());
    }
    @ParameterizedTest @ValueSource(strings={"scalar","job","attempt","manual","missingField","longPath","missingManual","nullManual"})
    void bindingCorruptionDoesNotRequireTypedDeserialization(String mutation) {
        var v=version("v");scan(v).put("scanState","REMOTE_REVIEW");var binding=binding("a","v");Object stored=binding;
        switch(mutation) {
            case "scalar" -> stored=42;case "job" -> binding.put("jobId",List.of("unexpected"));
            case "attempt" -> binding.put("attempt",1.5);case "manual" -> binding.put("manualRescan","false");
            case "missingManual" -> binding.remove("manualRescan");case "nullManual" -> binding.put("manualRescan",null);
            case "missingField" -> binding.remove("contextSha256");case "longPath" -> binding.put("filePath","p".repeat(100000));
        }
        scan(v).put("remoteReview",stored);insert("a",List.of(v));assertEquals(List.of(INVALID_BINDING),reader.page(null,1).items().getFirst().reasons());
        if(mutation.equals("missingManual") || mutation.equals("nullManual"))assertThrows(RuntimeException.class,()->new MongoTemplate(client,db.getName()).findById("a",net.modtale.model.project.Project.class));
    }
    @Test void missingBindingAndMismatchedIdentityAreDistinct() {
        var missing=version("missing");scan(missing).put("scanState","REMOTE_REVIEW");
        var mismatch=version("mismatch");scan(mismatch).put("scanState","REMOTE_REVIEW");scan(mismatch).put("remoteReview",binding("other","mismatch"));
        insert("a",List.of(missing,mismatch));var page=reader.page(null,64);
        assertEquals(List.of(MISSING_BINDING),page.items().getFirst().reasons());assertEquals(List.of(BINDING_MISMATCH),page.items().get(1).reasons());
    }
    @Test void badRequestArtifactAndManualModeAreAllReportedWithoutEchoingContent() {
        var v=version("v").append("hash",List.of("bad")).append("fileUrl","secret".repeat(10000));
        scan(v).put("scanRequestId","secret".repeat(10000));scan(v).put("scanAttempt",Long.MAX_VALUE);scan(v).put("manualRescan","invalid");scan(v).put("scanState","unexpected");
        scan(v).put("issues",List.of(new Document("secret","do not expose")));insert("a",List.of(v));
        var item=reader.page(null,1).items().getFirst();assertEquals(List.of(INVALID_REQUEST,INVALID_ARTIFACT,INVALID_MANUAL_MODE,INVALID_SCAN_STATE),item.reasons());
        assertFalse(item.toString().contains("secret"));assertFalse(item.toString().contains("do not expose"));
    }
    @Test void duplicateAndMalformedVersionIdsKeepPhysicalPositions() {
        insert("a",List.of(version("same"),version("same"),version("x".repeat(10000)),version("bad\n")));
        var page=reader.page(null,64);assertEquals(4,page.items().size());
        for(int i=0;i<4;i++)assertEquals(i,page.items().get(i).versionIndex());
        assertEquals(List.of(DUPLICATE_VERSION_ID),page.items().getFirst().reasons());assertNull(page.items().get(2).versionId());assertNull(page.items().get(3).versionId());
    }
    @Test void boundedPhysicalPagingCrossesHealthySlotsAndMixedBsonIds() {
        String hex="abcdefabcdefabcdefabcdef";var versions=new ArrayList<Object>();
        for(int i=0;i<70;i++)versions.add(i%3==0?"malformed sibling":version("v"+i));
        var invalid=version("last");scan(invalid).put("remotePoll",null);versions.add(invalid);
        insert(hex,versions);var invalid2=version("other");scan(invalid2).put("scanRequestId","bad");insert(new ObjectId(hex),List.of(invalid2));
        net.modtale.service.security.scan.RemoteReviewDiscovery.Cursor cursor=null;var found=new ArrayList<ReviewStateDiagnosticReader.Item>();int examined=0,pages=0;
        do {var page=reader.page(cursor,7);assertTrue(page.examined()<=7);found.addAll(page.items());examined+=page.examined();cursor=page.next();assertTrue(++pages<20);}while(cursor!=null);
        assertEquals(72,examined);assertEquals(2,found.size());assertEquals(hex,found.getFirst().projectId());assertEquals(70,found.getFirst().versionIndex());
        assertEquals(new ObjectId(hex),found.get(1).projectId());assertEquals(List.of(POLL_WITHOUT_REMOTE_STATE,INVALID_POLL),found.getFirst().reasons());
    }
    @Test void deletedRootContinuesAndNonpendingRecordsAreNotDiagnosed() {
        var v=version("v");scan(v).put("scanRequestId","bad");insert("a",List.of(v,v));insert("b",List.of(v));
        var page=reader.page(null,1);projects.deleteOne(new Document("_id","a"));assertEquals("b",reader.page(page.next(),1).items().getFirst().projectId());
        projects.deleteMany(new Document());v.put("reviewStatus","APPROVED");insert("a",List.of(v));assertEquals(0,reader.page(null,1).examined());
    }
    @Test void httpPaginationTraversesEmptyDiagnosticPageAndMixedIdentityRoots()throws Exception {
        String hex="abcdefabcdefabcdefabcdef";var bad=version("bad");scan(bad).put("remotePoll",null);
        insert(hex,List.of(version("healthy"),bad));insert(new ObjectId(hex),List.of(bad));
        var controller=new net.modtale.controller.admin.ReviewStateDiagnosticController(reader);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();String cursor=null;var types=new ArrayList<String>();int calls=0,examined=0;
        do {
            var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/diagnostics/page").param("limit","1");
            if(cursor!=null)request.param("cursor",cursor);
            var response=mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store")).andReturn().getResponse();
            var body=mapper.readTree(response.getContentAsString());examined+=body.get("examinedSlots").asInt();
            if(calls==0) {assertTrue(body.get("items").isEmpty());assertFalse(body.get("nextCursor").isNull());}
            for(var item:body.get("items")) {types.add(item.get("position").get("projectIdType").asText());assertEquals(hex,item.get("position").get("projectId").asText());}
            cursor=body.get("nextCursor").isNull()?null:body.get("nextCursor").asText();assertTrue(++calls<=4);
        } while(cursor!=null);
        assertEquals(4,calls);assertEquals(3,examined);assertEquals(List.of("STRING","OBJECT_ID"),types);
    }
    @Test void invalidLimitsFailBeforeDatabaseAccess(){assertThrows(IllegalArgumentException.class,()->reader.page(null,0));assertThrows(IllegalArgumentException.class,()->reader.page(null,65));}
}
