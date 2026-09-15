package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationPriorWorkReaderTest {
    ProjectMutationJobAccountingTest base=new ProjectMutationJobAccountingTest();ProjectMutationPriorWorkReader reader;
    @BeforeEach void setup()throws Exception{base.setup();reader=new ProjectMutationPriorWorkReader(base.base.base.base.fixture.mongo,base.budget,base.history);}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationPriorWorkReader.Inventory read(String id){return reader.read(base.project,id,()->true);}
    @Test void replacementIncludesRemovedOriginalJobAndNewVersionButNotUnchangedSibling(){
        var current=base.base.root();var result=read(base.prepared.id());assertEquals(List.of(base.prepared.id()),result.groups());assertEquals(2,result.work().size());
        var old=result.work().stream().filter(w->w.versionId().equals("v")).findFirst().orElseThrow();assertEquals(ProjectMutationPriorWorkReader.Kind.REMOTE_JOB,old.kind());assertEquals(base.base.base.base.fixture.job,old.binding().jobId());
        var fresh=result.work().stream().filter(w->w.versionId().equals("new-upload")).findFirst().orElseThrow();assertEquals(ProjectMutationPriorWorkReader.Kind.NEW_VERSION,fresh.kind());assertNull(fresh.binding());
        assertEquals(current,base.base.root());assertEquals(0,base.base.base.base.fixture.posts.get());
    }
    @ParameterizedTest @ValueSource(strings={"missing","jobMissing","wrongArtifact","malformed"})
    void unusableOriginalIdentityIsUnresolvedRatherThanNoPriorWork(String type){
        var id=restage(v->{var scan=v.get("scanResult",Document.class);var remote=scan.get("remoteReview",Document.class);
            switch(type){case "missing"->v.remove("scanResult");case "jobMissing"->remote.put("jobId",null);case "wrongArtifact"->remote.put("artifactSha256","0".repeat(64));case "malformed"->scan.put("remoteReview","bad");}
        });
        var original=read(id).work().stream().filter(w->w.versionId().equals("v")).findFirst().orElseThrow();assertEquals(ProjectMutationPriorWorkReader.Kind.UNRESOLVED,original.kind());assertNull(original.binding());
    }
    @Test void heldChainPreservesRemovedJobsFromEarlierReplacementGroup(){
        String next=editHeld("next");var result=read(next);assertEquals(List.of(next,base.prepared.id()),result.groups());
        assertEquals(1,result.work().stream().filter(w->w.kind()==ProjectMutationPriorWorkReader.Kind.REMOTE_JOB).count());
        assertTrue(result.work().stream().anyMatch(w->w.mutationId().equals(base.prepared.id()) && w.versionId().equals("v")));
        base.base.base.base.fixture.mongo.getCollection("projects").deleteMany(new Document());assertEquals(result,read(next));
    }
    @Test void alteredHeldPointerCannotHideEarlierWork(){
        var mongo=base.base.base.base.fixture.mongo;mongo.getCollection("projects").updateOne(new Document("_id",base.project),new Document("$set",new Document("versions.1.versionMutation.beforeSha256","0".repeat(64))));
        String next=editHeld("changed");assertThrows(IllegalStateException.class,()->read(next));
    }
    @Test void chainLimitFailsClosedInsteadOfReturningPartialInventory(){
        String last=base.prepared.id();for(int i=0;i<7;i++)last=editHeld("runtime-"+i);assertEquals(8,read(last).groups().size());
        String ninth=editHeld("runtime-8");assertThrows(IllegalStateException.class,()->read(ninth));
    }
    @Test void permissionAndSharedShutdownPreventReading(){
        assertThrows(SecurityException.class,()->reader.read(base.project,base.prepared.id(),()->false));base.budget.close();assertThrows(IllegalStateException.class,()->read(base.prepared.id()));
    }
    @Test void unsupportedPriorTransitionIsExplicitEvenWhenItsCurrentJobIsValid(){
        String id=restage(v->v.put("reviewReplacement",new Document("operationId",UUID.randomUUID().toString())));
        var work=read(id).work();assertTrue(work.stream().anyMatch(w->w.kind()==ProjectMutationPriorWorkReader.Kind.REMOTE_JOB));
        assertTrue(work.stream().anyMatch(w->"PRIOR_TRANSITION_REQUIRES_ACCOUNTING".equals(w.reason())));
    }
    @Test void workLimitDoesNotSilentlyOmitRemovedVersions(){
        var original=base.base.root();var many=new ArrayList<Document>();
        for(int i=0;i<257;i++)many.add(new Document("_id","old-"+i).append("reviewStatus","PENDING"));
        original.put("versions",many);original.put("status","DRAFT");
        base.base.base.base.fixture.mongo.getCollection("projects").replaceOne(new Document("_id",base.project),original);
        var captured=base.base.base.service.capture(base.project,()->true);original.put("versions",List.of());
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),base.project,captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(original));
        var prepared=base.base.base.service.prepare(request,()->true);assertEquals("APPLIED",base.base.executor().apply(prepared,"owner",()->true).state());
        assertThrows(IllegalStateException.class,()->read(prepared.id()));
    }
    String restage(Consumer<Document> edit){
        var old=new RawBsonDocument(base.base.base.base.archive.load(base.prepared.beforeArchiveId()).versionBytes()).decode(new DocumentCodec());edit.accept(old.getList("versions",Document.class).getFirst());
        base.base.base.base.fixture.mongo.getCollection("projects").replaceOne(new Document("_id",base.project),old);
        var prepared=base.base.base.service.prepare(base.base.base.request(),()->true);assertEquals("APPLIED",base.base.executor().apply(prepared,"owner",()->true).state());return prepared.id();
    }
    String editHeld(String runtime){
        var captured=base.base.base.service.capture(base.project,()->true);var next=new RawBsonDocument(captured.bytes()).decode(new DocumentCodec());
        next.getList("versions",Document.class).stream().filter(v->"new-upload".equals(v.get("_id"))).findFirst().orElseThrow().put("gameVersions",List.of(runtime));
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),base.project,captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(next));
        var prepared=base.base.base.service.prepare(request,()->true);assertEquals("APPLIED",base.base.executor().apply(prepared,"owner",()->true).state());return prepared.id();
    }
}
