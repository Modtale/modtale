package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.bson.types.Binary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationAdmissionReaderTest {
    ProjectMutationActivatorTest base=new ProjectMutationActivatorTest();ProjectMutationAdmissionReader reader;
    @BeforeEach void setup()throws Exception{base.setup();assertEquals("APPLIED",base.activate().state());reader=new ProjectMutationAdmissionReader(base.f().mongo,base.base.base.base.base.base.archive);}
    @AfterEach void cleanup(){base.cleanup();}
    Object project(){return base.base.base.project;}
    Document root(){return base.f().mongo.getCollection("projects").find().first();}
    void heads(){base.base.base.history.requireHeldHeads(project(),VersionMutationPreparationTest.bytes(root()),()->true);}
    void change(String path,Object value){base.f().mongo.getCollection("projects").updateOne(new Document("_id",project()),new Document("$set",new Document("versions.1."+path,value)));}
    @Test void pendingAdmissionAllowsMetadataAndWorkerProgress(){
        heads();change("changelog","revised notes");change("scanResult.remoteReview.jobId",UUID.randomUUID().toString());
        change("scanResult.remotePoll",new Document("token",null).append("leaseUntil",new Date()).append("nextPollAt",new Date()));heads();
        assertEquals(base.decision,reader.read(project(),base.decision.binding().requestId(),()->true).decision());
    }
    @ParameterizedTest @ValueSource(strings={"hash","gameVersions","replacementSecurityHold","versionMutation.beforeSha256","scanResult.remoteReview.reviewConfigSha256","scanResult.remoteReview.origin.deploymentId","scanResult.scanAttempt","unexpected"})
    void changedArtifactAuthorityOrUnknownFieldsReject(String field){
        Object value=switch(field){case "gameVersions"->List.of("changed");case "scanResult.scanAttempt"->3;case "replacementSecurityHold","scanResult.remoteReview.origin.deploymentId"->UUID.randomUUID().toString();default->"0".repeat(64);};
        change(field,value);assertThrows(RuntimeException.class,this::heads);
    }
    @Test void missingMutationPointerCannotHideAnExistingAdmission(){
        change("versionMutation",null);assertThrows(IllegalStateException.class,this::heads);
    }
    @Test void restoredHeldBytesCannotHideAnExistingAdmission(){
        var source=reader.read(project(),base.decision.binding().requestId(),()->true).source();
        var payload=new RawBsonDocument(source.versionBytes()).decode(new DocumentCodec());var held=new RawBsonDocument(payload.get("heldVersion",Binary.class).getData()).decode(new DocumentCodec());
        base.f().mongo.getCollection("projects").updateOne(new Document("_id",project()),new Document("$set",new Document("versions.1",held)));
        assertThrows(IllegalStateException.class,this::heads);
    }
    @Test void scheduledAndPrunedBindingsRetainAdmissionProvenance(){
        base.existingWorkerDeliversAndCompletesTheFixedRequestAfterRestart();heads();
        var version=base.version();var binding=version.get("scanResult",Document.class).get("remoteReview",Document.class);
        change("retainedRemoteReview",binding);change("scanResult",null);change("reviewStatus","APPROVED");heads();
        // This validates origin only: no clearance result is produced by this reader.
        assertNotNull(reader.read(project(),base.decision.binding().requestId(),()->true));
    }
    @Test void nextContextEditAccountsForCurrentJobWithoutRepeatingRetiredHistory(){
        base.existingWorkerDeliversAndCompletesTheFixedRequestAfterRestart();var a=base.base.base;
        var preparation=a.base.base.service;var captured=preparation.capture(project(),()->true);var next=new RawBsonDocument(captured.bytes()).decode(new DocumentCodec());
        next.getList("versions",Document.class).get(1).put("gameVersions",List.of("new-runtime"));
        var workflow=new ProjectMutationWorkflow(a.budget,preparation,a.base.executor(),a.history);
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),project(),captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(next));
        var prepared=workflow.prepare(request,()->true);assertEquals("APPLIED",workflow.apply(prepared,"owner",()->true).state());heads();
        var inventory=new ProjectMutationPriorWorkReader(base.f().mongo,a.budget,a.history).read(project(),prepared.id(),()->true);
        assertEquals(1,inventory.work().size());assertEquals(ProjectMutationPriorWorkReader.Kind.REMOTE_JOB,inventory.work().getFirst().kind());assertEquals(base.f().job,inventory.work().getFirst().binding().jobId());
    }
    @Test void missingOrChangedReceiptCannotAuthenticateAHead(){
        base.f().mongo.getCollection(ReviewRepairJournal.COLLECTION).updateOne(new Document("_id",base.decision.id()),new Document("$set",new Document("state","UNKNOWN")));
        assertThrows(IllegalStateException.class,this::heads);
    }
    @Test void historicalReadSurvivesProjectDeletionAndStillRequiresPermission(){
        var result=reader.read(project(),base.decision.binding().requestId(),()->true);base.f().mongo.getCollection("projects").deleteMany(new Document());
        assertEquals(result.decision(),reader.read(project(),base.decision.binding().requestId(),()->true).decision());
        assertThrows(SecurityException.class,()->reader.read(project(),base.decision.binding().requestId(),()->false));
    }
}
