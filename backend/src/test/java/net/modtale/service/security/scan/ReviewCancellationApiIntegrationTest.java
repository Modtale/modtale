package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.*;
import net.modtale.controller.admin.ReviewCancellationController;
import net.modtale.service.admin.review.*;
import net.modtale.service.user.account.AccountService;
import net.modtale.model.user.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewCancellationApiIntegrationTest {
    @Configuration @EnableMethodSecurity static class MethodSecurity {}
    public static class Permissions {
        public boolean hasAdminPermission(String permission,Authentication auth){return auth!=null&&auth.getAuthorities().stream().anyMatch(a->permission.equals(a.getAuthority()));}
    }
    ReviewOrphanCancellationJournalTest base=new ReviewOrphanCancellationJournalTest();
    AnnotationConfigApplicationContext context;MockMvc mvc;ObjectMapper mapper=new ObjectMapper();
    AtomicReference<User> account=new AtomicReference<>();AtomicInteger cancellations=new AtomicInteger(),reads=new AtomicInteger();
    String prefix="/api/v1/admin/verification/cancellations";
    @BeforeEach void setup()throws Exception {
        base.setup();account.set(user());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("actor",null,List.of(new SimpleGrantedAuthority("PROJECT_REVIEW_READ"),new SimpleGrantedAuthority("PROJECT_VERSION_RESCAN"))));
        open();
    }
    User user(){var u=new User();u.setId("actor");u.setAdminPermissions(Set.of(AdminPermission.PROJECT_REVIEW_READ,AdminPermission.PROJECT_VERSION_RESCAN));return u;}
    void open() {
        var f=base.fixture.fixture;var accounts=mock(AccountService.class);when(accounts.getCurrentUser(any())).thenAnswer(i->account.get());
        var journal=base.create(f.mongo,Clock.systemUTC());
        var preparation=new ReviewRepairPreparation(base.fixture.archive,base.fixture.reader,Clock.systemUTC(),1,60000);
        var workflow=new ReviewRepairWorkflow(preparation,base.fixture.isolation,1);
        var executor=new ReviewOrphanCancellationExecutor(journal,f.client,1,Duration.ofSeconds(5));
        var reconciler=new ReviewCancellationReconciler(f.mongo,journal,f.client,1,Duration.ofSeconds(5));
        context=new AnnotationConfigApplicationContext();context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",Map.of("app.warden.repair.cancellation.enabled","true")));context.register(MethodSecurity.class);
        context.registerBean("apiSecurity",Permissions.class,Permissions::new);
        context.registerBean(ReviewRepairWorkflow.class,()->workflow);context.registerBean(ReviewOrphanCancellationExecutor.class,()->executor);context.registerBean(ReviewCancellationReconciler.class,()->reconciler);
        context.registerBean(ReviewCancellationAccess.class,()->new ReviewCancellationAccess(accounts,workflow,journal,executor,reconciler));
        context.registerBean(ReviewCancellationController.class,()->new ReviewCancellationController(context.getBean(ReviewCancellationAccess.class)));
        context.refresh();mvc=MockMvcBuilders.standaloneSetup(context.getBean(ReviewCancellationController.class)).build();
    }
    @AfterEach void cleanup(){if(context!=null)context.close();SecurityContextHolder.clearContext();base.cleanup();}
    JsonNode postJson(String route,Object body)throws Exception {
        var response=mvc.perform(post(prefix+route).contentType("application/json").content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray());
    }
    JsonNode getJson(String route)throws Exception {
        return mapper.readTree(mvc.perform(get(prefix+route)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray());
    }
    JsonNode prepare()throws Exception{return postJson("/prepare",Map.of("isolationId",base.fixture.prepared.id()));}
    @Test void duplicateHttpRequestsAndRestartRecoverOriginalAndLaterOutcomesWithoutRemoteRedispatch()throws Exception {
        var f=base.fixture.fixture;var before=base.fixture.raw();
        f.route(e->{if(e.getRequestMethod().equals("DELETE")){cancellations.incrementAndGet();e.close();}else{assertEquals("GET",e.getRequestMethod());f.reply(e,200,reads.incrementAndGet()==1?"CANCELLED":"COMPLETED");}});
        var preview=getJson("/targets/"+base.fixture.prepared.id());assertEquals(f.job,preview.path("jobId").asText());
        var original=prepare();assertEquals(preview.path("targetSha256"),original.path("targetSha256"));
        var executed=postJson("/execute",original);assertEquals("UNKNOWN",executed.path("state").asText());
        assertEquals(executed,postJson("/execute",original));assertEquals(1,cancellations.get());
        var initial=getJson("/operations/"+base.fixture.prepared.id());assertEquals(executed.path("receipt"),initial);
        String id=UUID.randomUUID().toString();var check=Map.of("id",id,"original",original);
        var observed=postJson("/checks",check);assertEquals("CANCELLED",observed.path("observation").path("status").path("state").asText());
        assertEquals(observed,postJson("/checks",check));assertEquals(observed,postJson("/checks/receipt",check));assertEquals(1,reads.get());
        context.close();open();assertEquals(initial,getJson("/operations/"+base.fixture.prepared.id()));assertEquals(observed,postJson("/checks/receipt",check));
        assertEquals(executed,postJson("/execute",original));assertEquals(observed,postJson("/checks",check));assertEquals(1,cancellations.get());assertEquals(1,reads.get());
        var second=postJson("/checks",Map.of("id",UUID.randomUUID().toString(),"original",original));assertEquals("COMPLETED",second.path("observation").path("status").path("state").asText());
        assertEquals(initial,getJson("/operations/"+base.fixture.prepared.id()));assertEquals(observed,postJson("/checks/receipt",check));
        assertEquals(2,f.mongo.getCollection(ReviewCancellationReconciler.COLLECTION).countDocuments());assertEquals(before,base.fixture.raw());
        assertEquals("BLOCK",f.saved().getVerdict());assertEquals(0,f.identityGets.get());verifyNoInteractions(f.storage);
    }
    @Test void storedAccountRevocationAfterDispatchCannotReturnAuthorityOrCauseAnotherCancellation()throws Exception {
        var f=base.fixture.fixture;var original=prepare();
        f.route(e->{assertEquals("DELETE",e.getRequestMethod());cancellations.incrementAndGet();account.set(null);f.reply(e,200,"CANCELLED");});
        mvc.perform(post(prefix+"/execute").contentType("application/json").content(mapper.writeValueAsBytes(original)))
                .andExpect(status().isForbidden()).andExpect(content().string(""));assertEquals(1,cancellations.get());
        account.set(user());var recovered=getJson("/operations/"+base.fixture.prepared.id());assertEquals("DISPATCHING",recovered.path("state").asText());
        assertEquals("DISPATCHING",postJson("/execute",original).path("state").asText());assertEquals(1,cancellations.get());
    }
    @Test void forgedIntentAndFreshAccountDenialCannotCreateRemoteWork()throws Exception {
        var f=base.fixture.fixture;f.route(e->{assertEquals("DELETE",e.getRequestMethod());cancellations.incrementAndGet();f.reply(e,200,"CANCELLED");});var original=prepare();
        var forged=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)forged).put("targetSha256","f".repeat(64));
        // Unknown execution response carries no claim; the retained intent still cannot be changed.
        assertEquals("UNKNOWN",postJson("/execute",forged).path("state").asText());assertEquals(0,cancellations.get());
        var denied=user();denied.setAdminPermissions(Set.of(AdminPermission.PROJECT_REVIEW_READ));account.set(denied);
        mvc.perform(get(prefix+"/targets/"+base.fixture.prepared.id())).andExpect(status().isForbidden());
        mvc.perform(post(prefix+"/execute").contentType("application/json").content(mapper.writeValueAsBytes(original))).andExpect(status().isForbidden());
        assertEquals(0,cancellations.get());assertEquals("PREPARED",base.operations().find().first().getString("state"));assertEquals(0,f.mongo.getCollection(ReviewCancellationReconciler.COLLECTION).countDocuments());
    }
}
