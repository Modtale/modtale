package net.modtale.controller.admin;

import net.modtale.service.admin.review.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ReviewRepairControllerTest.Config.class)
class ReviewRepairControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ReviewRepairAccess access(){return mock(ReviewRepairAccess.class);}
        @Bean ReviewRepairController controller(ReviewRepairAccess access){return new ReviewRepairController(access);}
        @Bean(name="apiSecurity") Permissions permissions(){return new Permissions();}
    }
    public static class Permissions {
        public boolean hasAdminPermission(String permission,Authentication auth){return auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}
    }
    @Autowired ReviewRepairController controller;@Autowired ReviewRepairAccess access;
    @BeforeEach void setup(){reset(access);auth("PROJECT_REVIEW_READ","PROJECT_VERSION_RESCAN");}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    void auth(String...permissions){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("actor",null,Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));}
    @Test void everyRouteRequiresBothPermissionsBeforeServiceAccess() {
        for(String permission:List.of("PROJECT_REVIEW_READ","PROJECT_VERSION_RESCAN","PROJECT_REVIEW_DECIDE","ROLE_USER")) {
            auth(permission);assertThrows(AccessDeniedException.class,()->controller.operations(null,25));assertThrows(AccessDeniedException.class,()->controller.recover("invalid"));assertThrows(AccessDeniedException.class,()->controller.capabilities());assertThrows(AccessDeniedException.class,()->controller.inspect(null));assertThrows(AccessDeniedException.class,()->controller.prepare(null));
            assertThrows(AccessDeniedException.class,()->controller.execute(null));assertThrows(AccessDeniedException.class,()->controller.receipt(null));
        }
        verifyNoInteractions(access);
    }
    @Test void preparedAndReceiptResponsesAreBoundedAndNotCacheable()throws Exception {
        var prepared=new ReviewRepairPreparation.Prepared(UUID.randomUUID().toString(),"a".repeat(64),1,2);
        when(access.prepare(any())).thenReturn(prepared);when(access.receipt(any())).thenReturn(new ReviewRepairAccess.Receipt(new ReviewRepairAccess.Position("STRING","p",0),"v",prepared.sha256(),"UNKNOWN",null));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/v1/admin/verification/repairs/prepare").contentType("application/json").content("{\"id\":\""+prepared.id()+"\",\"position\":{\"projectIdType\":\"STRING\",\"projectId\":\"p\",\"versionIndex\":0},\"versionId\":\"v\",\"expectedSha256\":\""+prepared.sha256()+"\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.id").value(prepared.id())).andExpect(jsonPath("$.actor").doesNotExist());
        mvc.perform(post("/api/v1/admin/verification/repairs/receipt").contentType("application/json").content("{\"id\":\""+prepared.id()+"\",\"sha256\":\""+prepared.sha256()+"\",\"createdAt\":1,\"expiresAt\":2}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("UNKNOWN"));
    }
    @Test void serviceErrorsNeverExposeStoredPayloads()throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        for(var failure:List.of(new SecurityException("private payload"),new IllegalArgumentException("private payload"),new IllegalStateException("private payload"),new com.mongodb.MongoException("private payload"))) {
            when(access.inspect(any())).thenThrow(failure);int expected=failure instanceof SecurityException?403:failure instanceof IllegalArgumentException?400:failure instanceof IllegalStateException?409:503;
            mvc.perform(post("/api/v1/admin/verification/repairs/inspect").contentType("application/json").content("{}"))
                    .andExpect(status().is(expected)).andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(""));reset(access);
        }
    }
    @Test void controllerIsAbsentUnlessFeatureExplicitlyEnabled() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(ReviewRepairController.class)
                .run(context->{assertNull(context.getStartupFailure());assertFalse(context.containsBean("reviewRepairController"));});
    }
    @Test void capabilityUsesAuthenticatedNoStoreContract()throws Exception {
        when(access.capability()).thenReturn(new ReviewRepairAccess.Capability("ISOLATE_LOCAL_REVIEW"));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/repairs/capabilities"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.action").value("ISOLATE_LOCAL_REVIEW"));
    }

    @Test void operationDiscoveryAndRecoveryUseNoStoreHttpResponses()throws Exception {
        String id="11111111-1111-1111-1111-111111111111";var prepared=new ReviewRepairPreparation.Prepared(id,"a".repeat(64),1,2);
        when(access.operations(null,25)).thenReturn(new ReviewRepairOperationReader.Page(List.of(new ReviewRepairOperationReader.Item(id,"UNKNOWN")),null,"OPERATION_ID"));
        when(access.recover(id)).thenReturn(new ReviewRepairAccess.Recovered(prepared,new ReviewRepairAccess.Receipt(new ReviewRepairAccess.Position("STRING","p",0),"v",prepared.sha256(),"UNKNOWN",null)));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/repairs/operations"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/verification/repairs/operations/"+id))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.prepared.id").value(id)).andExpect(jsonPath("$.receipt.beforeSha256").value(prepared.sha256()));
    }

}
