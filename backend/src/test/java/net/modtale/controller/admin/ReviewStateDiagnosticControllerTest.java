package net.modtale.controller.admin;

import net.modtale.service.admin.review.*;
import net.modtale.service.security.scan.RemoteReviewDiscovery;
import org.bson.types.ObjectId;
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
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ReviewStateDiagnosticControllerTest.Config.class)
class ReviewStateDiagnosticControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ReviewStateDiagnosticReader reader(){return mock(ReviewStateDiagnosticReader.class);}
        @Bean ReviewStateDiagnosticController controller(ReviewStateDiagnosticReader reader){return new ReviewStateDiagnosticController(reader);}
        @Bean(name="apiSecurity") Permissions permissions(){return new Permissions();}
    }
    public static class Permissions {
        public boolean hasAdminPermission(String permission,Authentication auth){return auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}
    }
    @Autowired ReviewStateDiagnosticController controller;
    @Autowired ReviewStateDiagnosticReader reader;
    void auth(String permission){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority(permission))));}
    @BeforeEach void setup(){reset(reader);auth("PROJECT_REVIEW_READ");}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void permissionPrecedesEvenInvalidCursorAndDatabaseAccess() {
        for(String permission:List.of("PROJECT_REVIEW_DECIDE","ROLE_USER","ROLE_ANONYMOUS")) {
            auth(permission);assertThrows(AccessDeniedException.class,()->controller.read("bad",0));
        }
        verifyNoInteractions(reader);
    }
    @Test void emptyDiagnosticPageRetainsPhysicalContinuation()throws Exception {
        var next=new RemoteReviewDiscovery.Cursor("a",25,false);
        when(reader.page(null,25)).thenReturn(new ReviewStateDiagnosticReader.Page(List.of(),next,25));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/admin/verification/diagnostics/page")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.nextCursor").value(ReviewStateDiagnosticCursor.encode(next)))
                .andExpect(jsonPath("$.examinedSlots").value(25)).andExpect(jsonPath("$.scope").value("PENDING_SCAN_STRUCTURE"));
    }
    @Test void invalidHttpParametersNeverReachDatabase()throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        for(String limit:List.of("0","65","no","2147483648"))mvc.perform(get("/api/v1/admin/verification/diagnostics/page").param("limit",limit)).andExpect(status().isBadRequest());
        for(String cursor:List.of("bad","1.s.0.YQ","d1.a.1.s.1.YQ"))mvc.perform(get("/api/v1/admin/verification/diagnostics/page").param("cursor",cursor)).andExpect(status().isBadRequest());
        verifyNoInteractions(reader);
    }
    @Test void responsePreservesAmbiguousBsonIdentityWithoutFlattening()throws Exception {
        String hex="abcdefabcdefabcdefabcdef";var reasons=List.of(ReviewStateDiagnosticReader.Reason.DUPLICATE_VERSION_ID);
        var cursor=new RemoteReviewDiscovery.Cursor(hex,1,false);
        when(reader.page(cursor,2)).thenReturn(new ReviewStateDiagnosticReader.Page(List.of(
                new ReviewStateDiagnosticReader.Item(hex,1,null,reasons),new ReviewStateDiagnosticReader.Item(new ObjectId(hex),0,"v",reasons)),null,2));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/admin/verification/diagnostics/page").param("limit","2").param("cursor",ReviewStateDiagnosticCursor.encode(cursor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].position.projectIdType").value("STRING"))
                .andExpect(jsonPath("$.items[1].position.projectIdType").value("OBJECT_ID"))
                .andExpect(jsonPath("$.items[0].position.projectId").value(hex)).andExpect(jsonPath("$.items[1].position.projectId").value(hex))
                .andExpect(jsonPath("$.items[0].position.versionIndex").value(1)).andExpect(jsonPath("$.items[0].versionId").isEmpty())
                .andExpect(jsonPath("$.items[0].reasons[0]").value("DUPLICATE_VERSION_ID")).andExpect(jsonPath("$.nextCursor").isEmpty());
        verify(reader).page(cursor,2);
    }
}
