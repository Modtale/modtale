package net.modtale.controller.admin;

import net.modtale.service.admin.review.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ReviewCancellationControllerTest.Config.class)
class ReviewCancellationControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ReviewCancellationAccess access(){return mock(ReviewCancellationAccess.class);}
        @Bean ReviewCancellationController controller(ReviewCancellationAccess access){return new ReviewCancellationController(access);}
        @Bean(name="apiSecurity") ReviewRepairControllerTest.Permissions permissions(){return new ReviewRepairControllerTest.Permissions();}
    }
    @Autowired ReviewCancellationController controller;@Autowired ReviewCancellationAccess access;
    String id="11111111-1111-1111-1111-111111111111",checkId="22222222-2222-2222-2222-222222222222";
    ReviewOrphanCancellationJournal.Prepared original=new ReviewOrphanCancellationJournal.Prepared(id,"a".repeat(64),1,60001);
    String body(){return "{\"isolationId\":\""+id+"\",\"targetSha256\":\""+"a".repeat(64)+"\",\"createdAt\":1,\"expiresAt\":60001}";}
    @BeforeEach void setup(){reset(access);auth("PROJECT_REVIEW_READ","PROJECT_VERSION_RESCAN");}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    void auth(String...permissions){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("actor",null,Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));}
    @Test void allRoutesRequireBothPermissionsBeforeAccess() {
        for(String permission:List.of("PROJECT_REVIEW_READ","PROJECT_VERSION_RESCAN","PROJECT_REVIEW_DECIDE","ROLE_USER")) {
            auth(permission);assertThrows(AccessDeniedException.class,()->controller.history(null));assertThrows(AccessDeniedException.class,()->controller.preview(id));assertThrows(AccessDeniedException.class,()->controller.capabilities());assertThrows(AccessDeniedException.class,()->controller.prepare(null));
            assertThrows(AccessDeniedException.class,()->controller.execute(null));assertThrows(AccessDeniedException.class,()->controller.receipt(null));
            assertThrows(AccessDeniedException.class,()->controller.recover(id));assertThrows(AccessDeniedException.class,()->controller.check(null));assertThrows(AccessDeniedException.class,()->controller.checkReceipt(null));
        }
        SecurityContextHolder.clearContext();var missing=assertThrows(IllegalArgumentException.class,()->controller.capabilities());Throwable root=missing;while(root.getCause()!=null)root=root.getCause();assertInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,root);verifyNoInteractions(access);
    }
    @Test void previewIsReadOnlyNoStoreAndContainsOnlyConfirmationFields()throws Exception {
        when(access.preview(id)).thenReturn(new ReviewOrphanCancellationJournal.Preview(id,"OBJECT_ID","0123456789abcdef01234567",2,"v",checkId,id,"b".repeat(64),"a".repeat(64),"c".repeat(64)));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/v1/admin/verification/cancellations/targets/"+id))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.projectIdType").value("OBJECT_ID")).andExpect(jsonPath("$.versionIndex").value(2))
                .andExpect(jsonPath("$.targetSha256").value("a".repeat(64))).andExpect(jsonPath("$.jobId").value(id))
                .andExpect(jsonPath("$.binding").doesNotExist()).andExpect(jsonPath("$.filePath").doesNotExist()).andExpect(jsonPath("$.actor").doesNotExist())
                .andExpect(jsonPath("$.origin").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
        verify(access).preview(id);verifyNoMoreInteractions(access);
    }
    @Test void preparationAndExecutionRemainSeparateHttpActions()throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();when(access.prepare(id)).thenReturn(original);
        mvc.perform(post("/api/v1/admin/verification/cancellations/prepare").contentType("application/json").content("{\"isolationId\":\""+id+"\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.isolationId").value(id)).andExpect(jsonPath("$.token").doesNotExist());
        verify(access).prepare(id);verifyNoMoreInteractions(access);
        when(access.execute(original)).thenReturn(new ReviewOrphanCancellationExecutor.Execution("UNKNOWN",null));
        mvc.perform(post("/api/v1/admin/verification/cancellations/execute").contentType("application/json").content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("UNKNOWN")).andExpect(header().string("Cache-Control","no-store"));verify(access).execute(original);
    }
    @Test void recoveryAndReceiptRoutesNeverDispatchNewChecks()throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();var receipt=new ReviewOrphanCancellationJournal.Receipt(original,"UNKNOWN",null,null);
        when(access.recover(id)).thenReturn(receipt);when(access.receipt(original)).thenReturn(receipt);
        var request=new ReviewCancellationAccess.Check(checkId,original);when(access.checkReceipt(request)).thenReturn(new ReviewCancellationReconciler.Receipt(checkId,original,"READING",null,null));
        mvc.perform(get("/api/v1/admin/verification/cancellations/operations/"+id)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.prepared.expiresAt").value(60001));
        mvc.perform(post("/api/v1/admin/verification/cancellations/receipt").contentType("application/json").content(body())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/v1/admin/verification/cancellations/checks/receipt").contentType("application/json").content("{\"id\":\""+checkId+"\",\"original\":"+body()+"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("READING"));
        verify(access).recover(id);verify(access).receipt(original);verify(access).checkReceipt(request);verifyNoMoreInteractions(access);
    }
    @Test void explicitCheckPreservesItsOwnIdentityAndOriginalIntent()throws Exception {
        var request=new ReviewCancellationAccess.Check(checkId,original);
        when(access.check(request)).thenReturn(new ReviewCancellationReconciler.Receipt(checkId,original,"UNKNOWN",null,null));
        MockMvcBuilders.standaloneSetup(controller).build().perform(post("/api/v1/admin/verification/cancellations/checks").contentType("application/json").content("{\"id\":\""+checkId+"\",\"original\":"+body()+"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.id").value(checkId)).andExpect(jsonPath("$.original.isolationId").value(id));
        verify(access).check(request);verifyNoMoreInteractions(access);
    }
    @Test void errorsAndMalformedBodiesAreEmptyAndNotCacheable()throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        for(var failure:List.of(new SecurityException("private"),new IllegalArgumentException("private"),new IllegalStateException("private"),new com.mongodb.MongoException("private"))) {
            when(access.recover(id)).thenThrow(failure);int code=failure instanceof SecurityException?403:failure instanceof IllegalArgumentException?400:failure instanceof IllegalStateException?409:503;
            mvc.perform(get("/api/v1/admin/verification/cancellations/operations/"+id)).andExpect(status().is(code)).andExpect(content().string("")).andExpect(header().string("Cache-Control","no-store"));reset(access);
        }
        mvc.perform(post("/api/v1/admin/verification/cancellations/execute").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"));verifyNoInteractions(access);
    }
    @Test void capabilityIsAccountGatedAndNoStore()throws Exception {
        when(access.capability()).thenReturn(new ReviewCancellationAccess.Capability("CANCEL_ORIGINAL_REVIEW"));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/v1/admin/verification/cancellations/capabilities"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.action").value("CANCEL_ORIGINAL_REVIEW"));
    }
    @Test void controllerAbsentUnlessCancellationExplicitlyEnabled() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(ReviewCancellationController.class)
                .withPropertyValues("app.warden.repair.enabled=true","app.warden.jobs.enabled=true")
                .run(c->{assertNull(c.getStartupFailure());assertFalse(c.containsBean("reviewCancellationController"));});
    }
    @Test void historyReturnsOnlyReferencesWithoutDispatchAndIsNotCacheable()throws Exception {
        var request=new ReviewCancellationAccess.History(original,null,10);
        when(access.history(request)).thenReturn(new ReviewObservationReader.Page(List.of(new ReviewObservationReader.Item(checkId,"READING")),null,"OBSERVATION_ID"));
        MockMvcBuilders.standaloneSetup(controller).build().perform(post("/api/v1/admin/verification/cancellations/checks/history")
                .contentType("application/json").content("{\"original\":"+body()+",\"cursor\":null,\"limit\":10}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.items[0].id").value(checkId)).andExpect(jsonPath("$.items[0].recordedState").value("READING"))
                .andExpect(jsonPath("$.items[0].observation").doesNotExist()).andExpect(jsonPath("$.order").value("OBSERVATION_ID"));
        verify(access).history(request);verifyNoMoreInteractions(access);
    }
}
