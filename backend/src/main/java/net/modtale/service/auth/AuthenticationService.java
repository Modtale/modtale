package net.modtale.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.EmailService;
import net.modtale.validation.AccountNameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    private final UserRepository userRepository;
    private final BannedEmailRepository bannedEmailRepository;
    private final TrackingService trackingService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ReservedAccountGuardService reservedAccountGuardService;
    private final AppSecurityProperties securityProperties;
    private final OAuthUserLoginService oauthUserLoginService;
    private final OAuthAccountLinkingService oauthAccountLinkingService;

    public AuthenticationService(
            UserRepository userRepository,
            BannedEmailRepository bannedEmailRepository,
            TrackingService trackingService,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ReservedAccountGuardService reservedAccountGuardService,
            AppSecurityProperties securityProperties,
            OAuthUserLoginService oauthUserLoginService,
            OAuthAccountLinkingService oauthAccountLinkingService
    ) {
        this.userRepository = userRepository;
        this.bannedEmailRepository = bannedEmailRepository;
        this.trackingService = trackingService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.reservedAccountGuardService = reservedAccountGuardService;
        this.securityProperties = securityProperties;
        this.oauthUserLoginService = oauthUserLoginService;
        this.oauthAccountLinkingService = oauthAccountLinkingService;
    }

    public User registerUser(String username, String email, String password) {
        reservedAccountGuardService.rejectReservedEmailInProduction(email);
        AccountNameRules.validateRegistrationUsername(username);
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidAuthenticationRequestException("Enter a valid email address before creating an account.");
        }
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new InvalidAuthenticationRequestException("This email address is not allowed to register on Modtale.");
        }
        if (password == null || password.length() < 6) {
            throw new InvalidAuthenticationRequestException("Passwords must be at least 6 characters long.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new InvalidAuthenticationRequestException("That username is already taken.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new InvalidAuthenticationRequestException("An account with that email address already exists.");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setCreatedAt(LocalDate.now().toString());
        user.setRoles(Collections.singletonList("USER"));
        user.setEmailVerified(false);
        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        user.setAvatarUrl("https://ui-avatars.com/api/?name=" + username + "&background=random");

        User savedUser = userRepository.save(user);
        trackingService.logNewUser(savedUser.getId());

        try {
            emailService.sendVerificationEmail(email, username, savedUser.getVerificationToken());
        } catch (RuntimeException e) {
            logger.error("Failed to send verification email during registration", e);
        }

        return savedUser;
    }

    public User authenticate(String login, String password) {
        reservedAccountGuardService.rejectReservedEmailInProduction(login);
        reservedAccountGuardService.purgeReservedAccountsIfProduction();

        User user = userRepository.findByUsernameIgnoreCase(login)
                .or(() -> userRepository.findByEmail(login))
                .orElseThrow(() -> new UnauthorizedException("We couldn't sign you in with that username and password. Double-check both fields and try again."));
        reservedAccountGuardService.rejectReservedUserInProduction(user);

        if (user.isDeleted()) throw new UnauthorizedException("This account is no longer available.");
        if (bannedEmailRepository.existsByEmailIgnoreCase(user.getEmail())) throw new UnauthorizedException("This account has been suspended.");

        if (!user.getHasPassword() || !passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("We couldn't sign you in with that username and password. Double-check both fields and try again.");
        }

        if (user.isMfaEnabled() && (user.getMfaSecret() == null || user.getMfaSecret().isBlank())) {
            logger.warn("User {} has MFA enabled but missing secret. Auto-disabling MFA to prevent lockout.", user.getId());
            user.setMfaEnabled(false);
            user = userRepository.save(user);
        }

        return user;
    }

    public String generatePreAuthToken(String userId) {
        long expiry = System.currentTimeMillis() + (securityProperties.preAuthExpirySeconds() * 1000);
        String payload = userId + ":" + expiry;
        String signature = hmacSha256(payload, securityProperties.preAuthSecret());
        return Base64.getEncoder().encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    public User validatePreAuthToken(String token) {
        reservedAccountGuardService.purgeReservedAccountsIfProduction();

        try {
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 3) return null;

            String userId = parts[0];
            long expiry = Long.parseLong(parts[1]);
            String providedSignature = parts[2];

            if (System.currentTimeMillis() > expiry) return null;

            String expectedSignature = hmacSha256(userId + ":" + expiry, securityProperties.preAuthSecret());
            if (!expectedSignature.equals(providedSignature)) return null;

            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.isDeleted()) return null;
            if (user != null) reservedAccountGuardService.rejectReservedUserInProduction(user);
            return user;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC", e);
        }
    }

    public DefaultOAuth2User processUserLogin(String providerStr, OAuth2User oauthUser, String accessToken) {
        return oauthUserLoginService.processUserLogin(providerStr, oauthUser, accessToken);
    }

    public DefaultOAuth2User linkAccount(User currentUser, String providerStr, OAuth2User oauthUser, String accessToken) {
        return oauthAccountLinkingService.linkAccount(currentUser, providerStr, oauthUser, accessToken);
    }

    public DefaultOAuth2User linkAccountToOrg(String orgId, String providerStr, OAuth2User oauthUser, String accessToken) {
        return oauthAccountLinkingService.linkAccountToOrg(orgId, providerStr, oauthUser, accessToken);
    }
}
