package net.modtale.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import net.modtale.exception.AuthenticationOperationException;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.OAuthAccountCollisionException;
import net.modtale.exception.OrganizationNotFoundException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.User;
import net.modtale.service.user.account.AccountService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OidcLoginService extends OidcUserService {

    private final AccountService accountService;
    private final AuthenticationService authenticationService;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public OidcLoginService(
            AccountService accountService,
            AuthenticationService authenticationService,
            ObjectProvider<HttpServletRequest> requestProvider
    ) {
        this.accountService = accountService;
        this.authenticationService = authenticationService;
        this.requestProvider = requestProvider;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        String provider = userRequest.getClientRegistration().getRegistrationId();
        OidcUser oidcUser = fetchOidcUser(userRequest);
        String accessToken = userRequest.getAccessToken().getTokenValue();

        try {
            Authentication currentAuth = currentAuthentication();
            HttpServletRequest request = currentRequest();

            if (!hasPendingLauncherOAuth()
                    && currentAuth != null && currentAuth.isAuthenticated() && !currentAuth.getName().equals("anonymousUser")) {
                String pendingOrgId = request != null
                        ? (String) request.getSession().getAttribute("pending_org_link_id")
                        : null;

                if (pendingOrgId != null) {
                    request.getSession().removeAttribute("pending_org_link_id");
                    DefaultOAuth2User linkedUser = authenticationService.linkAccountToOrg(pendingOrgId, provider, oidcUser, accessToken);
                    return new CustomOidcUser(linkedUser, oidcUser.getIdToken(), oidcUser.getUserInfo());
                }

                User currentUser = accountService.getCurrentUser();

                if (currentUser != null) {
                    DefaultOAuth2User linkedUser = authenticationService.linkAccount(currentUser, provider, oidcUser, accessToken);
                    return new CustomOidcUser(linkedUser, oidcUser.getIdToken(), oidcUser.getUserInfo());
                }
            }

            DefaultOAuth2User appUser = authenticationService.processUserLogin(provider, oidcUser, accessToken);
            return new CustomOidcUser(appUser, oidcUser.getIdToken(), oidcUser.getUserInfo());

        } catch (OAuthAccountCollisionException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_collision"), ex.getMessage());
        } catch (InvalidAuthenticationRequestException | ForbiddenOperationException | UnauthorizedException | OrganizationNotFoundException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("login_failure"), ex.getMessage());
        } catch (AuthenticationOperationException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("login_failure"), ex.getMessage());
        }
    }

    private Authentication currentAuthentication() {
        HttpServletRequest request = currentRequest();
        return request != null && request.getUserPrincipal() instanceof Authentication auth ? auth : null;
    }

    private HttpServletRequest currentRequest() {
        return requestProvider.getIfAvailable();
    }

    private boolean hasPendingLauncherOAuth() {
        HttpServletRequest request = currentRequest();
        HttpSession session = request == null ? null : request.getSession(false);
        return session != null
                && session.getAttribute(LauncherAuthService.OAUTH_REDIRECT_URI_SESSION_ATTRIBUTE) instanceof String;
    }

    protected OidcUser fetchOidcUser(OidcUserRequest userRequest) {
        return super.loadUser(userRequest);
    }

    public static class CustomOidcUser extends DefaultOAuth2User implements OidcUser {
        private final OidcIdToken idToken;
        private final OidcUserInfo userInfo;

        public CustomOidcUser(DefaultOAuth2User appUser, OidcIdToken idToken, OidcUserInfo userInfo) {
            super(appUser.getAuthorities(), appUser.getAttributes(), "login");
            this.idToken = idToken;
            this.userInfo = userInfo;
        }

        @Override
        public Map<String, Object> getClaims() {
            return this.getAttributes();
        }

        @Override
        public OidcUserInfo getUserInfo() {
            return this.userInfo;
        }

        @Override
        public OidcIdToken getIdToken() {
            return this.idToken;
        }
    }
}
