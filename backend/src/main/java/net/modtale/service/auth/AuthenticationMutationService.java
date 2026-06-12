package net.modtale.service.auth;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationMutationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationMutationService.class);
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    private final UserRepository userRepository;
    private final BannedEmailRepository bannedEmailRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ReservedAccountGuardService reservedAccountGuardService;

    public AuthenticationMutationService(
            UserRepository userRepository,
            BannedEmailRepository bannedEmailRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ReservedAccountGuardService reservedAccountGuardService
    ) {
        this.userRepository = userRepository;
        this.bannedEmailRepository = bannedEmailRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.reservedAccountGuardService = reservedAccountGuardService;
    }

    public void setTempMfaSecret(String userId, String secret) {
        User user = requireUser(userId);
        user.setMfaSecret(secret);
        userRepository.save(user);
    }

    public void enableMfa(String userId) {
        User user = requireUser(userId);
        user.setMfaEnabled(true);
        userRepository.save(user);
    }

    public void disableMfa(String userId) {
        User user = requireUser(userId);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
    }

    public void addCredentials(String userId, String email, String password) {
        User user = requireUser(userId);
        reservedAccountGuardService.rejectReservedEmailInProduction(email);

        validateEmail(email, "Enter a valid email address before adding sign-in credentials.");
        validatePassword(password, "Passwords must be at least 6 characters long.");
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new InvalidAuthenticationRequestException("This email address is not allowed on Modtale.");
        }

        if (!email.equalsIgnoreCase(user.getEmail())) {
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent() && !existing.get().getId().equals(userId)) {
                throw new InvalidAuthenticationRequestException("Another account is already using that email address.");
            }

            user.setEmail(email);
            user.setEmailVerified(false);
            user.setVerificationToken(UUID.randomUUID().toString());
            user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));

            try {
                emailService.sendVerificationEmail(email, user.getUsername(), user.getVerificationToken());
            } catch (RuntimeException ex) {
                logger.error("Failed to send verification email during credential update", ex);
            }
        }

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidAuthenticationRequestException("The current password you entered was not correct.");
        }

        validatePassword(newPassword, "New passwords must be at least 6 characters long.");
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new InvalidAuthenticationRequestException("That verification link is invalid or has already expired."));

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidAuthenticationRequestException("That verification link has expired. Request a new verification email and try again.");
        }
        if (bannedEmailRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new ForbiddenOperationException("This email address has been suspended.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    public void resendVerificationEmail(User user) {
        if (user.isEmailVerified()) {
            throw new InvalidAuthenticationRequestException("This account's email address has already been verified.");
        }

        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), user.getVerificationToken());
    }

    public void initiatePasswordReset(String email) {
        if (reservedAccountGuardService.isProductionDeployment() && reservedAccountGuardService.isReservedEmail(email)) {
            reservedAccountGuardService.purgeReservedAccountsIfProduction();
            return;
        }
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        User user = userOpt.get();
        if (user.isDeleted() || (user.getPassword() == null && Optional.ofNullable(user.getConnectedAccounts()).orElse(Collections.emptyList()).isEmpty())) {
            return;
        }

        user.setPasswordResetToken(UUID.randomUUID().toString());
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(email, user.getUsername(), user.getPasswordResetToken());
        } catch (RuntimeException ex) {
            logger.error("Failed to send password reset email", ex);
        }
    }

    public void completePasswordReset(String token, String newPassword) {
        validatePassword(newPassword, "Passwords must be at least 6 characters long.");

        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new InvalidAuthenticationRequestException("That password reset link is invalid or has already expired."));

        if (user.getPasswordResetTokenExpiry() != null && user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidAuthenticationRequestException("That password reset link has expired. Request a new reset email and try again.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private void validateEmail(String email, String message) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidAuthenticationRequestException(message);
        }
    }

    private void validatePassword(String password, String message) {
        if (password == null || password.length() < 6) {
            throw new InvalidAuthenticationRequestException(message);
        }
    }
}
