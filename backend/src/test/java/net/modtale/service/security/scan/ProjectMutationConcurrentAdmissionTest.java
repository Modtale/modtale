package net.modtale.service.security.scan;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.UpdateOptions;
import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationConcurrentAdmissionTest {
    ProjectMutationAutomaticAdmissionTest base=new ProjectMutationAutomaticAdmissionTest();
    @BeforeEach void setup()throws Exception{base.setup();}
    @AfterEach void cleanup(){base.cleanup();}
    List<ProjectMutationAdmissionPreparation.Prepared> prepared(){
        var f=base.f();var archive=base.base.base.base.base.base.archive;var project=base.candidate().projectId();
        var original=new RawBsonDocument(archive.load(base.base.base.prepared.beforeArchiveId()).versionBytes()).decode(new DocumentCodec());f.mongo.getCollection("projects").replaceOne(new Document("_id",project),original);
        var preparation=base.base.base.base.base.service;var captured=preparation.capture(project,()->true);var versions=new ArrayList<>(original.getList("versions",Document.class));
        for(String id:List.of("alpha","beta"))versions.add(new Document("_id",id).append("reviewStatus","PENDING").append("fileUrl",f.binding.filePath()).append("hash",f.binding.artifactSha256()));original.put("versions",versions);
        var group=preparation.prepare(new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),project,captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(original)),()->true);
        assertEquals("APPLIED",base.base.base.base.executor().apply(group,"owner",()->true).state());var results=new ArrayList<ProjectMutationAdmissionPreparation.Prepared>();
        for(var candidate:new ProjectMutationDiscovery(f.mongo).page(null,64).candidates()){
            var held=new RawReviewSnapshotReader(f.mongo).capture(project,candidate.versionIndex(),candidate.versionId());
            results.add(base.base.service.prepare(new ProjectMutationAdmissionPreparation.Request(UUID.randomUUID().toString(),project,candidate.versionIndex(),candidate.versionId(),held.sha256(),candidate.mutationId(),"moderator",Map.of(),false),()->true));
        }
        assertEquals(2,results.size());return results;
    }
    @Test void simultaneousSiblingAdmissionsDoNotLoseVerifiedWorkToWriteConflict()throws Exception{
        var decisions=prepared();var f=base.f();var projects=spy(f.mongo.getCollection("projects"));
        doReturn(projects).when(projects).withReadPreference(any());doReturn(projects).when(projects).withReadConcern(any());doReturn(projects).when(projects).withWriteConcern(any());doReturn(projects).when(projects).withTimeout(anyLong(),any());
        var mongo=spy(f.mongo);doReturn(projects).when(mongo).getCollection("projects");var snapshotsReady=new CountDownLatch(2);var writes=new AtomicInteger();
        doAnswer(call->{assertTrue(f.mongo.collectionExists(ProjectMutationActivator.ADMISSIONS));if(writes.getAndIncrement()<2){snapshotsReady.countDown();assertTrue(snapshotsReady.await(10,TimeUnit.SECONDS));}return call.callRealMethod();})
                .when(projects).updateOne(any(ClientSession.class),any(Bson.class),any(Bson.class),any(UpdateOptions.class));
        try(var budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),2);var workers=Executors.newFixedThreadPool(2)){
            var archive=base.base.base.base.base.base.archive;var activator=new ProjectMutationActivator(mongo,budget,base.base.service,archive,new ReviewRepairJournal(f.mongo,archive));
            var first=workers.submit(()->activator.activate(decisions.get(0),"moderator",()->true));var second=workers.submit(()->activator.activate(decisions.get(1),"moderator",()->true));
            var outcomes=List.of(first.get(25,TimeUnit.SECONDS),second.get(25,TimeUnit.SECONDS));assertTrue(outcomes.stream().allMatch(r->"APPLIED".equals(r.state())),outcomes.toString());
            for(var decision:decisions)assertEquals("APPLIED",activator.receipt(decision,"moderator",()->true).state());
        }
        assertTrue(writes.get()>=3);assertEquals(2,f.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());assertEquals(0,f.posts.get());
    }
    @Test void changedHeldBytesAreRecapturedBeforeRetry(){
        var decision=prepared().getFirst();var f=base.f();var projects=spy(f.mongo.getCollection("projects"));
        doReturn(projects).when(projects).withReadPreference(any());doReturn(projects).when(projects).withReadConcern(any());doReturn(projects).when(projects).withWriteConcern(any());doReturn(projects).when(projects).withTimeout(anyLong(),any());
        var mongo=spy(f.mongo);doReturn(projects).when(mongo).getCollection("projects");var writes=new AtomicInteger();var archive=base.base.base.base.base.base.archive;var source=archive.load(decision.id());
        doAnswer(call->{writes.incrementAndGet();f.mongo.getCollection("projects").updateOne(new Document("_id",source.projectId()),new Document("$set",new Document("versions."+source.versionIndex()+".hash","0".repeat(64))));
            var conflict=new com.mongodb.MongoException(112,"fixture concurrent artifact edit");conflict.addLabel(com.mongodb.MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);throw conflict;})
                .when(projects).updateOne(any(ClientSession.class),any(Bson.class),any(Bson.class),any(UpdateOptions.class));
        var activator=new ProjectMutationActivator(mongo,base.base.base.budget,base.base.service,archive,new ReviewRepairJournal(f.mongo,archive));
        assertEquals("NOT_APPLIED",activator.activate(decision,"moderator",()->true).state());assertEquals(1,writes.get());assertEquals(0,f.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());assertEquals(0,f.posts.get());
    }
    @ParameterizedTest @CsvSource({"112,true,false,3","112,false,false,1","91,true,false,1","112,true,true,1"})
    void retriesRequireExactConflictEvidenceAndConfirmedAbort(int code,boolean labelled,boolean abortFails,int expectedWrites){
        var decision=prepared().getFirst();var f=base.f();var projects=spy(f.mongo.getCollection("projects"));
        doReturn(projects).when(projects).withReadPreference(any());doReturn(projects).when(projects).withReadConcern(any());doReturn(projects).when(projects).withWriteConcern(any());doReturn(projects).when(projects).withTimeout(anyLong(),any());
        var mongo=spy(f.mongo);doReturn(projects).when(mongo).getCollection("projects");var writes=new AtomicInteger();
        doAnswer(call->{writes.incrementAndGet();var failure=new com.mongodb.MongoException(code,"fixture database failure");if(labelled)failure.addLabel(com.mongodb.MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);throw failure;})
                .when(projects).updateOne(any(ClientSession.class),any(Bson.class),any(Bson.class),any(UpdateOptions.class));
        if(abortFails){var factory=spy(mongo.getMongoDatabaseFactory());doReturn(factory).when(mongo).getMongoDatabaseFactory();
            doAnswer(call->{var session=spy((ClientSession)call.callRealMethod());doThrow(new com.mongodb.MongoException("abort response unavailable")).when(session).abortTransaction();return session;}).when(factory).getSession(any(com.mongodb.ClientSessionOptions.class));}
        var archive=base.base.base.base.base.base.archive;var activator=new ProjectMutationActivator(mongo,base.base.base.budget,base.base.service,archive,new ReviewRepairJournal(f.mongo,archive));
        assertEquals("UNKNOWN",activator.activate(decision,"moderator",()->true).state());assertEquals(expectedWrites,writes.get());assertEquals(0,f.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());assertEquals(0,f.posts.get());
    }
}
