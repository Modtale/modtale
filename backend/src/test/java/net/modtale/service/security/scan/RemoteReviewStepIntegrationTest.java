package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.sun.net.httpserver.*;
import net.modtale.config.properties.AppWardenProperties;
import net.modtale.model.project.*;
import net.modtale.service.storage.StorageService;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class RemoteReviewStepIntegrationTest {
    MongoClient db;MongoTemplate mongo;String database;HttpServer server;RemoteReviewClient client;
    RemoteReviewPollStore polls;StorageService storage;RemoteReviewStep step;RemoteReviewBinding binding;
    byte[] bytes="inert original".getBytes();String project="abcdefabcdefabcdefabcdef",job=UUID.randomUUID().toString();
    AtomicInteger posts=new AtomicInteger(),gets=new AtomicInteger();ObjectMapper mapper=new ObjectMapper();
    interface Handler {void handle(HttpExchange e)throws Exception;}
    void route(Handler handler){server.createContext("/api/v1/review-jobs",e->{try{if(e.getRequestMethod().equals("POST")){posts.incrementAndGet();e.getRequestBody().readAllBytes();}else gets.incrementAndGet();handler.handle(e);}catch(Exception failure){throw new RuntimeException(failure);}finally{e.close();}});}
    void reply(HttpExchange e,int code,String state)throws Exception {
        var body=new LinkedHashMap<String,Object>();body.put("jobId",job);body.put("requestId",binding.requestId());
        body.put("binding",Map.of("artifactSha256",binding.artifactSha256(),"contextSha256",binding.contextSha256(),"policyVersion",binding.policyVersion(),"reviewConfigSha256",binding.reviewConfigSha256()));
        body.put("state",state);body.put("artifactRetained",!Set.of("UPLOADING","AWAITING_UPLOAD").contains(state));body.put("createdAt",1000L);body.put("expiresAt",2000L);body.put("workState",null);
        byte[] data=mapper.writeValueAsBytes(body);e.sendResponseHeaders(code,data.length);e.getResponseBody().write(data);
    }
    @BeforeEach void setup()throws Exception {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        db=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");database="warden_remote_step_"+UUID.randomUUID().toString().replace("-","");mongo=new MongoTemplate(db,database);
        var version=new ProjectVersion();version.setId("v");version.setFileUrl("original.zip");version.setHash(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        var scan=new ScanResult();scan.setStatus(ScanStatus.SCANNING);scan.setScanState("SCANNING");scan.setScanAttempt(1);scan.setScanRequestId(UUID.randomUUID().toString());version.setScanResult(scan);
        var root=new Project();root.setId(project);root.setVersions(List.of(version));mongo.insert(root);
        binding=new RemoteReviewBinding(project,"v",scan.getScanRequestId(),1,version.getFileUrl(),version.getHash(),ArtifactReviewContext.automaticallyReviewableFingerprint(version),"warden-3.0.0:"+"b".repeat(64),"c".repeat(64),null);
        assertTrue(new RemoteReviewPersistence(mongo).bind(version,binding));polls=new RemoteReviewPollStore(mongo);storage=mock(StorageService.class);when(storage.downloadBounded(binding.filePath(),100*1024*1024)).thenReturn(bytes);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());server.start();
        client=new RemoteReviewClient(new AppWardenProperties("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-key",true,1,600),Duration.ofSeconds(2));step=new RemoteReviewStep(polls,client,storage);
    }
    @AfterEach void cleanup(){if(client!=null)client.close();if(server!=null)server.stop(0);if(db!=null){db.getDatabase(database).drop();db.close();}}
    ScanResult saved(){return mongo.findById(project,Project.class).getVersions().getFirst().getScanResult();}
    void change(String field,Object value){mongo.updateFirst(Query.query(Criteria.where("_id").is(project)),new Update().set("versions.0."+field,value),Project.class);}
    void ready(){change("scanResult.remotePoll.nextPollAt",new Date(0));}
    @Test void submitThenPersistAndPollExistingJobWithoutRedownload() {
        var found=new AtomicBoolean();route(e->{if(e.getRequestMethod().equals("POST")){found.set(true);reply(e,202,"QUEUED");}else reply(e,found.get()?200:404,found.get()?"COMPLETED":"AWAITING_UPLOAD");});
        assertEquals("RECORDED",step.advance(binding).state());assertEquals(job,saved().getRemoteReview().jobId());assertEquals("QUEUED",saved().getRemoteStatus().state());assertNull(saved().getRemotePoll().token());
        assertEquals("NO_WORK",step.advance(saved().getRemoteReview()).state());ready();
        var outcome=new RemoteReviewStep(new RemoteReviewPollStore(mongo),client,storage).advance(saved().getRemoteReview());assertEquals("COMPLETED",outcome.remoteState());
        assertEquals("REMOTE_REVIEW",saved().getScanState());assertFalse(saved().isArtifactVerified());assertEquals(1,posts.get());verify(storage,times(1)).downloadBounded(anyString(),anyInt());
    }
    @Test void lostPostResponseRecoversSameRequestAndRecordsJobWithoutAnotherUpload() {
        var accepted=new AtomicBoolean();route(e->{if(e.getRequestMethod().equals("POST")){accepted.set(true);return;}reply(e,accepted.get()?200:404,accepted.get()?"QUEUED":"AWAITING_UPLOAD");});
        assertEquals("RETRY",step.advance(binding).state());assertNull(saved().getRemoteReview().jobId());ready();
        assertEquals("RECORDED",step.advance(saved().getRemoteReview()).state());assertEquals(job,saved().getRemoteReview().jobId());assertEquals(1,posts.get());verify(storage,times(1)).downloadBounded(anyString(),anyInt());
    }
    @Test void contextChangeDuringLookupPreventsDownloadAndPost() {
        route(e->{change("manifestVersion","changed");reply(e,404,"AWAITING_UPLOAD");});
        assertEquals("SUPERSEDED",step.advance(binding).state());verifyNoInteractions(storage);assertEquals(0,posts.get());assertNull(saved().getRemoteStatus());
    }
    @Test void leaseExpiryDuringDownloadPreventsPost() {
        route(e->reply(e,404,"AWAITING_UPLOAD"));when(storage.downloadBounded(anyString(),anyInt())).thenAnswer(i->{change("scanResult.remotePoll.leaseUntil",new Date(0));return bytes;});
        assertEquals("SUPERSEDED",step.advance(binding).state());assertEquals(0,posts.get());assertNull(saved().getRemoteReview().jobId());
    }
    @Test void changedContextDuringPostCannotAttachRemoteJob() {
        route(e->{if(e.getRequestMethod().equals("POST")){change("manifestVersion","changed");reply(e,202,"QUEUED");}else reply(e,404,"AWAITING_UPLOAD");});
        assertEquals("SUPERSEDED",step.advance(binding).state());assertEquals(1,posts.get());assertNull(saved().getRemoteReview().jobId());assertNull(saved().getRemoteStatus());
    }
    @Test void knownUploadingJobCanResumeAbandonedUploadWithSameIdentity() {
        var state=new AtomicReference<>("UPLOADING");route(e->{if(e.getRequestMethod().equals("POST"))reply(e,202,"QUEUED");else reply(e,200,state.get());});
        assertEquals("UPLOADING",step.advance(binding).remoteState());verifyNoInteractions(storage);ready();state.set("AWAITING_UPLOAD");
        assertEquals("QUEUED",step.advance(saved().getRemoteReview()).remoteState());assertEquals(job,saved().getRemoteReview().jobId());assertEquals(1,posts.get());
    }
    @Test void competingStepCannotDispatchUnderExistingLiveClaim() {
        assertNotNull(polls.claim(binding,120000));route(e->reply(e,404,"AWAITING_UPLOAD"));assertEquals("NO_WORK",step.advance(binding).state());assertEquals(0,gets.get());verifyNoInteractions(storage);
    }
}
