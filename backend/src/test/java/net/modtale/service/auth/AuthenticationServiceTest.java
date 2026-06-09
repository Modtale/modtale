package net.modtale.service.auth;

import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private AuthenticationService authenticationService;
    private UserRepository userRepository;
    private BannedEmailRepository bannedEmailRepository;
    private TrackingService trackingService;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private ReservedAccountGuardService reservedAccountGuardService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService();
        userRepository = mock(UserRepository.class);
        bannedEmailRepository = mock(BannedEmailRepository.class);
        trackingService = mock(TrackingService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        reservedAccountGuardService = mock(ReservedAccountGuardService.class);

        ReflectionTestUtils.setField(authenticationService, "userRepository", userRepository);
        ReflectionTestUtils.setField(authenticationService, "bannedEmailRepository", bannedEmailRepository);
        ReflectionTestUtils.setField(authenticationService, "trackingService", trackingService);
        ReflectionTestUtils.setField(authenticationService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authenticationService, "emailService", emailService);
        ReflectionTestUtils.setField(authenticationService, "reservedAccountGuardService", reservedAccountGuardService);
        ReflectionTestUtils.setField(authenticationService, "preAuthSigningKey", "test-signing-key");
        ReflectionTestUtils.setField(authenticationService, "preAuthExpirySeconds", 60L);
    }

    @Test
    void registerUserEncodesPasswordSavesUserAndSendsVerificationEmail() {
        when(bannedEmailRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("Ada")).thenReturn(false);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = authenticationService.registerUser("Ada", "ada@example.com", "secret123");

        assertDoesNotThrow(() -> UUID.fromString(saved.getId()));
        assertEquals("Ada", saved.getUsername());
        assertEquals("encoded-secret", saved.getPassword());
        assertFalse(saved.isEmailVerified());
        assertEquals(List.of("USER"), saved.getRoles());
        assertNotNull(saved.getVerificationToken());
        assertNotNull(saved.getVerificationTokenExpiry());
        assertTrue(saved.getAvatarUrl().contains("name=Ada"));
        verify(trackingService).logNewUser(saved.getId());
        verify(emailService).sendVerificationEmail("ada@example.com", "Ada", saved.getVerificationToken());
        verify(reservedAccountGuardService).rejectReservedEmailInProduction("ada@example.com");
    }

    @Test
    void authenticateRejectsSuspendedAccountsBeforePasswordValidation() {
        User user = user("user-1", "Ada", "ada@example.com");
        user.setPassword("encoded");

        when(userRepository.findByUsernameIgnoreCase("Ada")).thenReturn(Optional.of(user));
        when(bannedEmailRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThrows(SecurityException.class, () -> authenticationService.authenticate("Ada", "secret123"));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void authenticateReturnsTheUserWhenPasswordMatches() {
        User user = user("user-1", "Ada", "ada@example.com");
        user.setPassword("encoded");

        when(userRepository.findByUsernameIgnoreCase("Ada")).thenReturn(Optional.of(user));
        when(bannedEmailRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(passwordEncoder.matches("secret123", "encoded")).thenReturn(true);

        User authenticated = authenticationService.authenticate("Ada", "secret123");

        assertSame(user, authenticated);
        verify(reservedAccountGuardService).purgeReservedAccountsIfProduction();
        verify(reservedAccountGuardService).rejectReservedUserInProduction(user);
    }

    @Test
    void validatePreAuthTokenRoundTripsForValidUsersAndRejectsTampering() {
        User user = user("user-1", "Ada", "ada@example.com");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        String token = authenticationService.generatePreAuthToken("user-1");

        assertSame(user, authenticationService.validatePreAuthToken(token));
        assertNull(authenticationService.validatePreAuthToken(token.substring(0, token.length() - 2) + "xx"));
    }

    @Test
    void addCredentialsChangingEmailResetsVerificationAndSendsANewMessage() {
        User user = user("user-1", "Ada", "old@example.com");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(bannedEmailRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("newpass1")).thenReturn("encoded-newpass");

        authenticationService.addCredentials("user-1", "new@example.com", "newpass1");

        assertEquals("new@example.com", user.getEmail());
        assertFalse(user.isEmailVerified());
        assertEquals("encoded-newpass", user.getPassword());
        assertNotNull(user.getVerificationToken());
        assertNotNull(user.getVerificationTokenExpiry());
        verify(emailService).sendVerificationEmail("new@example.com", "Ada", user.getVerificationToken());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailMarksTheAddressVerifiedAndClearsTheTokenState() {
        User user = user("user-1", "Ada", "ada@example.com");
        user.setVerificationToken("verify-token");
        user.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByVerificationToken("verify-token")).thenReturn(Optional.of(user));
        when(bannedEmailRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);

        authenticationService.verifyEmail("verify-token");

        assertTrue(user.isEmailVerified());
        assertNull(user.getVerificationToken());
        assertNull(user.getVerificationTokenExpiry());
        verify(userRepository).save(user);
    }

    @Test
    void initiatePasswordResetStoresATokenAndEmailsEligibleUsers() {
        User user = user("user-1", "Ada", "ada@example.com");
        user.setPassword("encoded");

        when(bannedEmailRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        authenticationService.initiatePasswordReset("ada@example.com");

        assertNotNull(user.getPasswordResetToken());
        assertNotNull(user.getPasswordResetTokenExpiry());
        verify(userRepository).save(user);
        verify(emailService).sendPasswordResetEmail("ada@example.com", "Ada", user.getPasswordResetToken());
    }

    @Test
    void completePasswordResetEncodesThePasswordAndClearsResetState() {
        User user = user("user-1", "Ada", "ada@example.com");
        user.setPasswordResetToken("reset-token");
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByPasswordResetToken("reset-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass1")).thenReturn("encoded-reset");

        authenticationService.completePasswordReset("reset-token", "newpass1");

        assertEquals("encoded-reset", user.getPassword());
        assertNull(user.getPasswordResetToken());
        assertNull(user.getPasswordResetTokenExpiry());
        verify(userRepository).save(user);
    }

    @Test
    void processUserLoginCreatesAGoogleAccountWithLinkMetadataAndSuggestedFields() {
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GOOGLE, "google-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(bannedEmailRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DefaultOAuth2User oauthUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-123",
                        "name", "Ada Example",
                        "email", "ada@example.com",
                        "picture", "https://cdn.example/avatar.png"
                ),
                "sub"
        );

        DefaultOAuth2User principal = authenticationService.processUserLogin("google", oauthUser, "ignored-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("ada@example.com", savedUser.getEmail());
        assertTrue(savedUser.isEmailVerified());
        assertTrue(savedUser.getUsername().startsWith("user_"));
        assertTrue(savedUser.getAvatarUrl().contains(savedUser.getUsername()));
        assertEquals(1, savedUser.getConnectedAccounts().size());
        assertEquals(OAuthProvider.GOOGLE, savedUser.getConnectedAccounts().get(0).getProvider());
        assertEquals("google-123", savedUser.getConnectedAccounts().get(0).getProviderId());
        assertEquals("AdaExample", savedUser.getConnectedAccounts().get(0).getUsername());
        assertFalse(savedUser.getConnectedAccounts().get(0).isVisible());
        assertDoesNotThrow(() -> UUID.fromString(savedUser.getId()));

        assertEquals(savedUser.getUsername(), principal.getAttribute("login"));
        assertEquals(savedUser.getId(), principal.getAttribute("id"));
        assertEquals(Boolean.TRUE, principal.getAttribute("is_new_account"));
        assertEquals("AdaExample", principal.getAttribute("suggested_username"));
        assertEquals("https://cdn.example/avatar.png", principal.getAttribute("suggested_avatar"));
        verify(trackingService).logNewUser(savedUser.getId());
    }

    private static User user(String id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }
}
