package net.modtale.service.auth;

import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.user.ConnectedAccountMutationService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OAuthUserLoginService {

    private final UserRepository userRepository;
    private final BannedEmailRepository bannedEmailRepository;
    private final TrackingService trackingService;
    private final ReservedAccountGuardService reservedAccountGuardService;
    private final OAuthProviderProfileService providerProfileService;
    private final ConnectedAccountMutationService connectedAccountMutationService;

    public OAuthUserLoginService(
            UserRepository userRepository,
            BannedEmailRepository bannedEmailRepository,
            TrackingService trackingService,
            ReservedAccountGuardService reservedAccountGuardService,
            OAuthProviderProfileService providerProfileService,
            ConnectedAccountMutationService connectedAccountMutationService
    ) {
        this.userRepository = userRepository;
        this.bannedEmailRepository = bannedEmailRepository;
        this.trackingService = trackingService;
        this.reservedAccountGuardService = reservedAccountGuardService;
        this.providerProfileService = providerProfileService;
        this.connectedAccountMutationService = connectedAccountMutationService;
    }

    public DefaultOAuth2User processUserLogin(String providerStr, OAuth2User oauthUser, String accessToken) {
        reservedAccountGuardService.purgeReservedAccountsIfProduction();

        OAuthProviderProfile profile = providerProfileService.extract(providerStr, oauthUser);
        reservedAccountGuardService.rejectReservedEmailInProduction(profile.email());

        if (profile.email() != null && bannedEmailRepository.existsByEmailIgnoreCase(profile.email())) {
            throw new ForbiddenOperationException("This account has been suspended.");
        }

        User user = resolveExistingUser(profile);
        boolean isNewUser = user == null;

        if (user != null) {
            if (user.isDeleted()) {
                throw new UnauthorizedException("This account is no longer available.");
            }

            connectedAccountMutationService.linkProvider(
                    user,
                    profile.provider(),
                    profile.providerId(),
                    profile.username(),
                    profile.profileUrl(),
                    profile.visible(),
                    accessToken
            );
            userRepository.save(user);
        } else {
            user = createUser(profile, accessToken);
        }

        return buildPrincipal(user, oauthUser, isNewUser, profile);
    }

    private User resolveExistingUser(OAuthProviderProfile profile) {
        Optional<User> linkedUser = userRepository.findByConnectedAccountsProviderAndProviderId(profile.provider(), profile.providerId());
        if (linkedUser.isPresent()) {
            User user = linkedUser.get();
            reservedAccountGuardService.rejectReservedUserInProduction(user);
            return user;
        }

        if (profile.email() != null && !profile.email().isEmpty()) {
            Optional<User> emailUser = userRepository.findByEmailIgnoreCase(profile.email());
            if (emailUser.isPresent()) {
                User user = emailUser.get();
                reservedAccountGuardService.rejectReservedUserInProduction(user);
                if (!user.isEmailVerified()) {
                    user.setEmailVerified(true);
                    user.setVerificationToken(null);
                    user.setVerificationTokenExpiry(null);
                }
                return user;
            }
        }

        return null;
    }

    private User createUser(OAuthProviderProfile profile, String accessToken) {
        User user = new User();
        user.setEmail(profile.email());
        user.setCreatedAt(LocalDate.now().toString());
        user.setEmailVerified(true);

        if (profile.provider() == OAuthProvider.GOOGLE) {
            String randomHandle = "user_" + UUID.randomUUID().toString().substring(0, 5);
            while (userRepository.existsByUsernameIgnoreCase(randomHandle)) {
                randomHandle = "user_" + UUID.randomUUID().toString().substring(0, 5);
            }
            user.setUsername(randomHandle);
            user.setAvatarUrl("https://ui-avatars.com/api/?name=" + randomHandle + "&background=random&length=1");
        } else {
            String finalUsername = profile.username();
            if (userRepository.existsByUsernameIgnoreCase(finalUsername)) {
                int suffix = 1;
                while (userRepository.existsByUsernameIgnoreCase(finalUsername + "_" + suffix)) {
                    suffix++;
                }
                finalUsername = finalUsername + "_" + suffix;
            }
            user.setUsername(finalUsername);
            user.setAvatarUrl(profile.avatarUrl() != null
                    ? profile.avatarUrl()
                    : "https://ui-avatars.com/api/?name=" + finalUsername + "&background=random");
        }

        connectedAccountMutationService.linkProvider(
                user,
                profile.provider(),
                profile.providerId(),
                profile.username(),
                profile.profileUrl(),
                profile.visible(),
                accessToken
        );

        User savedUser = userRepository.save(user);
        trackingService.logNewUser(savedUser.getId());
        return savedUser;
    }

    private DefaultOAuth2User buildPrincipal(
            User user,
            OAuth2User oauthUser,
            boolean isNewUser,
            OAuthProviderProfile profile
    ) {
        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("login", user.getUsername());
        attributes.put("id", user.getId());
        attributes.remove("is_linking");

        if (isNewUser && profile.provider() == OAuthProvider.GOOGLE) {
            attributes.put("is_new_account", true);
            if (profile.username() != null) {
                attributes.put("suggested_username", profile.username());
            }
            if (profile.avatarUrl() != null) {
                attributes.put("suggested_avatar", profile.avatarUrl());
            }
        }

        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }
}
