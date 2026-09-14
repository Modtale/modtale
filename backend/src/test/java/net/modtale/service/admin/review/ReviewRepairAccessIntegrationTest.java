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
        var result=access.execute(prepared);assertEquals("APPLIED",result.state());assertEquals(result,access.receipt(prepared));
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

}
