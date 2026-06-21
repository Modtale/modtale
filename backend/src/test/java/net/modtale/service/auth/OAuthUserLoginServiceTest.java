package net.modtale.service.auth;

import java.util.Map;
import java.util.Optional;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.user.connection.ConnectedAccountMutationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthUserLoginServiceTest {

    private UserRepository userRepository;
    private BannedEmailRepository bannedEmailRepository;
    private TrackingService trackingService;
    private ReservedAccountGuardService reservedAccountGuardService;
    private OAuthProviderProfileService providerProfileService;
    private ConnectedAccountMutationService connectedAccountMutationService;
    private OAuthUserLoginService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        bannedEmailRepository = mock(BannedEmailRepository.class);
        trackingService = mock(TrackingService.class);
        reservedAccountGuardService = mock(ReservedAccountGuardService.class);
        providerProfileService = mock(OAuthProviderProfileService.class);
        connectedAccountMutationService = mock(ConnectedAccountMutationService.class);
        service = new OAuthUserLoginService(
                userRepository,
                bannedEmailRepository,
                trackingService,
                reservedAccountGuardService,
                providerProfileService,
                connectedAccountMutationService
        );
    }

    @Test
    void processUserLoginRejectsBannedEmailBeforeCreatingOrSavingUsers() {
        OAuth2User oauthUser = oauthUser();
        OAuthProviderProfile profile = profile(OAuthProvider.GITHUB, "willow", "willow@example.com");

        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile);
        when(bannedEmailRepository.existsByEmailIgnoreCase("willow@example.com")).thenReturn(true);

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.processUserLogin("github", oauthUser, "token")
        );

        verify(userRepository, never()).save(any(User.class));
        verify(trackingService, never()).logNewUser(any());
    }

    @Test
    void processUserLoginRejectsDeletedLinkedUsers() {
        OAuth2User oauthUser = oauthUser();
        OAuthProviderProfile profile = profile(OAuthProvider.GITHUB, "willow", "willow@example.com");
        User deletedUser = user("user-1", "willow");
        deletedUser.setDeletedAt(java.time.LocalDateTime.now());

        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile);
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GITHUB, "provider-1"))
                .thenReturn(Optional.of(deletedUser));

        assertThrows(
                UnauthorizedException.class,
                () -> service.processUserLogin("github", oauthUser, "token")
        );
    }

    @Test
    void processUserLoginVerifiesExistingEmailUserAndLinksProvider() {
        OAuth2User oauthUser = oauthUser();
        OAuthProviderProfile profile = profile(OAuthProvider.GITHUB, "willow", "willow@example.com");
        User existingUser = user("user-1", "willow");
        existingUser.setEmailVerified(false);
        existingUser.setVerificationToken("verify-token");
        existingUser.setVerificationTokenExpiry(java.time.LocalDateTime.now().plusDays(1));

        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile);
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GITHUB, "provider-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("willow@example.com")).thenReturn(Optional.of(existingUser));

        DefaultOAuth2User principal = service.processUserLogin("github", oauthUser, "token");

        assertEquals("willow", principal.getAttribute("login"));
        assertFalse(Boolean.TRUE.equals(principal.getAttribute("is_new_account")));
        assertTrue(existingUser.isEmailVerified());
        assertNull(existingUser.getVerificationToken());
        assertNull(existingUser.getVerificationTokenExpiry());
        verify(connectedAccountMutationService).linkProvider(
                existingUser,
                OAuthProvider.GITHUB,
                "provider-1",
                "willow",
                "https://example.com/profile",
                true,
                "token"
        );
        verify(userRepository).save(existingUser);
        verify(trackingService, never()).logNewUser(any());
    }

    @Test
    void processUserLoginCreatesNewNonGoogleUserWithDeduplicatedUsername() {
        OAuth2User oauthUser = oauthUser();
        OAuthProviderProfile profile = profile(OAuthProvider.GITHUB, "willow", "willow@example.com");
        User savedUser = user("user-1", "willow_2");

        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile);
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GITHUB, "provider-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("willow@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("willow")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("willow_1")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("willow_2")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });

        DefaultOAuth2User principal = service.processUserLogin("github", oauthUser, "token");

        assertEquals("willow_2", principal.getAttribute("login"));
        verify(trackingService).logNewUser("user-1");
    }

    @Test
    void processUserLoginCreatesGoogleUserWithOnboardingHints() {
        OAuth2User oauthUser = oauthUser();
        OAuthProviderProfile profile = profile(OAuthProvider.GOOGLE, "Willow Branch", "willow@example.com");

        when(providerProfileService.extract("google", oauthUser)).thenReturn(profile);
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GOOGLE, "provider-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("willow@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });

        DefaultOAuth2User principal = service.processUserLogin("google", oauthUser, "token");

        assertEquals(true, principal.getAttribute("is_new_account"));
        assertEquals("Willow Branch", principal.getAttribute("suggested_username"));
        assertEquals("https://example.com/avatar.png", principal.getAttribute("suggested_avatar"));
        verify(trackingService).logNewUser("user-1");
    }

    private static OAuthProviderProfile profile(OAuthProvider provider, String username, String email) {
        return new OAuthProviderProfile(
                provider,
                "provider-1",
                username,
                "https://example.com/avatar.png",
                email,
                "https://example.com/profile",
                provider != OAuthProvider.GOOGLE
        );
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static OAuth2User oauthUser() {
        return new DefaultOAuth2User(
                java.util.List.of(() -> "ROLE_USER"),
                Map.of("login", "provider-user"),
                "login"
        );
    }
}
