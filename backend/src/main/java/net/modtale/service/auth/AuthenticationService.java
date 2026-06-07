package net.modtale.service.auth;

import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    @Autowired private UserRepository userRepository;
    @Autowired private BannedEmailRepository bannedEmailRepository;
    @Autowired private TrackingService trackingService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;
    @Autowired private ReservedAccountGuardService reservedAccountGuardService;

    @Value("${app.security.pre-auth-secret:default-secret-change-in-prod}")
    private String preAuthSigningKey;

    @Value("${app.security.pre-auth-expiry-seconds:600}")
    private long preAuthExpirySeconds;

    public User registerUser(String username, String email, String password) {
        reservedAccountGuardService.rejectReservedEmailInProduction(email);

        if (username == null || username.length() < 3 || !username.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid username. Must be at least 3 characters and alphanumeric.");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email address format.");
        }
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("This email address is prohibited from registration.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already taken.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered.");
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
        } catch (Exception e) {
            logger.error("Failed to send verification email during registration", e);
        }

        return savedUser;
    }

    public User authenticate(String login, String password) {
        reservedAccountGuardService.rejectReservedEmailInProduction(login);
        reservedAccountGuardService.purgeReservedAccountsIfProduction();

        User user = userRepository.findByUsernameIgnoreCase(login)
                .or(() -> userRepository.findByEmail(login))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        reservedAccountGuardService.rejectReservedUserInProduction(user);

        if (user.isDeleted()) throw new IllegalArgumentException("Account deleted.");
        if (bannedEmailRepository.existsByEmailIgnoreCase(user.getEmail())) throw new SecurityException("This account has been suspended.");

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return user;
    }

    public void setTempMfaSecret(String userId, String secret) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setMfaSecret(secret);
        userRepository.save(user);
    }

    public void enableMfa(String userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setMfaEnabled(true);
        userRepository.save(user);
    }

    public void disableMfa(String userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
    }

    public String generatePreAuthToken(String userId) {
        long expiry = System.currentTimeMillis() + (preAuthExpirySeconds * 1000);
        String payload = userId + ":" + expiry;
        String signature = hmacSha256(payload, preAuthSigningKey);
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

            String expectedSignature = hmacSha256(userId + ":" + expiry, preAuthSigningKey);
            if (!expectedSignature.equals(providedSignature)) return null;

            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.isDeleted()) return null;
            if (user != null) reservedAccountGuardService.rejectReservedUserInProduction(user);
            return user;
        } catch (Exception e) {
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
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

    public void addCredentials(String userId, String email, String password) {
        User user = userRepository.findById(userId).orElseThrow();
        reservedAccountGuardService.rejectReservedEmailInProduction(email);

        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) throw new IllegalArgumentException("Invalid email format.");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("This email address is not allowed.");

        if (!email.equalsIgnoreCase(user.getEmail())) {
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent() && !existing.get().getId().equals(userId)) {
                throw new IllegalArgumentException("Email already in use.");
            }
            user.setEmail(email);
            user.setEmailVerified(false);
            user.setVerificationToken(UUID.randomUUID().toString());
            user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));

            try {
                emailService.sendVerificationEmail(email, user.getUsername(), user.getVerificationToken());
            } catch (Exception e) {
                logger.error("Failed to send verification email during update", e);
            }
        }

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token."));

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification link has expired. Please request a new one.");
        }

        if (bannedEmailRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new SecurityException("This email address is suspended.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    public void resendVerificationEmail(User user) {
        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email is already verified.");
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

        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) return;

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            return;
        }

        User user = userOpt.get();
        if (user.isDeleted() || (user.getPassword() == null && user.getConnectedAccounts().isEmpty())) return;

        user.setPasswordResetToken(UUID.randomUUID().toString());
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(email, user.getUsername(), user.getPasswordResetToken());
        } catch (Exception e) {
            logger.error("Failed to send password reset email", e);
        }
    }

    public void completePasswordReset(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        if (user.getPasswordResetTokenExpiry() != null && user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset link has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
    }

    public DefaultOAuth2User processUserLogin(String providerStr, OAuth2User oauthUser, String accessToken) {
        reservedAccountGuardService.purgeReservedAccountsIfProduction();

        OAuthProvider provider = OAuthProvider.fromString(providerStr);
        if (provider == null) throw new IllegalArgumentException("Unsupported OAuth provider: " + providerStr);

        String providerId = extractProviderId(providerStr, oauthUser);
        String oauthUsername = extractUsername(providerStr, oauthUser);
        String oauthAvatar = extractAvatarUrl(providerStr, oauthUser, providerId);
        String email = oauthUser.getAttribute("email");
        String profileUrl = extractProfileUrl(providerStr, oauthUser, oauthUsername, providerId);
        reservedAccountGuardService.rejectReservedEmailInProduction(email);

        boolean isVisible = provider != OAuthProvider.GOOGLE;
        User user = null;

        if (email != null && bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Account suspended.");
        }

        Optional<User> linkedUser = userRepository.findByConnectedAccountsProviderAndProviderId(provider, providerId);
        if (linkedUser.isPresent()) {
            user = linkedUser.get();
            reservedAccountGuardService.rejectReservedUserInProduction(user);
        } else if (email != null && !email.isEmpty()) {
            Optional<User> emailUser = userRepository.findByEmailIgnoreCase(email);
            if (emailUser.isPresent()) {
                user = emailUser.get();
                reservedAccountGuardService.rejectReservedUserInProduction(user);
                if (!user.isEmailVerified()) {
                    user.setEmailVerified(true);
                    user.setVerificationToken(null);
                    user.setVerificationTokenExpiry(null);
                }
            }
        }

        boolean isNewUser = false;

        if (user != null) {
            if (user.isDeleted()) throw new IllegalArgumentException("Account deleted.");

            if (provider == OAuthProvider.GITHUB) user.setGithubAccessToken(accessToken);
            if (provider == OAuthProvider.GITLAB) user.setGitlabAccessToken(accessToken);

            updateConnectedAccount(user, provider, providerId, oauthUsername, profileUrl, isVisible);
            userRepository.save(user);
        } else {
            isNewUser = true;

            user = new User();
            user.setEmail(email);
            user.setCreatedAt(LocalDate.now().toString());
            user.setEmailVerified(true);

            if (provider == OAuthProvider.GOOGLE) {
                String randomHandle = "user_" + UUID.randomUUID().toString().substring(0, 5);
                while (userRepository.existsByUsernameIgnoreCase(randomHandle)) {
                    randomHandle = "user_" + UUID.randomUUID().toString().substring(0, 5);
                }
                user.setUsername(randomHandle);
                user.setAvatarUrl("https://ui-avatars.com/api/?name=" + randomHandle + "&background=random&length=1");
            } else {
                String finalUsername = oauthUsername;
                if (userRepository.existsByUsernameIgnoreCase(finalUsername)) {
                    int suffix = 1;
                    while (userRepository.existsByUsernameIgnoreCase(finalUsername + "_" + suffix)) {
                        suffix++;
                    }
                    finalUsername = finalUsername + "_" + suffix;
                }
                user.setUsername(finalUsername);
                user.setAvatarUrl(oauthAvatar != null ? oauthAvatar : "https://ui-avatars.com/api/?name=" + finalUsername + "&background=random");
            }

            if (provider == OAuthProvider.GITHUB) user.setGithubAccessToken(accessToken);
            if (provider == OAuthProvider.GITLAB) user.setGitlabAccessToken(accessToken);

            updateConnectedAccount(user, provider, providerId, oauthUsername, profileUrl, isVisible);
            User savedUser = userRepository.save(user);

            trackingService.logNewUser(savedUser.getId());
            user = savedUser;
        }

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("login", user.getUsername());
        attributes.put("id", user.getId());
        attributes.remove("is_linking");

        if (isNewUser && provider == OAuthProvider.GOOGLE) {
            attributes.put("is_new_account", true);
            if (oauthUsername != null) attributes.put("suggested_username", oauthUsername);
            if (oauthAvatar != null) attributes.put("suggested_avatar", oauthAvatar);
        }

        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }

    public DefaultOAuth2User linkAccount(User currentUser, String providerStr, OAuth2User oauthUser, String accessToken) {
        OAuthProvider provider = OAuthProvider.fromString(providerStr);
        if (provider == null) throw new IllegalArgumentException("Unsupported OAuth provider: " + providerStr);

        String providerId = extractProviderId(providerStr, oauthUser);
        String username = extractUsername(providerStr, oauthUser);
        String profileUrl = extractProfileUrl(providerStr, oauthUser, username, providerId);
        boolean isVisible = provider != OAuthProvider.GOOGLE;

        Optional<User> conflict = userRepository.findByConnectedAccountsProviderAndProviderId(provider, providerId);
        if (conflict.isPresent() && !conflict.get().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("This account is already linked to another user.");
        }

        if (provider == OAuthProvider.GITHUB) currentUser.setGithubAccessToken(accessToken);
        if (provider == OAuthProvider.GITLAB) currentUser.setGitlabAccessToken(accessToken);

        updateConnectedAccount(currentUser, provider, providerId, username, profileUrl, isVisible);
        userRepository.save(currentUser);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", currentUser.getUsername());
        attributes.put("id", currentUser.getId());
        attributes.put("is_linking", true);
        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }

    public DefaultOAuth2User linkAccountToOrg(String orgId, String providerStr, OAuth2User oauthUser, String accessToken) {
        OAuthProvider provider = OAuthProvider.fromString(providerStr);
        if (provider == null) throw new IllegalArgumentException("Unsupported OAuth provider: " + providerStr);

        if (provider == OAuthProvider.DISCORD || provider == OAuthProvider.GOOGLE) {
            throw new IllegalArgumentException("Organizations cannot link " + providerStr + " accounts.");
        }

        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        String providerId = extractProviderId(providerStr, oauthUser);
        String username = extractUsername(providerStr, oauthUser);
        String profileUrl = extractProfileUrl(providerStr, oauthUser, username, providerId);
        boolean isVisible = true;

        Optional<User> conflict = userRepository.findByConnectedAccountsProviderAndProviderId(provider, providerId);
        if (conflict.isPresent() && !conflict.get().getId().equals(orgId)) {
            throw new IllegalArgumentException("This account is already linked to another user or organization.");
        }

        if (provider == OAuthProvider.GITHUB) org.setGithubAccessToken(accessToken);
        if (provider == OAuthProvider.GITLAB) org.setGitlabAccessToken(accessToken);

        updateConnectedAccount(org, provider, providerId, username, profileUrl, isVisible);
        userRepository.save(org);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", org.getUsername());
        attributes.put("id", org.getId());
        attributes.put("is_linking", true);
        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }

    private void updateConnectedAccount(User user, OAuthProvider provider, String providerId, String username, String profileUrl, boolean visible) {
        if (user.getConnectedAccounts() == null) user.setConnectedAccounts(new ArrayList<>());
        user.getConnectedAccounts().removeIf(a -> a.getProvider() == provider);
        user.getConnectedAccounts().add(new User.ConnectedAccount(provider, providerId, username, profileUrl, visible));
    }

    private String extractProviderId(String provider, OAuth2User user) {
        if ("twitter".equals(provider)) {
            Map<String, Object> data = user.getAttribute("data");
            if (data != null) return String.valueOf(data.get("id"));
        }
        if ("bluesky".equals(provider)) return user.getAttribute("did");
        if ("google".equals(provider)) return user.getAttribute("sub");
        Object id = user.getAttribute("id");
        return id != null ? String.valueOf(id) : user.getName();
    }

    private String extractUsername(String provider, OAuth2User user) {
        if ("twitter".equals(provider)) {
            Map<String, Object> data = user.getAttribute("data");
            if (data != null) return (String) data.get("username");
        }
        if ("bluesky".equals(provider)) return user.getAttribute("handle");
        if ("discord".equals(provider)) {
            String name = user.getAttribute("username");
            String discriminator = user.getAttribute("discriminator");
            return (discriminator != null && !discriminator.equals("0")) ? name + "#" + discriminator : name;
        }
        if ("google".equals(provider)) {
            String name = user.getAttribute("name");
            if (name == null) name = user.getAttribute("email");
            if (name == null) name = "User";
            return name.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        }
        String login = user.getAttribute("login");
        String uname = user.getAttribute("username");
        return login != null ? login : (uname != null ? uname : user.getName());
    }

    private String extractProfileUrl(String provider, OAuth2User user, String username, String providerId) {
        if ("discord".equals(provider)) return "https://discord.com/users/" + providerId;
        if ("gitlab".equals(provider)) return user.getAttribute("web_url");
        if ("twitter".equals(provider)) return "https://twitter.com/" + username;
        if ("github".equals(provider)) return "https://github.com/" + username;
        if ("bluesky".equals(provider)) return "https://bsky.app/profile/" + username;
        return "";
    }

    private String extractAvatarUrl(String provider, OAuth2User user, String providerId) {
        if ("discord".equals(provider)) {
            String avatar = user.getAttribute("avatar");
            if (avatar != null) return "https://cdn.discordapp.com/avatars/" + providerId + "/" + avatar + ".png";
        }
        if ("bluesky".equals(provider)) return user.getAttribute("avatar");
        if ("google".equals(provider)) return user.getAttribute("picture");
        return user.getAttribute("avatar_url");
    }
}
