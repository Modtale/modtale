package net.modtale.controller.auth;
import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.security.*;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.user.User;
import net.modtale.service.auth.*;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginSessionRotationTest {
 @Configuration @EnableWebSecurity @EnableWebMvc static class Config {
  @Bean AuthenticationService authentication(){return mock(AuthenticationService.class);}
  @Bean LauncherAuthService launcher(){return mock(LauncherAuthService.class);}
  @Bean TwoFactorService factors(){return mock(TwoFactorService.class);}
  @Bean AccountService accounts(){return mock(AccountService.class);}
  @Bean SecurityContextRepository sessions(){return new HttpSessionSecurityContextRepository();}
  @Bean ClientRegistrationRepository clients(){return mock(ClientRegistrationRepository.class);}
  @Bean AuthController controller(AuthenticationService a,LauncherAuthService l,TwoFactorService f,AccountService u,SecurityContextRepository s){return new AuthController(a,mock(AuthenticationMutationService.class),u,f,l,s,mock(MfaEnrollmentService.class));}
  @Bean SecurityFilterChain chain(HttpSecurity http,AuthenticationService auth,LauncherAuthService launcher,AccountService accounts)throws Exception {
   var keys=mock(ApiKeyService.class);
   var config=new SecurityConfig(new ApiKeyAuthFilter(keys,(req,res,h,e)->{res.setStatus(401);return new org.springframework.web.servlet.ModelAndView();}),new RateLimitFilter(keys),
      mock(OAuth2LoginService.class),mock(OidcLoginService.class),mock(OAuth2AuthorizedClientRepository.class),mock(LocalUserDetailsService.class),mock(PasswordEncoder.class),accounts,auth,launcher,new AppFrontendProperties("http://localhost:3000"));
   return config.securityFilterChain(http,mock(OAuth2AuthorizationRequestResolver.class));
  }
 }
 AnnotationConfigWebApplicationContext context;MockMvc mvc;User user;
 @BeforeEach void setup(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();user=new User();user.setId("fixture-user");user.setUsername("fixture");}
 @AfterEach void cleanup(){context.close();SecurityContextHolder.clearContext();}
 @ParameterizedTest @ValueSource(strings={"signin","mfa/validate-login","launcher/exchange"})
 void successfulManualAuthenticationRotatesAnExistingSession(String route)throws Exception {
  String body;
  if(route.equals("signin")){when(context.getBean(AuthenticationService.class).authenticate("fixture","fixture-password")).thenReturn(user);body="{\"username\":\"fixture\",\"password\":\"fixture-password\"}";}
  else if(route.equals("mfa/validate-login")){user.setMfaEnabled(true);user.setMfaSecret("fixture-factor");when(context.getBean(AuthenticationService.class).validatePreAuthToken("fixture-token")).thenReturn(user);when(context.getBean(TwoFactorService.class).isOtpValid("fixture-factor","123456")).thenReturn(true);body="{\"pre_auth_token\":\"fixture-token\",\"code\":\"123456\"}";}
  else{when(context.getBean(LauncherAuthService.class).consumeCode("fixture-code")).thenReturn(user);body="{\"code\":\"fixture-code\"}";}
  var session=new MockHttpSession();String previous=session.getId();session.setAttribute("fixture-marker","retained");
  var result=mvc.perform(post("/api/v1/auth/"+route).session(session).header("User-Agent","Mozilla/5.0").header("Origin","http://localhost:3000").contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
  var authenticated=result.getRequest().getSession(false);assertNotNull(authenticated);assertNotEquals(previous,authenticated.getId());assertEquals("retained",authenticated.getAttribute("fixture-marker"));
  var security=(SecurityContext)authenticated.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);assertNotNull(security);assertEquals(user,security.getAuthentication().getPrincipal());
 }
 @Test void mfaChallengeDoesNotCreateAnAuthenticatedSession()throws Exception {
  user.setMfaEnabled(true);when(context.getBean(AuthenticationService.class).authenticate("fixture","fixture-password")).thenReturn(user);when(context.getBean(AuthenticationService.class).generatePreAuthToken(user.getId())).thenReturn("fixture-token");
  var session=new MockHttpSession();String previous=session.getId();
  mvc.perform(post("/api/v1/auth/signin").session(session).header("User-Agent","Mozilla/5.0").header("Origin","http://localhost:3000").contentType("application/json").content("{\"username\":\"fixture\",\"password\":\"fixture-password\"}")).andExpect(status().isAccepted());
  assertEquals(previous,session.getId());assertNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
 }
}
