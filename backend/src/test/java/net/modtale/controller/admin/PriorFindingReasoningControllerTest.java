package net.modtale.controller.admin;

import java.util.List;
import net.modtale.service.security.issue.PriorFindingReasoningService;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(PriorFindingReasoningControllerTest.Config.class)
class PriorFindingReasoningControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean PriorFindingReasoningService service(){return mock(PriorFindingReasoningService.class);}
        @Bean PriorFindingReasoningController controller(PriorFindingReasoningService service){return new PriorFindingReasoningController(service);}
        @Bean(name="apiSecurity") Permissions permissions(){return new Permissions();}
    }
    public static class Permissions {
        public boolean hasAdminPermission(String permission,Authentication auth){return auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}
    }
    @Autowired PriorFindingReasoningController controller;
    @Autowired PriorFindingReasoningService service;
    @BeforeEach void resetMock(){reset(service);}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void permissionRequiredBeforeHistoryAccess() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user",null,List.of(new SimpleGrantedAuthority("PROJECT_REVIEW_DECIDE"))));
        assertThrows(AccessDeniedException.class,()->controller.read("project","target","source",0,"snapshot"));verifyNoInteractions(service);
    }
    @Test void httpBindsSelectedSourceFindingAndProjectTokenAndNeverCaches() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority("PROJECT_REVIEW_READ"))));
        when(service.read("project","target","source",3,"snapshot")).thenReturn(new PriorFindingReasoningService.Reasoning("snapshot","source","1",1,List.of(),List.of(),0));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        String path="/api/v1/admin/projects/project/versions/target/prior-finding-reasoning";
        mvc.perform(get(path).param("sourceVersionId","source").param("issueIndex","3")).andExpect(status().isBadRequest());verifyNoInteractions(service);
        mvc.perform(get(path).param("sourceVersionId","source").param("issueIndex","3").header("If-Match","snapshot"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.reviewToken").value("snapshot"));
        verify(service).read("project","target","source",3,"snapshot");
    }
}
