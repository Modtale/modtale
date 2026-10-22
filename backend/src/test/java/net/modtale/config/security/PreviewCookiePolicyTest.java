package net.modtale.config.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.service.auth.AuthenticationService;
import net.modtale.service.auth.LauncherAuthService;
import net.modtale.service.auth.LocalUserDetailsService;
import net.modtale.service.auth.OAuth2LoginService;
import net.modtale.service.auth.OidcLoginService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.session.web.http.CookieSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PreviewCookiePolicyTest {
    @ParameterizedTest
    @CsvSource({
            "https://preview-example.run.app, true, true, None, ''",
            "https://PREVIEW-EXAMPLE.RUN.APP/, true, true, None, ''",
            "https://dev.modtale.net, true, false, None, ''",
            "https://modtale.net, true, false, Lax, modtale.net",
            "https://www.modtale.net/, true, false, Lax, modtale.net",
            "http://localhost:3000, false, false, Lax, ''",
            "http://127.0.0.1:3000, false, false, Lax, ''",
            "http://[::1]:3000, false, false, Lax, ''",
            "https://preview.run.app.attacker.test, true, false, Lax, attacker.test"
    })
    void sessionAndCsrfCookieCreationAndExpiryKeepTheirEnvironmentPolicy(
            String frontendUrl, boolean secure, boolean partitioned, String sameSite, String domain) {
        var config = configuration(frontendUrl);
        // Exercise the raw-header path as well as the Servlet 6 path covered by
        // PreviewSessionFlowTest. Mock responses omit SameSite for plain Cookies.
        var servletContext = new MockServletContext();
        servletContext.setMajorVersion(5);
        var request = new MockHttpServletRequest(servletContext);
        var sessionResponse = new MockHttpServletResponse();
        var serializer = config.cookieSerializer();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(request, sessionResponse, "fixture-session"));
        assertPolicy(sessionResponse.getHeader("Set-Cookie"), "SESSION", secure, true, partitioned, sameSite, domain, false);

        var expiredSessionResponse = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(request, expiredSessionResponse, ""));
        assertPolicy(expiredSessionResponse.getHeader("Set-Cookie"), "SESSION", secure, true, partitioned, sameSite, domain, true);

        var repository = config.csrfTokenRepository();
        var token = repository.generateToken(request);
        var csrfResponse = new MockHttpServletResponse();
        repository.saveToken(token, request, csrfResponse);
        assertPolicy(csrfResponse.getHeader("Set-Cookie"), "XSRF-TOKEN", secure, false, partitioned, sameSite, domain, false);

        var expiredCsrfResponse = new MockHttpServletResponse();
        repository.saveToken(null, request, expiredCsrfResponse);
        assertPolicy(expiredCsrfResponse.getHeader("Set-Cookie"), "XSRF-TOKEN", secure, false, partitioned, sameSite, domain, true);
    }

    private static void assertPolicy(String header, String name, boolean secure, boolean httpOnly,
            boolean partitioned, String sameSite, String domain, boolean expired) {
        assertNotNull(header);
        assertTrue(header.startsWith(name + "="), header);
        Set<String> attributes = Arrays.stream(header.split(";")).skip(1).map(String::trim).collect(Collectors.toSet());
        assertTrue(attributes.contains("Path=/"), header);
        assertEquals(secure, attributes.contains("Secure"), header);
        assertEquals(httpOnly, attributes.contains("HttpOnly"), header);
        assertEquals(partitioned, attributes.contains("Partitioned"), header);
        assertTrue(attributes.contains("SameSite=" + sameSite), header);
        assertEquals(domain.isEmpty() ? Set.of() : Set.of("Domain=" + domain),
                attributes.stream().filter(attribute -> attribute.startsWith("Domain=")).collect(Collectors.toSet()), header);
        assertEquals(expired, attributes.contains("Max-Age=0"), header);
    }

    private static SecurityConfig configuration(String frontendUrl) {
        return new SecurityConfig(
                mock(ApiKeyAuthFilter.class), mock(RateLimitFilter.class),
                mock(OAuth2LoginService.class), mock(OidcLoginService.class),
                mock(OAuth2AuthorizedClientRepository.class), mock(LocalUserDetailsService.class),
                mock(PasswordEncoder.class), mock(AccountService.class),
                mock(AuthenticationService.class), mock(LauncherAuthService.class),
                new AppFrontendProperties(frontendUrl));
    }
}
