package net.modtale.config.security;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.model.user.User;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOidcUserService extends OidcUserService {

    @Autowired
    private UserService userService;

    @Autowired
    private HttpServletRequest request;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        String provider = userRequest.getClientRegistration().getRegistrationId();
        OidcUser oidcUser = super.loadUser(userRequest);
        String accessToken = userRequest.getAccessToken().getTokenValue();

        try {
            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
            if (currentAuth != null && currentAuth.isAuthenticated() && !currentAuth.getName().equals("anonymousUser")) {

                String pendingOrgId = (String) request.getSession().getAttribute("pending_org_link_id");

                if (pendingOrgId != null) {
                    request.getSession().removeAttribute("pending_org_link_id");
                    DefaultOAuth2User linkedUser = userService.linkAccountToOrg(pendingOrgId, provider, oidcUser, accessToken);
                    return new CustomOidcUser(linkedUser, oidcUser.getIdToken(), oidcUser.getUserInfo());
                }

                User currentUser = null;

                if (currentAuth.getPrincipal() instanceof User) {
                    currentUser = (User) currentAuth.getPrincipal();
                } else if (currentAuth.getPrincipal() instanceof OAuth2User) {
                    String username = ((OAuth2User) currentAuth.getPrincipal()).getAttribute("login");
                    if (username == null) username = currentAuth.getName();
                    currentUser = userService.getPublicProfile(username);
                }

                if (currentUser != null) {
                    DefaultOAuth2User linkedUser = userService.linkAccount(currentUser, provider, oidcUser, accessToken);
                    return new CustomOidcUser(linkedUser, oidcUser.getIdToken(), oidcUser.getUserInfo());
                }
            }

            DefaultOAuth2User appUser = userService.processUserLogin(provider, oidcUser, accessToken);
            return new CustomOidcUser(appUser, oidcUser.getIdToken(), oidcUser.getUserInfo());

        } catch (Exception ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("login_failure"), "Authentication processing failed: " + ex.getMessage());
        }
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