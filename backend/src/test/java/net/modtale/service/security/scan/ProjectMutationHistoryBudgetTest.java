package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationHistoryBudgetTest {
    ProjectMutationAutomaticAdmissionTest base=new ProjectMutationAutomaticAdmissionTest();
    @BeforeEach void setup()throws Exception{base.setup();}
    @AfterEach void cleanup(){base.cleanup();}
    @ParameterizedTest @CsvSource({"2790000,8,false","2800000,8,true","0,9,true"})
    void historyBoundsNeverPermitPartialAdmission(int padding,int groupCount,boolean exceedsLimit){
        long sourceBytes=stage(padding,groupCount);var f=base.f();var archive=base.base.base.base.base.base.archive;var project=base.candidate().projectId();
        var candidate=base.candidate();long started=System.nanoTime();
        assertEquals(exceedsLimit,sourceBytes>64L*1024*1024 || groupCount>8);
        if(exceedsLimit){
            assertThrows(ProjectMutationPriorWorkReader.LimitExceeded.class,()->base.base.prior.read(project,candidate.mutationId(),()->true));
            assertEquals("ATTENTION",base.automatic.advance(candidate,()->true).state());
            assertEquals("ATTENTION",base.create().advance(candidate,()->true).state());
            assertEquals(1,base.attempts.status(base.scope(candidate),()->true).attempts());
            assertEquals(0,f.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());assertEquals(0,f.gets.get());
        }else{
            assertTrue(sourceBytes>60L*1024*1024);var inventory=base.base.prior.read(project,candidate.mutationId(),()->true);assertEquals(8,inventory.groups().size());
            assertEquals(1,inventory.work().stream().filter(w->w.kind()==ProjectMutationPriorWorkReader.Kind.REMOTE_JOB).count());
            var admission=base.base.create(archive,Clock.systemUTC());var activator=new ProjectMutationActivator(f.mongo,base.base.base.budget,admission,archive,new ReviewRepairJournal(f.mongo,archive));
            var automatic=new ProjectMutationAutomaticAdmission(f.mongo,base.base.base.budget,base.base.base.history,base.base.prior,base.attempts,base.base.base.accounting,admission,activator,archive);
            var result=automatic.advance(candidate,()->true);assertEquals("ADMITTED",result.state());
            var decision=new RawBsonDocument(archive.load(result.decisionId()).versionBytes()).decode(new DocumentCodec());assertEquals(8,decision.getList("groups",String.class).size());assertEquals("PRIOR_WORK_ACCOUNTED",decision.getString("rule"));
        }
        assertEquals(0,f.posts.get());System.out.println("historyGroups="+groupCount+" sourceBytes="+sourceBytes+" validationMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
    }
    long stage(int padding,int groupCount){
        var f=base.f();var archive=base.base.base.base.base.base.archive;var project=base.candidate().projectId();
        var source=new RawBsonDocument(base.base.base.history.read(project,base.candidate().mutationId(),()->true).evidence().before().versionBytes()).decode(new DocumentCodec());
        source.put("fixturePadding","x".repeat(padding));f.mongo.getCollection("projects").replaceOne(new Document("_id",project),source);
        var preparation=new ProjectMutationPreparation(f.mongo,archive,Clock.systemUTC(),60000);
        var executor=new ProjectMutationExecutor(f.mongo,preparation,archive,new ReviewRepairJournal(f.mongo,archive));
        var groups=new ArrayList<ProjectMutationPreparation.Prepared>();long sourceBytes=0;
        for(int i=0;i<groupCount;i++){
            var captured=preparation.capture(project,()->true);var next=new RawBsonDocument(captured.bytes()).decode(new DocumentCodec());
            if(i==0)next.put("versions",List.of(next.getList("versions",Document.class).get(1),new Document("_id","new-upload").append("reviewStatus","PENDING").append("fileUrl",f.binding.filePath()).append("hash",f.binding.artifactSha256())));
            else next.getList("versions",Document.class).get(1).put("gameVersions",List.of("runtime-"+i));
            var prepared=preparation.prepare(new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),project,captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(next)),()->true);
            assertEquals("APPLIED",executor.apply(prepared,"owner",()->true).state());groups.add(prepared);
            sourceBytes+=archive.load(prepared.beforeArchiveId()).versionBytes().length+archive.load(prepared.afterArchiveId()).versionBytes().length;
            String applied=UUID.nameUUIDFromBytes(("project-mutation-applied-1:"+prepared.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();sourceBytes+=archive.load(applied).versionBytes().length;
        }
        assertEquals(groups.getLast().id(),base.candidate().mutationId());return sourceBytes;
    }
}
