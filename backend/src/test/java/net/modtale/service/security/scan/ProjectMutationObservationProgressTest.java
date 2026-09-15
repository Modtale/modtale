package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import net.modtale.model.project.RemoteReviewBinding;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationObservationProgressTest {
    static final String PROGRESS="project_mutation_observation_progress";
    ProjectMutationAutomaticAdmissionTest base=new ProjectMutationAutomaticAdmissionTest();
    AtomicInteger reads=new AtomicInteger();String secondState="COMPLETED";
    @BeforeEach void setup()throws Exception{
        base.setup();var f=base.f();var preparation=base.base.base.base.base;
        var old=new RawBsonDocument(preparation.base.archive.load(base.base.base.prepared.beforeArchiveId()).versionBytes()).decode(new DocumentCodec());
        var versions=new ArrayList<>(old.getList("versions",Document.class));var first=versions.getFirst();
        var second=new RawBsonDocument(VersionMutationPreparationTest.bytes(first)).decode(new DocumentCodec());second.put("_id","v2");
        var scan=second.get("scanResult",Document.class);String request=UUID.randomUUID().toString();scan.put("scanRequestId",request);
        var remote=scan.get("remoteReview",Document.class);remote.put("versionId","v2");remote.put("requestId",request);remote.put("jobId",UUID.randomUUID().toString());
        versions.add(second);old.put("versions",versions);f.mongo.getCollection("projects").replaceOne(new Document("_id",old.get("_id")),old);
        var proposal=preparation.request();var next=new RawBsonDocument(proposal.proposedProject()).decode(new DocumentCodec());next.getList("versions",Document.class).get(1).putAll(new Document("fileUrl",f.binding.filePath()).append("hash",f.binding.artifactSha256()));
        var prepared=preparation.service.prepare(new ProjectMutationPreparation.Request(proposal.id(),proposal.projectId(),proposal.expectedSha256(),proposal.actor(),proposal.mutation(),VersionMutationPreparationTest.bytes(next)),()->true);
        assertEquals("APPLIED",base.base.base.base.executor().apply(prepared,"owner",()->true).state());
        var bindings=new HashMap<String,RemoteReviewBinding>();
        for(var version:List.of(first,second)){var binding=f.mongo.getConverter().read(RemoteReviewBinding.class,version.get("scanResult",Document.class).get("remoteReview",Document.class));bindings.put(binding.jobId(),binding);}
        f.server.removeContext("/api/v1/review-jobs");f.route(e->{
            if(e.getRequestURI().getPath().endsWith("/configuration")){var bytes=f.mapper.writeValueAsBytes(Map.of("policyVersion",f.binding.policyVersion(),"reviewConfigSha256",base.base.config));e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);return;}
            var path=e.getRequestURI().getPath();var binding=bindings.get(path.substring(path.lastIndexOf('/')+1));assertNotNull(binding);reads.incrementAndGet();
            String state="v2".equals(binding.versionId())?secondState:"COMPLETED";
            var body=new LinkedHashMap<String,Object>();body.put("jobId",binding.jobId());body.put("requestId",binding.requestId());body.put("binding",Map.of("artifactSha256",binding.artifactSha256(),"contextSha256",binding.contextSha256(),"policyVersion",binding.policyVersion(),"reviewConfigSha256",binding.reviewConfigSha256()));
            body.put("state",state);body.put("workState",state);body.put("artifactRetained",true);body.put("createdAt",1000L);body.put("expiresAt",2000L);
            byte[] bytes=f.mapper.writeValueAsBytes(body);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);
        });
    }
    @AfterEach void cleanup(){base.cleanup();}
    @Test void multipleJobsContinueAcrossRestartWithoutRepeatingCompletedReads()throws Exception{
        var candidate=base.candidate();assertEquals("WAITING",base.automatic.advance(candidate,()->true).state());assertEquals(1,reads.get());assertEquals(1,base.f().mongo.getCollection(PROGRESS).countDocuments());
        Thread.sleep(1100);assertEquals("ADMITTED",base.create().advance(candidate,()->true).state());assertEquals(2,reads.get());assertEquals(2,base.f().mongo.getCollection(PROGRESS).countDocuments());assertEquals(0,base.f().posts.get());
    }
    @Test void lostCheckpointWriteReplyRecoversExactStoredProgress()throws Exception{
        var mongo=spy(base.f().mongo);var collection=spy(base.f().mongo.getCollection(PROGRESS));
        doReturn(collection).when(collection).withReadPreference(any());doReturn(collection).when(collection).withReadConcern(any());doReturn(collection).when(collection).withWriteConcern(any());doReturn(collection).when(collection).withTimeout(anyLong(),any());
        doReturn(collection).when(mongo).getCollection(PROGRESS);var writes=new AtomicInteger();doAnswer(call->{call.callRealMethod();writes.incrementAndGet();throw new com.mongodb.MongoException("lost checkpoint acknowledgment");}).when(collection).insertOne(any(Document.class));
        var coordinator=new ProjectMutationAutomaticAdmission(mongo,base.base.base.budget,base.base.base.history,base.base.prior,base.attempts,base.base.base.accounting,base.base.service,base.activator,base.base.base.base.base.base.archive);
        var candidate=base.candidate();assertEquals("WAITING",coordinator.advance(candidate,()->true).state());assertEquals(1,writes.get());Thread.sleep(1100);
        assertEquals("ADMITTED",base.create().advance(candidate,()->true).state());assertEquals(2,reads.get());assertEquals(0,base.f().posts.get());
    }
    @Test void slowReadYieldsWithinChildDeadlineAndRetainsUncertainty(){
        var f=base.f();f.server.removeContext("/api/v1/review-jobs");f.route(e->{reads.incrementAndGet();Thread.sleep(6500);e.close();});
        var client=new RemoteReviewClient(new net.modtale.config.properties.AppWardenProperties("http://127.0.0.1:"+f.server.getAddress().getPort(),"fixture-key",true,1,600),java.time.Duration.ofSeconds(20));
        try(var accounting=new ProjectMutationJobAccounting(f.mongo,base.base.base.budget,base.base.base.history,client,1)){
            var coordinator=new ProjectMutationAutomaticAdmission(f.mongo,base.base.base.budget,base.base.base.history,base.base.prior,base.attempts,accounting,base.base.service,base.activator,base.base.base.base.base.base.archive);
            long started=System.nanoTime();assertEquals("WAITING",coordinator.advance(base.candidate(),()->true).state());long elapsed=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            assertTrue(elapsed<9000,"Child read must leave the outer work window available: "+elapsed);assertEquals(1,reads.get());assertEquals(0,f.mongo.getCollection(PROGRESS).countDocuments());
            assertEquals("UNKNOWN",f.mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).find().first().getString("state"));assertEquals(0,f.posts.get());
        }
    }
    @Test void onlyUnfinishedJobGetsANewObservation()throws Exception{
        secondState="RUNNING";var candidate=base.candidate();assertEquals("WAITING",base.automatic.advance(candidate,()->true).state());Thread.sleep(1100);
        assertEquals("WAITING",base.create().advance(candidate,()->true).state());assertEquals(2,reads.get());assertEquals(1,base.f().mongo.getCollection(PROGRESS).countDocuments());
        secondState="COMPLETED";Thread.sleep(1100);assertEquals("ADMITTED",base.create().advance(candidate,()->true).state());assertEquals(3,reads.get());assertEquals(0,base.f().posts.get());
    }
    @ParameterizedTest @ValueSource(strings={"scope","missingReceipt","changedReceipt"})
    void progressCannotSubstituteForAuthenticatedOriginalEvidence(String change)throws Exception{
        var candidate=base.candidate();assertEquals("WAITING",base.automatic.advance(candidate,()->true).state());var collection=base.f().mongo.getCollection(PROGRESS);var progress=collection.find().first();assertNotNull(progress);
        if(change.equals("scope"))collection.updateOne(new Document("_id",progress.get("_id")),new Document("$set",new Document("scope.projectId","other")));
        else if(change.equals("missingReceipt"))base.f().mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).deleteOne(new Document("_id",progress.get("observationId")));
        else base.f().mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).updateOne(new Document("_id",progress.get("observationId")),new Document("$set",new Document("observation.status.state","RUNNING").append("observation.status.workState","RUNNING")));
        Thread.sleep(1100);assertThrows(RuntimeException.class,()->base.create().advance(candidate,()->true));assertEquals(1,reads.get());assertEquals(0,base.f().posts.get());assertEquals("RUNNING",base.attempts.status(base.scope(candidate),()->true).state());
    }
}
