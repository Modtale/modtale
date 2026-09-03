package net.modtale.config.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;

/**
 * Hytale requires S256 PKCE even for confidential clients. Spring Security only
 * enables PKCE automatically for public clients, so apply it explicitly to this
 * registration while leaving the other providers' requests unchanged.
 */
public class HytaleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String HYTALE_REGISTRATION_ID = "hytale";

    private final OAuth2AuthorizationRequestResolver delegate;

    public HytaleAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                registrations,
                "/oauth2/authorization"
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return addHytalePkce(delegate.resolve(request), registrationIdFrom(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return addHytalePkce(delegate.resolve(request, clientRegistrationId), clientRegistrationId);
    }

    private OAuth2AuthorizationRequest addHytalePkce(
            OAuth2AuthorizationRequest authorizationRequest,
            String registrationId
    ) {
        if (authorizationRequest == null || !HYTALE_REGISTRATION_ID.equals(registrationId)) {
            return authorizationRequest;
        }

        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(authorizationRequest);
        OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);
        return builder.build();
    }

    private String registrationIdFrom(HttpServletRequest request) {
        String path = request.getRequestURI();
        int separator = path.lastIndexOf('/');
        return separator >= 0 ? path.substring(separator + 1) : path;
    }
}
