package net.modtale.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.model.dto.request.auth.SignInRequest;
import net.modtale.model.user.User;
import net.modtale.service.auth.AuthenticationMutationService;
import net.modtale.service.auth.AuthenticationService;
import net.modtale.service.auth.TwoFactorService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AuthController controller;
    private AuthenticationService authenticationService;
    private AuthenticationMutationService authenticationMutationService;
    private AccountService accountService;
    private TwoFactorService twoFactorService;
    private SecurityContextRepository securityContextRepository;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        authenticationMutationService = mock(AuthenticationMutationService.class);
        accountService = mock(AccountService.class);
        twoFactorService = mock(TwoFactorService.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        controller = new AuthController(
                authenticationService,
                authenticationMutationService,
                accountService,
                twoFactorService,
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
}
