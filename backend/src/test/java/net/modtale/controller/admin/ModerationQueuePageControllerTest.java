package net.modtale.controller.admin;

import java.util.List;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ModerationQueuePageControllerTest.Config.class)
class ModerationQueuePageControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ModerationQueuePageReader reader(){return mock(ModerationQueuePageReader.class);}
        @Bean ModerationQueuePageController controller(ModerationQueuePageReader reader){return new ModerationQueuePageController(reader);}
        @Bean net.modtale.service.admin.review.ProjectReviewAdminService reviews(){return mock(net.modtale.service.admin.review.ProjectReviewAdminService.class);}
        @Bean net.modtale.service.user.account.AccountService accounts(){return mock(net.modtale.service.user.account.AccountService.class);}
        @Bean net.modtale.service.admin.project.ProjectAdminOperationsService operations(){return mock(net.modtale.service.admin.project.ProjectAdminOperationsService.class);}
        @Bean ProjectManagementController legacy(net.modtale.service.user.account.AccountService accounts,net.modtale.service.admin.review.ProjectReviewAdminService reviews,net.modtale.service.admin.project.ProjectAdminOperationsService operations){return new ProjectManagementController(accounts,reviews,operations);}
        @Bean(name="apiSecurity") Permissions permissions(){return new Permissions();}
    }
    public static class Permissions {
        public boolean hasAdminPermission(String permission,Authentication auth){return auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}
    }
    @Autowired ModerationQueuePageController controller;
    @Autowired ModerationQueuePageReader reader;
    @Autowired ProjectManagementController legacy;
    @Autowired net.modtale.service.admin.review.ProjectReviewAdminService reviews;
    @Autowired net.modtale.service.user.account.AccountService accounts;
    @Autowired net.modtale.service.admin.project.ProjectAdminOperationsService operations;
    @BeforeEach void setup(){reset(reader);auth("PROJECT_REVIEW_READ");}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    void auth(String permission){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority(permission))));}
    @Test void permissionIsCheckedBeforeCursorOrDatabaseAccess() {
        auth("PROJECT_REVIEW_DECIDE");assertThrows(AccessDeniedException.class,()->controller.read("bad",25));verifyNoInteractions(reader);
    }
    @Test void httpUsesDefaultPageSizeAndReturnsContinuationEvenWhenAllRowsAreUnavailable()throws Exception {
        var next=new ModerationQueuePageReader.Cursor("project",7);
        when(reader.page(null,25)).thenReturn(new ModerationQueuePageReader.Page(List.of(),next,25));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/admin/verification/queue/page")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.nextCursor").value(ModerationQueueCursor.encode(next))).andExpect(jsonPath("$.unavailableItems").value(25))
                .andExpect(jsonPath("$.order").value("PROJECT_VERSION")).andExpect(jsonPath("$.items").isEmpty());
        verify(reader).page(null,25);
    }
    @Test void invalidHttpParametersNeverReachTheDatabase()throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        for(String limit:List.of("0","51","2147483648","no"))mvc.perform(get("/api/v1/admin/verification/queue/page").param("limit",limit)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/verification/queue/page").param("cursor","invalid")).andExpect(status().isBadRequest());verifyNoInteractions(reader);
    }
    @Test void retiredEndpointReturnsExplicitMigrationWithoutReadingServices()throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(legacy).build();
        mvc.perform(get("/api/v1/admin/verification/queue")).andExpect(status().isGone())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Link","</api/v1/admin/verification/queue/page>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.status").value(410));
        verifyNoInteractions(reader,reviews,accounts,operations);
    }
    @Test void retiredEndpointStillRequiresReviewReadPermission() {
        auth("PROJECT_REVIEW_DECIDE");assertThrows(AccessDeniedException.class,()->legacy.getVerificationQueue());
        verifyNoInteractions(reader,reviews,accounts,operations);
    }
    @Test void exactTypedCursorIsPassedToReader() {
        var cursor=new ModerationQueuePageReader.Cursor(new org.bson.types.ObjectId(),8);
        when(reader.page(cursor,1)).thenReturn(new ModerationQueuePageReader.Page(List.of(),null,0));
        var result=controller.read(ModerationQueueCursor.encode(cursor),1);assertNull(result.getBody().nextCursor());verify(reader).page(cursor,1);
    }
}
