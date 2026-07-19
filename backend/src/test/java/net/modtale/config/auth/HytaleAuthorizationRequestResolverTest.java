package net.modtale.config.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

class HytaleAuthorizationRequestResolverTest {

    @Test
    void addsS256PkceToConfidentialHytaleAuthorizationRequest() {
        HytaleAuthorizationRequestResolver resolver = resolver();

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(requestFor("hytale"));

        assertNotNull(authorizationRequest);
        assertNotNull(authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER));
        assertEquals(
                "S256",
                authorizationRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE_METHOD)
        );
        assertNotNull(authorizationRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE));
    }

    private static HytaleAuthorizationRequestResolver resolver() {
        return new HytaleAuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(registration("hytale"), registration("other"))
        );
    }

    private static ClientRegistration registration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(registrationId + "-client")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://issuer.example/oauth2/auth")
                .tokenUri("https://issuer.example/oauth2/token")
                .jwkSetUri("https://issuer.example/jwks")
                .userInfoUri("https://issuer.example/userinfo")
                .userNameAttributeName("sub")
                .clientName(registrationId)
                .build();
    }

    private static MockHttpServletRequest requestFor(String registrationId) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/oauth2/authorization/" + registrationId
        );
        request.setServletPath("/oauth2/authorization/" + registrationId);
        request.setScheme("https");
        request.setServerName("api.modtale.net");
        request.setServerPort(443);
        return request;
    }
}
