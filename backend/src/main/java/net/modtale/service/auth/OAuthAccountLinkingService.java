package net.modtale.service.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.OAuthAccountCollisionException;
import net.modtale.exception.OrganizationNotFoundException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.connection.ConnectedAccountMutationService;
import net.modtale.service.system.PublicCreatorCacheService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OAuthAccountLinkingService {

    private final PublicCreatorCacheService publicCreatorCacheService;

    private final UserRepository userRepository;
    private final OAuthProviderProfileService providerProfileService;
    private final ConnectedAccountMutationService connectedAccountMutationService;

    public OAuthAccountLinkingService(
            UserRepository userRepository,
            OAuthProviderProfileService providerProfileService,
            ConnectedAccountMutationService connectedAccountMutationService,
            PublicCreatorCacheService publicCreatorCacheService
    ) {
        this.publicCreatorCacheService = publicCreatorCacheService;
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

        boolean publicConnectionChanged = PublicCreatorCacheService.publicConnectionWouldChange(
                currentUser, profile.provider(), profile.providerId(), profile.username(), profile.profileUrl(), profile.visible());
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
        if (publicConnectionChanged) publicCreatorCacheService.creatorChanged();

        return buildLinkedPrincipal(currentUser);
    }

    public DefaultOAuth2User linkAccountToOrg(String orgId, String providerStr, OAuth2User oauthUser, String accessToken) {
        OAuthProviderProfile profile = providerProfileService.extract(providerStr, oauthUser);

        if (profile.provider() == net.modtale.model.user.OAuthProvider.DISCORD
                || profile.provider() == net.modtale.model.user.OAuthProvider.GOOGLE
                || profile.provider() == net.modtale.model.user.OAuthProvider.HYTALE) {
            throw new InvalidAuthenticationRequestException("Organizations cannot link " + providerStr + " accounts.");
        }

        User org = userRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException("We couldn't find that organization."));

        Optional<User> conflict = userRepository.findByConnectedAccountsProviderAndProviderId(profile.provider(), profile.providerId());
        if (conflict.isPresent() && !conflict.get().getId().equals(orgId)) {
            throw new OAuthAccountCollisionException("That account is already linked to another user or organization.");
        }

        boolean publicConnectionChanged = PublicCreatorCacheService.publicConnectionWouldChange(
                org, profile.provider(), profile.providerId(), profile.username(), profile.profileUrl(), true);
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
        if (publicConnectionChanged) publicCreatorCacheService.creatorChanged();

        return buildLinkedPrincipal(org);
    }

    private DefaultOAuth2User buildLinkedPrincipal(User user) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", user.getUsername());
        attributes.put("id", user.getId());
        attributes.put("is_linking", true);
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(java.util.stream.Collectors.toList());
        return new DefaultOAuth2User(authorities, attributes, "login");
    }
}
