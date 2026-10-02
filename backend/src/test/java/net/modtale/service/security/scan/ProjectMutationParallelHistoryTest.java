package net.modtale.service.security.scan;

import com.mongodb.WriteConcern;
import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationParallelHistoryTest {
    ProjectMutationHistoryBudgetTest first=new ProjectMutationHistoryBudgetTest(),second=new ProjectMutationHistoryBudgetTest();
    @BeforeEach void setup()throws Exception{first.setup();second.base.f().project="1234567890abcdef12345678";second.setup();}
    @AfterEach void cleanup(){try{second.cleanup();}finally{first.cleanup();}}
    record Pipeline(ProjectMutationJobAccounting accounting,ProjectMutationAutomaticAdmission automatic) implements AutoCloseable {public void close(){accounting.close();}}
    Pipeline pipeline(MongoTemplate mongo,ReviewRepairWorkflow budget,ReviewSnapshotArchive archive,RemoteReviewClient client){
        var groupPreparation=new ProjectMutationPreparation(mongo,archive,Clock.systemUTC(),60000);var journal=new ReviewRepairJournal(mongo,archive);
        var groupExecutor=new ProjectMutationExecutor(mongo,groupPreparation,archive,journal);var history=new ProjectMutationReferenceReader(mongo,archive,groupPreparation,groupExecutor);history.initialize();
        var prior=new ProjectMutationPriorWorkReader(mongo,budget,history);var accounting=new ProjectMutationJobAccounting(mongo,budget,history,client,2);
        var preparation=new ProjectMutationAdmissionPreparation(mongo,budget,archive,history,prior,accounting,client,Clock.systemUTC(),60000);
        var activator=new ProjectMutationActivator(mongo,budget,preparation,archive,journal);
        return new Pipeline(accounting,new ProjectMutationAutomaticAdmission(mongo,budget,history,prior,new ProjectMutationAdmissionAttempts(mongo,budget),accounting,preparation,activator,archive));
    }
    @RepeatedTest(5) void largeIndependentProjectsShareCapacityWithoutLosingEvidence()throws Exception{
        long firstBytes=first.stage(2790000,8),secondBytes=second.stage(2790000,8);assertTrue(firstBytes>60L*1024*1024 && secondBytes>60L*1024*1024);
        var a=first.base.f();var b=second.base.f();var left=first.base.candidate();var right=second.base.candidate();assertNotEquals(left.projectId(),right.projectId());
        var expected=Map.of(left.projectId(),first.base.base.prior.read(left.projectId(),left.mutationId(),()->true),
                right.projectId(),second.base.base.prior.read(right.projectId(),right.mutationId(),()->true));
        // Both fixtures use the same isolated signing key; copy their complete, still-authenticated records into one test database.
        for(String name:b.mongo.getCollectionNames()){
            var target=a.mongo.getCollection(name).withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
            try(var rows=b.mongo.getCollection(name).find().batchSize(16).iterator()){while(rows.hasNext())target.insertOne(rows.next());}
        }
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var configurations=new AtomicInteger();
        var clientA=spy(a.client);var clientB=spy(b.client);
        for(var client:List.of(clientA,clientB))doAnswer(call->{if(configurations.getAndIncrement()<2){entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));}return call.callRealMethod();}).when(client).configuration(any(),any());
        try(var budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),2);
            var p=pipeline(a.mongo,budget,first.base.base.base.base.base.base.archive,clientA);
            var q=pipeline(a.mongo,budget,first.base.base.base.base.base.base.archive,clientB);
            var workers=Executors.newFixedThreadPool(2)){
            long started=System.nanoTime();var leftResult=workers.submit(()->p.automatic().advance(left,()->true));var rightResult=workers.submit(()->q.automatic().advance(right,()->true));
            try{assertTrue(entered.await(20,TimeUnit.SECONDS));assertEquals(2,budget.status().active());
                assertEquals("Review repair is busy",assertThrows(IllegalStateException.class,()->p.automatic().advance(left,()->true)).getMessage());
                assertEquals(2,a.mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).countDocuments());
            }finally{release.countDown();}
            var leftOutcome=leftResult.get(30,TimeUnit.SECONDS);var rightOutcome=rightResult.get(30,TimeUnit.SECONDS);
            assertEquals("ADMITTED",leftOutcome.state(),()->"left="+leftOutcome+" right="+rightOutcome+" journals="+a.mongo.getCollection(ReviewRepairJournal.COLLECTION).find().projection(new Document("state",1).append("action",1)).into(new ArrayList<>()));
            assertEquals("ADMITTED",rightOutcome.state());assertEquals(0,budget.status().active());
            var records=a.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).find().into(new ArrayList<Document>());assertEquals(2,records.size());
            assertEquals(Set.of(left.projectId(),right.projectId()),records.stream().map(d->d.get("projectId")).collect(java.util.stream.Collectors.toSet()));
            for(var record:records){var evidence=first.base.base.base.base.base.base.archive.load(record.getString("decisionId"));assertEquals(record.get("projectId"),evidence.projectId());
                var payload=new org.bson.RawBsonDocument(evidence.versionBytes()).decode(new org.bson.codecs.DocumentCodec());
                var inventory=expected.get(record.get("projectId"));
                assertEquals(inventory.groups(),payload.getList("groups",String.class));
                var items=payload.getList("evidence",Document.class);assertEquals(inventory.work().size(),items.size());
                var observations=payload.get("observations",Document.class);assertEquals(1,observations.size());
                for(int i=0;i<items.size();i++){
                    var work=inventory.work().get(i);var item=items.get(i);
                    assertEquals(work.mutationId(),item.getString("mutationId"));assertEquals(work.versionId(),item.getString("versionId"));
                    assertEquals(work.beforeSha256(),item.getString("beforeSha256"));assertEquals(work.kind().name(),item.getString("kind"));
                    if(work.kind()==ProjectMutationPriorWorkReader.Kind.REMOTE_JOB)
                        assertEquals(observations.getString(work.mutationId()+"/"+work.versionId()),item.get("observation",Document.class).getString("id"));
                }
                assertEquals("PRIOR_WORK_ACCOUNTED",payload.getString("rule"));}
            System.out.println("parallelProjects=2 groupsEach=8 sourceBytes="+(firstBytes+secondBytes)+" elapsedMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        }
        assertEquals(0,a.posts.get());assertEquals(0,b.posts.get());
    }
}
