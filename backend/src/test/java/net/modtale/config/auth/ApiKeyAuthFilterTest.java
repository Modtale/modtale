package net.modtale.config.auth;

import jakarta.servlet.FilterChain;
import java.util.EnumSet;
import java.util.Map;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.auth.ApiKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyAuthFilterTest {

    private ApiKeyAuthFilter filter;
    private ApiKeyService apiKeyService;
    private HandlerExceptionResolver exceptionResolver;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        exceptionResolver = mock(HandlerExceptionResolver.class);
        filter = new ApiKeyAuthFilter(apiKeyService, exceptionResolver);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresRequestsOutsideApiNamespace() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-MODTALE-KEY", "test-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verifyNoInteractions(apiKeyService);
        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void ignoresBlankApiKeyHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader("X-MODTALE-KEY", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verifyNoInteractions(apiKeyService);
        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsInvalidApiKeysThroughTheSharedExceptionResolver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader("X-MODTALE-KEY", "bad-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(apiKeyService.resolveKey("bad-key")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(), any(UnauthorizedException.class));
    }

    @Test
    void populatesSecurityContextWithApiRoleAndScopedPermissions() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader("X-MODTALE-KEY", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        ApiKey apiKey = new ApiKey();
        apiKey.setContextPermissions(Map.of(
                "project-1",
                EnumSet.of(ApiKey.ApiPermission.PROJECT_READ, ApiKey.ApiPermission.VERSION_DOWNLOAD)
        ));

        User user = new User();
        user.setId("user-1");
        user.setUsername("ada");

        when(apiKeyService.resolveKey("valid-key")).thenReturn(apiKey);
        when(apiKeyService.getUserFromKey(apiKey)).thenReturn(user);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(user, auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_project-1_PROJECT_READ")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_project-1_VERSION_DOWNLOAD")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingContextPermissionsOnlyGrantApiRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader("X-MODTALE-KEY", "unscoped-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        ApiKey apiKey = new ApiKey();
        User user = new User();
        user.setId("user-unscoped");

        when(apiKeyService.resolveKey("unscoped-key")).thenReturn(apiKey);
        when(apiKeyService.getUserFromKey(apiKey)).thenReturn(user);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API")));
        assertEquals(1, auth.getAuthorities().size());
        verify(chain).doFilter(request, response);
    }

    @Test
    void explicitEmptyContextPermissionsOnlyGrantApiRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader("X-MODTALE-KEY", "empty-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        ApiKey apiKey = new ApiKey();
        apiKey.setContextPermissions(Map.of());
        User user = new User();
        user.setId("user-empty");

        when(apiKeyService.resolveKey("empty-key")).thenReturn(apiKey);
        when(apiKeyService.getUserFromKey(apiKey)).thenReturn(user);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API")));
        assertEquals(1, auth.getAuthorities().size());
        verify(chain).doFilter(request, response);
    }
}
