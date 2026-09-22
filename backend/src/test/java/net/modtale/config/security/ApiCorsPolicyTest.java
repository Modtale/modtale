package net.modtale.config.security;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.junit.jupiter.api.Assertions.*;

class ApiCorsPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://modtale.net", "https://www.modtale.net", "https://preview-123.run.app"})
    void trustedFrontendsCanUseSessionCredentials(String origin) throws Exception {
        var response = process(origin, "/api/v1/user/me", "GET", null);
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://attacker.example", "https://other-preview.run.app", "null"})
    void arbitraryOriginsCannotReadSessionAuthenticatedResponses(String origin) throws Exception {
        var response = process(origin, "/api/v1/user/me", "GET", null);
        assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void thirdPartyApiKeyPreflightsRemainSupported() throws Exception {
        var response = process("https://third-party.example", "/api/v1/projects", "OPTIONS", "X-Modtale-Key, Content-Type");
        assertEquals(200, response.getStatus());
        assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("X-Modtale-Key"));
    }

    @Test
    void untrustedOriginsCannotSendCsrfHeaders() throws Exception {
        var response = process("https://attacker.example", "/api/v1/projects", "OPTIONS", "X-XSRF-TOKEN");
        assertEquals(403, response.getStatus());
    }

    @Test
    void onlyConfiguredPreviewCanAccessRestrictedEndpoints() throws Exception {
        assertEquals(403, process("https://other-preview.run.app", "/api/v1/admin/users", "GET", null).getStatus());
        var response = process("https://preview-123.run.app", "/api/v1/admin/users", "GET", null);
        assertEquals("https://preview-123.run.app", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    }

    private MockHttpServletResponse process(String origin, String path, String method, String headers) throws Exception {
        var source = ApiCorsPolicy.create(Set.of("https://modtale.net", "https://*.modtale.net", "https://preview-123.run.app"));
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("Origin", origin);
        if (headers != null) {
            request.addHeader("Access-Control-Request-Method", "POST");
            request.addHeader("Access-Control-Request-Headers", headers);
        }
        var response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}
