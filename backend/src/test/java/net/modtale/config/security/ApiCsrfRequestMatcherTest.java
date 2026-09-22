package net.modtale.config.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import static org.junit.jupiter.api.Assertions.*;

class ApiCsrfRequestMatcherTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/change-password", "/api/v1/auth/credentials", "/api/v1/auth/password",
            "/api/v1/auth/mfa/verify", "/api/v1/auth/launcher/issue", "/api/v1/auth/logout",
            "/api/v1/user/api-keys", "/api/v1/projects/example"
    })
    void sessionMutationsRequireTokenEvenWithEmptyKey(String path) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.addHeader("X-MODTALE-KEY", "");
        var response = new MockHttpServletResponse();
        var filter = new CsrfFilter(new HttpSessionCsrfTokenRepository());
        filter.setRequireCsrfProtectionMatcher(new ApiCsrfRequestMatcher());
        filter.doFilter(request, response, (req, res) -> fail("Request without CSRF token reached application"));
        assertEquals(403, response.getStatus());
    }

    @Test
    void validCsrfTokenAllowsSessionMutation() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/change-password");
        var response = new MockHttpServletResponse();
        var repository = new HttpSessionCsrfTokenRepository();
        var token = repository.generateToken(request);
        repository.saveToken(token, request, response);
        request.addHeader(token.getHeaderName(), token.getToken());
        var filter = new CsrfFilter(repository);
        filter.setRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler());
        filter.setRequireCsrfProtectionMatcher(new ApiCsrfRequestMatcher());
        boolean[] reachedApplication = {false};
        filter.doFilter(request, response, (req, res) -> reachedApplication[0] = true);
        assertTrue(reachedApplication[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/signin", "/api/v1/auth/launcher/exchange", "/api/v1/users/batch", "/api/v1/projects/external/identify"})
    void preservesPublicPostOperations(String path) {
        assertFalse(new ApiCsrfRequestMatcher().matches(new MockHttpServletRequest("POST", path)));
        assertTrue(new ApiCsrfRequestMatcher().matches(new MockHttpServletRequest("DELETE", path)));
    }

    @Test
    void apiKeyExemptionIsConfinedToApiNamespace() {
        var request = new MockHttpServletRequest("POST", "/api/v1/projects");
        request.addHeader("X-MODTALE-KEY", "key");
        assertFalse(new ApiCsrfRequestMatcher().matches(request));
        request.setRequestURI("/logout");
        assertTrue(new ApiCsrfRequestMatcher().matches(request));
    }
}
