package net.modtale.config.auth;

import net.modtale.model.user.User;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String provider = userRequest.getClientRegistration().getRegistrationId();

        OAuth2User oauthUser = super.loadUser(userRequest);

        String accessToken = userRequest.getAccessToken().getTokenValue();

        try {
            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

            if (currentAuth != null && currentAuth.isAuthenticated() &&
                    !currentAuth.getName().equals("anonymousUser")) {

                User currentUser = null;

                if (currentAuth.getPrincipal() instanceof User) {
                    currentUser = (User) currentAuth.getPrincipal();
                } else if (currentAuth.getPrincipal() instanceof OAuth2User) {
                    String username = ((OAuth2User) currentAuth.getPrincipal()).getAttribute("login");
                    if (username == null) username = currentAuth.getName();
                    currentUser = userService.getPublicProfile(username);
                }

                if (currentUser != null) {
                    return userService.linkAccount(currentUser, provider, oauthUser, accessToken);
                }
            }

            return userService.processUserLogin(provider, oauthUser, accessToken);
        } catch (IllegalArgumentException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_collision"), ex.getMessage());
        } catch (Exception ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("login_failure"), "Authentication processing failed: " + ex.getMessage());
        }
    }
}