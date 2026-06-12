package net.modtale.service.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationMutationServiceTest {

    private AuthenticationMutationService service;
    private UserRepository userRepository;
    private BannedEmailRepository bannedEmailRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private ReservedAccountGuardService reservedAccountGuardService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        bannedEmailRepository = mock(BannedEmailRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        reservedAccountGuardService = mock(ReservedAccountGuardService.class);
        service = new AuthenticationMutationService(
                userRepository,
                bannedEmailRepository,
                passwordEncoder,
                emailService,
                reservedAccountGuardService
        );
    }

    @Test
    void addCredentialsChangingEmailResetsVerificationAndSendsANewMessage() {
        User user = user("user-1", "Ada", "old@example.com");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(bannedEmailRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("newpass1")).thenReturn("encoded-newpass");

        service.addCredentials("user-1", "new@example.com", "newpass1");

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

        service.verifyEmail("verify-token");

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

        service.initiatePasswordReset("ada@example.com");

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

        service.completePasswordReset("reset-token", "newpass1");

        assertEquals("encoded-reset", user.getPassword());
        assertNull(user.getPasswordResetToken());
        assertNull(user.getPasswordResetTokenExpiry());
        verify(userRepository).save(user);
    }

    @Test
    void enableMfaMarksTheAccountEnabled() {
        User user = user("user-1", "Ada", "ada@example.com");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        service.enableMfa("user-1");

        assertTrue(user.isMfaEnabled());
        verify(userRepository).save(user);
    }

    private static User user(String id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }
}
