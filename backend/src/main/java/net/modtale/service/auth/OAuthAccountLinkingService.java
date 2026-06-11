package net.modtale.service.auth;

import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.OAuthAccountCollisionException;
import net.modtale.exception.OrganizationNotFoundException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.ConnectedAccountMutationService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class OAuthAccountLinkingService {

    private final UserRepository userRepository;
    private final OAuthProviderProfileService providerProfileService;
    private final ConnectedAccountMutationService connectedAccountMutationService;

    public OAuthAccountLinkingService(
            UserRepository userRepository,
            OAuthProviderProfileService providerProfileService,
            ConnectedAccountMutationService connectedAccountMutationService
    ) {
        this.userRepository = userRepository;
        this.providerProfileService = providerProfileService;
        this.connectedAccountMutationService = connectedAccountMutationService;
    }

    public DefaultOAuth2User linkAccount(User currentUser, String providerStr, OAuth2User oauthUser, String accessToken) {
        OAuthProviderProfile profile = providerProfileService.extract(providerStr, oauthUser);

        Optional<User> conflict = userRepository.findByConnectedAccountsProviderAndProviderId(profile.provider(), profile.providerId());
        if (conflict.isPresent() && !conflict.get().getId().equals(currentUser.getId())) {
            throw new OAuthAccountCollisionException("That account is already linked to another Modtale user.");
        }

        connectedAccountMutationService.linkProvider(
                currentUser,
                profile.provider(),
                profile.providerId(),
                profile.username(),
                profile.profileUrl(),
                profile.visible(),
                accessToken
        );
        userRepository.save(currentUser);

        return buildLinkedPrincipal(currentUser);
    }

    public DefaultOAuth2User linkAccountToOrg(String orgId, String providerStr, OAuth2User oauthUser, String accessToken) {
        OAuthProviderProfile profile = providerProfileService.extract(providerStr, oauthUser);

        if (profile.provider() == net.modtale.model.user.OAuthProvider.DISCORD
                || profile.provider() == net.modtale.model.user.OAuthProvider.GOOGLE) {
            throw new InvalidAuthenticationRequestException("Organizations cannot link " + providerStr + " accounts.");
        }

        User org = userRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException("We couldn't find that organization."));

        Optional<User> conflict = userRepository.findByConnectedAccountsProviderAndProviderId(profile.provider(), profile.providerId());
        if (conflict.isPresent() && !conflict.get().getId().equals(orgId)) {
            throw new OAuthAccountCollisionException("That account is already linked to another user or organization.");
        }

        connectedAccountMutationService.linkProvider(
                org,
                profile.provider(),
                profile.providerId(),
                profile.username(),
                profile.profileUrl(),
                true,
                accessToken
        );
        userRepository.save(org);

        return buildLinkedPrincipal(org);
    }

    private DefaultOAuth2User buildLinkedPrincipal(User user) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", user.getUsername());
        attributes.put("id", user.getId());
        attributes.put("is_linking", true);
        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }
}
