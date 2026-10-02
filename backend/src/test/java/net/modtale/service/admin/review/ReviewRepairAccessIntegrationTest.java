package net.modtale.service.admin.review;

import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewRepairAccessIntegrationTest {
    ReviewIsolationExecutorTest fixture;AccountService accounts;ReviewRepairWorkflow workflow;ReviewRepairAccess access;
    ReviewRepairAccess.Position position=new ReviewRepairAccess.Position("STRING","p",0);
    @BeforeEach void setup() {
        fixture=new ReviewIsolationExecutorTest();fixture.setup();accounts=mock(AccountService.class);
        var actor=ReviewRepairAccessTest.user("actor");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));when(accounts.getCurrentUser(any())).thenReturn(actor);
        configure(fixture.archive);
    }
    void configure(ReviewSnapshotArchive archive) {
        var executor=new ReviewIsolationExecutor(fixture.mongo,archive,fixture.reader,fixture.journal);
        workflow=new ReviewRepairWorkflow(new ReviewRepairPreparation(archive,fixture.reader,Clock.systemUTC(),1,60000),executor,1);
        access=new ReviewRepairAccess(accounts,workflow,fixture.reader,executor);
    }
    @AfterEach void cleanup(){workflow.close();fixture.cleanup();SecurityContextHolder.clearContext();}
    ReviewRepairPreparation.Prepared prepare() {
        var preview=access.inspect(new ReviewRepairAccess.Inspect(position,"v"));assertTrue(preview.eligible());assertEquals("ISOLATE_LOCAL_REVIEW",preview.effect());
        return access.prepare(new ReviewRepairAccess.Prepare(UUID.randomUUID().toString(),position,"v",preview.sha256()));
    }
    @Test void exactPreviewPreparationExecutionAndReceiptAreComposed() {
        var before=fixture.version();var prepared=prepare();assertEquals(before,fixture.version());assertNull(fixture.operation());
        var result=access.execute(prepared);assertEquals("APPLIED",result.state());var receipt=access.receipt(prepared);assertEquals(result.state(),receipt.state());assertEquals(result.afterSha256(),receipt.afterSha256());assertEquals(position,receipt.position());assertEquals("v",receipt.versionId());assertEquals(prepared.sha256(),receipt.beforeSha256());
        assertEquals("BLOCK",fixture.version().get("scanResult",org.bson.Document.class).get("verdict"));
    }
    @Test void anotherModeratorCannotUseAnExistingPreparedActionOrReceipt() {
        var prepared=prepare();when(accounts.getCurrentUser(any())).thenReturn(ReviewRepairAccessTest.user("other"));
        assertThrows(SecurityException.class,()->access.execute(prepared));assertThrows(SecurityException.class,()->access.receipt(prepared));assertNull(fixture.operation());
    }
    @Test void permissionLossDuringArchivalReturnsNoPreparedAuthority() {
        var archive=spy(fixture.archive);doAnswer(i->{var result=i.callRealMethod();when(accounts.getCurrentUser(any())).thenReturn(null);return result;}).when(archive).retain(any());configure(archive);
        var before=fixture.version();assertThrows(SecurityException.class,this::prepare);assertEquals(before,fixture.version());assertNull(fixture.operation());
    }
    @Test void stalePreviewAndTamperedPreparedIntentCannotMutateVersion() {
        var preview=access.inspect(new ReviewRepairAccess.Inspect(position,"v"));fixture.set("changed",true);
        assertThrows(IllegalStateException.class,()->access.prepare(new ReviewRepairAccess.Prepare(UUID.randomUUID().toString(),position,"v",preview.sha256())));
        var prepared=prepare();assertThrows(IllegalStateException.class,()->access.execute(new ReviewRepairPreparation.Prepared(prepared.id(),prepared.sha256(),prepared.createdAt(),prepared.expiresAt()+1)));assertNull(fixture.operation());
    }
    @Test void storedAccountRevocationIsObservedDuringPreparation() {
        var actor=ReviewRepairAccessTest.user("actor");fixture.mongo.save(actor);
        var repository=mock(net.modtale.repository.user.UserRepository.class);
        when(repository.findById("actor")).thenAnswer(i->Optional.ofNullable(fixture.mongo.findById("actor",net.modtale.model.user.User.class)));
        var resolver=new net.modtale.service.user.account.CurrentUserResolutionService(repository);
        when(accounts.getCurrentUser(any())).thenAnswer(i->resolver.resolveCurrentUser(i.getArgument(0)));
        var archived=spy(fixture.archive);
        doAnswer(i->{var result=i.callRealMethod();assertEquals(1,fixture.mongo.updateFirst(
                org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is("actor")),
                new org.springframework.data.mongodb.core.query.Update().set("adminPermissions",List.of()),net.modtale.model.user.User.class).getModifiedCount());return result;}).when(archived).retain(any());
        configure(archived);assertThrows(SecurityException.class,this::prepare);assertNull(fixture.operation());
    }

    @Test void actualHttpPreviewPrepareExecuteAndReceiptReachTransactionalStorage() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new net.modtale.controller.admin.ReviewRepairController(access)).build();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var previewResponse=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/verification/repairs/inspect")
                .contentType("application/json").content(mapper.writeValueAsString(new ReviewRepairAccess.Inspect(position,"v"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store")).andReturn();
        var preview=mapper.readValue(previewResponse.getResponse().getContentAsString(),ReviewRepairAccess.Preview.class);assertTrue(preview.eligible());
        var preparedResponse=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/verification/repairs/prepare")
                .contentType("application/json").content(mapper.writeValueAsString(new ReviewRepairAccess.Prepare(UUID.randomUUID().toString(),position,"v",preview.sha256()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String prepared=preparedResponse.getResponse().getContentAsString();assertNull(fixture.operation());
        for(String route:List.of("execute","receipt"))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/verification/repairs/"+route)
                .contentType("application/json").content(prepared)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("APPLIED"));
        assertEquals("actor",fixture.operation().get("actor"));
    }

    @Test void typedProjectSavePreservesIsolationAndHistoricalReceipt() {
        var prepared=prepare();access.execute(prepared);var originalMarker=fixture.version().get("reviewIsolation",org.bson.Document.class);
        var project=fixture.mongo.findById("p",net.modtale.model.project.Project.class);
        assertNotNull(project.getVersions().getFirst().getReviewIsolation());project.getVersions().getFirst().setVersionNumber("edited");fixture.mongo.save(project);
        assertEquals(originalMarker,fixture.version().get("reviewIsolation",org.bson.Document.class));
        var receipt=access.receipt(prepared);assertEquals("APPLIED",receipt.state());assertEquals(position,receipt.position());assertEquals(prepared.sha256(),receipt.beforeSha256());
        assertNotEquals(receipt.afterSha256(),fixture.reader.capture("p",0,"v").sha256());
        fixture.projects.deleteOne(new org.bson.Document("_id","p"));assertEquals(receipt,access.receipt(prepared));
    }
    @Test void objectIdReceiptsRetainOriginalBsonIdentity() {
        var version=fixture.version();String id="abcdefabcdefabcdefabcdef";fixture.projects.deleteMany(new org.bson.Document());
        fixture.projects.insertOne(new org.bson.Document("_id",new org.bson.types.ObjectId(id)).append("versions",List.of(version)));
        position=new ReviewRepairAccess.Position("OBJECT_ID",id,0);var prepared=prepare();access.execute(prepared);
        var receipt=access.receipt(prepared);assertEquals(position,receipt.position());assertEquals("v",receipt.versionId());assertEquals(prepared.sha256(),receipt.beforeSha256());
    }

    @Test void recoverByIdRestoresAuthenticatedIntentWithoutExecutingIt() {
        var prepared=prepare();var before=fixture.version();var recovered=access.recover(prepared.id());
        assertEquals(prepared,recovered.prepared());assertEquals(position,recovered.receipt().position());assertEquals("UNKNOWN",recovered.receipt().state());assertEquals(before,fixture.version());assertNull(fixture.operation());
        access.execute(prepared);var applied=access.recover(prepared.id());assertEquals("APPLIED",applied.receipt().state());assertEquals(prepared,applied.prepared());
        when(accounts.getCurrentUser(any())).thenReturn(ReviewRepairAccessTest.user("other"));assertThrows(SecurityException.class,()->access.recover(prepared.id()));
    }
    @Test void operationDiscoveryAndRecoveryNeverReclaimAnUnknownClaim() {
        var prepared=prepare();var claim=fixture.journal.claim(prepared,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,()->true);fixture.journal.markUnknown(claim,()->true);
        new ReviewRepairOperationReader(fixture.mongo).initialize();var before=fixture.version();var operation=fixture.operation();
        var page=access.operations(null,25);assertEquals(List.of(new ReviewRepairOperationReader.Item(prepared.id(),"UNKNOWN")),page.items());
        assertEquals("UNKNOWN",access.recover(prepared.id()).receipt().state());assertEquals(before,fixture.version());assertEquals(operation,fixture.operation());
    }

    @Test void explicitClosureHttpActionPreservesOriginalIdentityAndDoesNotChangeVersion()throws Exception {
        var capture=fixture.reader.capture("p",0,"v");long now=System.currentTimeMillis();String id=UUID.randomUUID().toString();
        fixture.archive.retain(capture.forArchive(id,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,now-20000,now-10000));
        var prepared=new ReviewRepairPreparation.Prepared(id,capture.sha256(),now-20000,now-10000);
        assertNull(fixture.journal.claim(prepared,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,()->true));var before=fixture.version();
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new net.modtale.controller.admin.ReviewRepairController(access)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/verification/repairs/close-expired")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(prepared)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("NOT_APPLIED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.beforeSha256").value(prepared.sha256()));
        assertEquals(before,fixture.version());assertEquals(position,access.receipt(prepared).position());assertEquals("NOT_APPLIED",access.recover(id).receipt().state());
    }

}
