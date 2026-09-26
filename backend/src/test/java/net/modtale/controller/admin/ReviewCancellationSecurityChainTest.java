package net.modtale.controller.admin;

import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.security.*;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.user.*;
import net.modtale.service.auth.*;
import net.modtale.service.admin.review.*;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewCancellationSecurityChainTest {
    @Configuration @EnableWebSecurity @EnableMethodSecurity @EnableWebMvc
    static class Config {
        @Bean AccountService accounts(){return mock(AccountService.class);}
        @Bean ApiKeyService keys(){return mock(ApiKeyService.class);}
        @Bean ClientRegistrationRepository clients(){return mock(ClientRegistrationRepository.class);}
        @Bean ReviewOrphanCancellationJournal journal(){return mock(ReviewOrphanCancellationJournal.class);}
        @Bean ReviewOrphanCancellationExecutor executor(){return mock(ReviewOrphanCancellationExecutor.class);}
        @Bean ReviewCancellationReconciler reconciler(){return mock(ReviewCancellationReconciler.class);}
        @Bean ReviewRepairWorkflow workflow(){return new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),1);}
        @Bean ReviewCancellationAccess access(AccountService a,ReviewRepairWorkflow w,ReviewOrphanCancellationJournal j,ReviewOrphanCancellationExecutor e,ReviewCancellationReconciler r){return new ReviewCancellationAccess(a,w,j,e,r);}
        @Bean ReviewCancellationController controller(ReviewCancellationAccess a){return new ReviewCancellationController(a);}
        @Bean(name="apiSecurity") AccessControlService permissions(AccountService a){return new AccessControlService(a,null,null,null);}
        @Bean SecurityFilterChain chain(HttpSecurity http,ApiKeyService keys,AccountService accounts)throws Exception {
            var api=new ApiKeyAuthFilter(keys,(request,response,handler,failure)->{response.setStatus(401);return new org.springframework.web.servlet.ModelAndView();});
            var configuration=new SecurityConfig(api,new RateLimitFilter(keys),mock(OAuth2LoginService.class),mock(OidcLoginService.class),
                    mock(OAuth2AuthorizedClientRepository.class),mock(LocalUserDetailsService.class),mock(PasswordEncoder.class),accounts,
                    mock(AuthenticationService.class),mock(LauncherAuthService.class),new AppFrontendProperties("http://localhost:3000"));
            return configuration.securityFilterChain(http,mock(OAuth2AuthorizationRequestResolver.class));
        }
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;ReviewOrphanCancellationJournal journal;ApiKeyService keys;
    AtomicReference<User> current=new AtomicReference<>();MockHttpSession session;
    String prefix="/api/v1/admin/verification/cancellations",id="11111111-1111-1111-1111-111111111111";
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
        journal=context.getBean(ReviewOrphanCancellationJournal.class);keys=context.getBean(ApiKeyService.class);
        var actor=new User();actor.setId("actor");actor.setAdminPermissions(Set.of(AdminPermission.PROJECT_REVIEW_READ,AdminPermission.PROJECT_VERSION_RESCAN));current.set(actor);
        when(context.getBean(AccountService.class).getCurrentUser(any())).thenAnswer(i->{var auth=i.<org.springframework.security.core.Authentication>getArgument(0);return auth!=null && auth.getPrincipal() instanceof User?current.get():null;});
        session=new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,new SecurityContextImpl(new UsernamePasswordAuthenticationToken(actor,null,List.of())));
        when(journal.prepare(eq(id),eq("actor"),any())).thenReturn(new ReviewOrphanCancellationJournal.Prepared(id,"a".repeat(64),1,60001));
    }
    @AfterEach void cleanup(){context.close();SecurityContextHolder.clearContext();}
    MockHttpServletRequestBuilder prepare(){return post(prefix+"/prepare").session(session).header("User-Agent","Mozilla/5.0").contentType("application/json").content("{\"isolationId\":\""+id+"\"}");}
    MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder r){return r.cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN","fixture-csrf")).header("X-XSRF-TOKEN","fixture-csrf");}
    @Test void sessionMutationRequiresMatchingCsrfBeforeJournalAccess()throws Exception {
        mvc.perform(prepare()).andExpect(status().isForbidden());verifyNoInteractions(journal);
        mvc.perform(prepare().cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN","one")).header("X-XSRF-TOKEN","other")).andExpect(status().isForbidden());verifyNoInteractions(journal);
        mvc.perform(csrf(prepare())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(journal).prepare(eq(id),eq("actor"),any());
    }
    @Test void anonymousAndRevokedSessionsCannotReachTheJournal()throws Exception {
        mvc.perform(get(prefix+"/capabilities").header("User-Agent","Mozilla/5.0").header("Origin","http://localhost:3000")).andExpect(status().isUnauthorized());
        current.get().setAdminPermissions(Set.of(AdminPermission.PROJECT_REVIEW_READ));mvc.perform(csrf(prepare())).andExpect(status().isForbidden());
        current.set(null);mvc.perform(csrf(prepare())).andExpect(status().isForbidden());verifyNoInteractions(journal);
    }
    @Test void suppliedInvalidApiKeyCannotFallBackToModeratorSession()throws Exception {
        mvc.perform(csrf(prepare()).header("X-MODTALE-KEY","invalid-fixture-key")).andExpect(status().isUnauthorized());verifyNoInteractions(journal);
    }
    @Test void validApiKeyCannotUseBrowserOnlyModerationRoutes()throws Exception {
        var key=new ApiKey();key.setId("fixture-key-id");when(keys.resolveKey("valid-fixture-key")).thenReturn(key);when(keys.getUserFromKey(key)).thenReturn(current.get());
        mvc.perform(prepare().header("X-MODTALE-KEY","valid-fixture-key")).andExpect(status().isForbidden());verifyNoInteractions(journal);
    }
    @Test void foreignOriginCannotUseValidSessionAndCsrf()throws Exception {
        mvc.perform(csrf(prepare()).header("Origin","https://untrusted.example")).andExpect(status().isForbidden());verifyNoInteractions(journal);
    }
    @Test void everyCancellationPostRequiresBrowserCsrf()throws Exception {
        for(String route:List.of("prepare","execute","receipt","checks","checks/receipt","checks/history")) {
            mvc.perform(post(prefix+"/"+route).session(session).header("User-Agent","Mozilla/5.0").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(journal,context.getBean(ReviewOrphanCancellationExecutor.class),context.getBean(ReviewCancellationReconciler.class));
    }
    @Test void anotherAccountCannotSupplyTheOriginalActorsAuthority()throws Exception {
        var other=new User();other.setId("other");other.setAdminPermissions(current.get().getAdminPermissions());current.set(other);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,new SecurityContextImpl(new UsernamePasswordAuthenticationToken(other,null,List.of())));
        when(journal.prepare(eq(id),eq("other"),any())).thenThrow(new SecurityException("Original actor required"));
        mvc.perform(csrf(prepare())).andExpect(status().isForbidden());
        verify(journal).prepare(eq(id),eq("other"),any());verify(journal,never()).prepare(eq(id),eq("actor"),any());
    }
}
