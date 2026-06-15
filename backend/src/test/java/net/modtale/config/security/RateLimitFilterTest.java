package net.modtale.config.security;

import jakarta.servlet.FilterChain;
import net.modtale.service.auth.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RateLimitFilterTest {

    private ApiKeyService apiKeyService;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        filter = new RateLimitFilter(apiKeyService);
    }

    @Test
    void allowsAutomatedUserAgentsOnPublicDownloadUrlRoutesWithoutApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/projects/5e9bbea3-0d7f-4365-93df-5e7acfadf0e7/versions/1.1.1/download-url"
        );
        request.addHeader("User-Agent", "curl/8.9.1");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verifyNoInteractions(apiKeyService);
        verify(chain).doFilter(request, response);
        assertEquals("Public-IP", response.getHeader("X-RateLimit-Tier"));
    }

    @Test
    void stillRequiresApiKeysForAutomatedUserAgentsOnAuthOnlyApiRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user/me");
        request.addHeader("User-Agent", "curl/8.9.1");
        request.setRemoteAddr("203.0.113.11");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verifyNoInteractions(apiKeyService);
        verify(chain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Automated access requires an API Key."));
    }
}
