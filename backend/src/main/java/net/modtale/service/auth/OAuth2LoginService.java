package net.modtale.service.auth;

import net.modtale.exception.AuthenticationOperationException;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.OAuthAccountCollisionException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.User;
import net.modtale.service.user.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OAuth2LoginService extends DefaultOAuth2UserService {

    private final AccountService accountService;
    private final AuthenticationService authenticationService;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public OAuth2LoginService(
            AccountService accountService,
            AuthenticationService authenticationService,
            ObjectProvider<HttpServletRequest> requestProvider
    ) {
        this.accountService = accountService;
        this.authenticationService = authenticationService;
        this.requestProvider = requestProvider;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String provider = userRequest.getClientRegistration().getRegistrationId();
        OAuth2User oauthUser = fetchOAuthUser(userRequest);
        String accessToken = userRequest.getAccessToken().getTokenValue();

        try {
            Authentication currentAuth = currentAuthentication();

            if (currentAuth != null && currentAuth.isAuthenticated() &&
                    !currentAuth.getName().equals("anonymousUser")) {

                User currentUser = accountService.getCurrentUser();

                if (currentUser != null) {
                    return authenticationService.linkAccount(currentUser, provider, oauthUser, accessToken);
                }
            }

            return authenticationService.processUserLogin(provider, oauthUser, accessToken);
        } catch (OAuthAccountCollisionException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_collision"), ex.getMessage());
        } catch (InvalidAuthenticationRequestException | ForbiddenOperationException | UnauthorizedException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("login_failure"), ex.getMessage());
        } catch (AuthenticationOperationException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("login_failure"), ex.getMessage());
        }
    }

    private Authentication currentAuthentication() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        return request != null && request.getUserPrincipal() instanceof Authentication auth ? auth : null;
    }

    protected OAuth2User fetchOAuthUser(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}
