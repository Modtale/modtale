package net.modtale.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.model.dto.request.auth.LauncherAuthExchangeRequest;
import net.modtale.model.dto.request.auth.LauncherAuthIssueRequest;
import net.modtale.model.dto.request.auth.SignInRequest;
import net.modtale.model.user.User;
import net.modtale.service.auth.AuthenticationMutationService;
import net.modtale.service.auth.AuthenticationService;
import net.modtale.service.auth.LauncherAuthService;
import net.modtale.service.auth.TwoFactorService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AuthController controller;
    private AuthenticationService authenticationService;
    private AuthenticationMutationService authenticationMutationService;
    private AccountService accountService;
    private TwoFactorService twoFactorService;
    private LauncherAuthService launcherAuthService;
    private SecurityContextRepository securityContextRepository;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        authenticationMutationService = mock(AuthenticationMutationService.class);
        accountService = mock(AccountService.class);
        twoFactorService = mock(TwoFactorService.class);
        launcherAuthService = mock(LauncherAuthService.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        controller = new AuthController(
                authenticationService,
                authenticationMutationService,
                accountService,
                twoFactorService,
                launcherAuthService,
                securityContextRepository
        );
    }

    @Test
    void loginReturnsAcceptedChallengeWhenMfaIsEnabled() {
        User user = new User();
        user.setId("user-1");
        user.setMfaEnabled(true);

        SignInRequest request = new SignInRequest();
        request.setUsername("ada");
        request.setPassword("secret123");

        when(authenticationService.authenticate("ada", "secret123")).thenReturn(user);
        when(authenticationService.generatePreAuthToken("user-1")).thenReturn("pre-auth-token");

        var response = controller.login(request, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertEquals(202, response.getStatusCode().value());
        assertEquals("mfa_required", response.getBody().status());
        assertEquals("pre-auth-token", response.getBody().preAuthToken());
    }

    @Test
    void logoutClearsTheSecurityContextAndExpiresSessionCookies() {
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        var result = controller.logout(request, response);

        assertEquals(200, result.getStatusCode().value());
        verify(securityContextRepository).saveContext(org.springframework.security.core.context.SecurityContextHolder.createEmptyContext(), request, response);
    }

    @Test
    void issueLauncherAuthCodeReturnsGrantForCurrentUser() {
        User user = new User();
        user.setId("user-1");

        LauncherAuthIssueRequest requestPayload = new LauncherAuthIssueRequest();
        requestPayload.setRedirectUri("http://127.0.0.1:49152/callback");
        requestPayload.setState("state-123");

        when(accountService.requireCurrentUser(null, "authorizing the Modtale Launcher")).thenReturn(user);
        when(launcherAuthService.issueCode(user, "http://127.0.0.1:49152/callback", "state-123"))
                .thenReturn(new LauncherAuthService.LauncherAuthGrant(
                        "launcher-code",
                        "http://127.0.0.1:49152/callback",
                        "state-123",
                        300
                ));

        var response = controller.issueLauncherAuthCode(requestPayload, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("launcher-code", response.getBody().code());
        assertEquals("state-123", response.getBody().state());
        assertEquals(300, response.getBody().expiresIn());
    }

    @Test
    void beginLauncherOAuthStoresCallbackAndRedirectsToProviderAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.beginLauncherOAuthLogin(
                "github",
                "http://127.0.0.1:49152/callback",
                "state-123",
                request,
                response
        );

        assertEquals("/oauth2/authorization/github", response.getRedirectedUrl());
        assertEquals(
                "http://127.0.0.1:49152/callback",
                request.getSession().getAttribute(LauncherAuthService.OAUTH_REDIRECT_URI_SESSION_ATTRIBUTE)
        );
        assertEquals(
                "state-123",
                request.getSession().getAttribute(LauncherAuthService.OAUTH_STATE_SESSION_ATTRIBUTE)
        );
        verify(launcherAuthService).validateLoopbackRedirectUri("http://127.0.0.1:49152/callback");
    }

    @Test
    void exchangeLauncherAuthCodeCreatesSessionForLauncherClient() {
        User user = new User();
        user.setId("user-1");
        user.setRoles(java.util.List.of("USER"));

        LauncherAuthExchangeRequest requestPayload = new LauncherAuthExchangeRequest();
        requestPayload.setCode("launcher-code");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(launcherAuthService.consumeCode("launcher-code")).thenReturn(user);

        var result = controller.exchangeLauncherAuthCode(requestPayload, request, response);

        assertEquals(200, result.getStatusCode().value());
        assertNotNull(request.getSession(false));
        verify(securityContextRepository).saveContext(any(SecurityContext.class), eq(request), eq(response));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

}
