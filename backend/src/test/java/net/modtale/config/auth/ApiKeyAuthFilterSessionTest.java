package net.modtale.config.auth;

import jakarta.servlet.FilterChain;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.auth.ApiKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterSessionTest {
    private final ApiKeyService service = mock(ApiKeyService.class);
    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(service, resolver);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private SecurityContext sessionContext;

    @BeforeEach
    void setUp() {
        sessionContext = SecurityContextHolder.createEmptyContext();
        sessionContext.setAuthentication(new UsernamePasswordAuthenticationToken("session-user", null, List.of()));
        SecurityContextHolder.setContext(sessionContext);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid"})
    void rejectsInvalidCredentialsInsteadOfUsingSession(String key) throws Exception {
        request.addHeader("X-MODTALE-KEY", key);
        filter.doFilter(request, response, chain);
        verifyNoInteractions(chain);
        verify(resolver).resolveException(eq(request), eq(response), isNull(), isA(UnauthorizedException.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("session-user", sessionContext.getAuthentication().getPrincipal());
    }

    @Test
    void rejectsKeyWhoseOwnerNoLongerExists() throws Exception {
        request.addHeader("X-MODTALE-KEY", "orphan");
        when(service.resolveKey("orphan")).thenReturn(new ApiKey());
        filter.doFilter(request, response, chain);
        verifyNoInteractions(chain);
        verify(resolver).resolveException(eq(request), eq(response), isNull(), isA(UnauthorizedException.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authenticatesKeyWithoutMutatingSharedSessionContext() throws Exception {
        ApiKey key = new ApiKey();
        User user = new User();
        request.addHeader("X-MODTALE-KEY", "valid");
        when(service.resolveKey("valid")).thenReturn(key);
        when(service.getUserFromKey(key)).thenReturn(user);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertSame(user, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API")));
        assertEquals("session-user", sessionContext.getAuthentication().getPrincipal());
        verifyNoInteractions(resolver);
    }

    @Test
    void preservesSessionWhenNoCredentialIsSupplied() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertSame(sessionContext, SecurityContextHolder.getContext());
        verifyNoInteractions(service, resolver);
    }

    @Test
    void ignoresPathsOutsideApiVersionBoundary() throws Exception {
        request.setRequestURI("/api/v10/projects");
        request.addHeader("X-MODTALE-KEY", "invalid");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(service, resolver);
    }
}
