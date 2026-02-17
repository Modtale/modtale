package net.modtale.service.user;

import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.resources.Mod;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.BannedEmail;
import net.modtale.model.user.User;
import net.modtale.model.user.Notification;
import net.modtale.repository.user.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.NotificationRepository;
import net.modtale.service.AnalyticsService;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.security.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
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
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    @Autowired private UserRepository userRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private BannedEmailRepository bannedEmailRepository;
    @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private SanitizationService sanitizer;
    @Autowired private NotificationService notificationService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;

    @Value("${app.security.pre-auth-secret:default-secret-change-in-prod}")
    private String preAuthSigningKey;

    @Value("${app.security.pre-auth-expiry-seconds:600}")
    private long preAuthExpirySeconds;

    @Value("${app.limits.max-orgs-per-user:5}")
    private int maxOrgsPerUser;

    public User registerUser(String username, String email, String password) {
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

        try {
            emailService.sendVerificationEmail(email, username, savedUser.getVerificationToken());
        } catch (Exception e) {
            logger.error("Failed to send verification email during registration", e);
        }

        return savedUser;
    }

    public User authenticate(String login, String password) {
        User user = userRepository.findByUsernameIgnoreCase(login)
                .or(() -> userRepository.findByEmail(login))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (user.isDeleted()) {
            throw new IllegalArgumentException("Account deleted.");
        }

        if (bannedEmailRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new SecurityException("This account has been suspended.");
        }

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

        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("This email address is not allowed.");
        }

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
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) return;

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            return;
        }

        User user = userOpt.get();
        if (user.isDeleted() || (user.getPassword() == null && user.getConnectedAccounts().isEmpty())) {
            return;
        }

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

    public List<User> searchUsers(String query) {
        if (query == null || query.length() < 2) return new ArrayList<>();
        Query dbQuery = new Query(Criteria.where("username").regex(query, "i").and("deletedAt").is(null)).limit(10);
        dbQuery.fields().include("username", "avatarUrl", "accountType");
        return mongoTemplate.find(dbQuery, User.class);
    }

    public User getPublicProfile(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user != null && user.isDeleted()) return null;
        return user;
    }

    public List<User> getPublicProfilesByUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) return new ArrayList<>();

        Query query = new Query(Criteria.where("username").in(usernames).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "accountType", "badges", "id", "roles", "tier");
        return mongoTemplate.find(query, User.class);
    }

    public void deleteUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setDeletedAt(LocalDateTime.now());

        apiKeyRepository.deleteByUserId(userId);
        user.setGithubAccessToken(null);
        user.setGitlabAccessToken(null);
        user.setGitlabRefreshToken(null);

        userRepository.save(user);
        logger.info("Soft deleted user account: " + user.getUsername() + " (" + userId + ")");
    }

    public void recoverUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isDeleted()) return;
        user.setDeletedAt(null);
        userRepository.save(user);
        logger.info("Recovered user account: " + user.getUsername() + " (" + userId + ")");
    }

    private void performHardDelete(User user) {
        mongoTemplate.remove(new Query(Criteria.where("userId").is(user.getId())), Notification.class);

        mongoTemplate.updateMulti(
                new Query(Criteria.where("followerIds").is(user.getId())),
                new Update().pull("followerIds", user.getId()),
                User.class
        );
        mongoTemplate.updateMulti(
                new Query(Criteria.where("followingIds").is(user.getId())),
                new Update().pull("followingIds", user.getId()),
                User.class
        );

        userRepository.deleteById(user.getId());
        logger.info("Permanently deleted user account: " + user.getUsername() + " (" + user.getId() + ")");
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupDeletedUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<User> expiredUsers = userRepository.findByDeletedAtBefore(cutoff);

        for (User user : expiredUsers) {
            try {
                performHardDelete(user);
            } catch (Exception e) {
                logger.error("Failed to hard delete user " + user.getId(), e);
            }
        }
    }

    public void banEmail(String email, String reason, String bannedBy) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already banned.");
        }

        bannedEmailRepository.save(new BannedEmail(email, reason, bannedBy));

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (!user.isDeleted()) {
                deleteUser(user.getId());
                logger.info("Automatically deleted user " + user.getUsername() + " due to email ban on " + email);
            }
        }
    }

    public void unbanEmail(String email) {
        bannedEmailRepository.findByEmailIgnoreCase(email).ifPresent(bannedEmailRepository::delete);
    }

    public List<BannedEmail> getBannedEmails() {
        return bannedEmailRepository.findAll(Sort.by(Sort.Direction.DESC, "bannedAt"));
    }

    public User createOrganization(String name, User owner) {
        String cleanName = name.trim();

        if (userRepository.existsByUsernameIgnoreCase(cleanName)) {
            throw new IllegalArgumentException("A user or organization with this name already exists.");
        }

        if (!cleanName.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Organization name contains invalid characters.");
        }

        List<User> myOrgs = getUserOrganizations(owner.getId());
        long adminOrgCount = myOrgs.stream()
                .filter(o -> o.getOrganizationMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(owner.getId()) && "ADMIN".equals(m.getRole())))
                .count();

        if (adminOrgCount >= maxOrgsPerUser) {
            throw new IllegalStateException("You have reached the limit of " + maxOrgsPerUser + " organizations.");
        }

        User org = new User();
        org.setUsername(cleanName);
        org.setAccountType(User.AccountType.ORGANIZATION);
        org.setCreatedAt(LocalDate.now().toString());
        org.setTier(owner.getTier());
        org.setAvatarUrl("https://ui-avatars.com/api/?name=" + cleanName + "&background=random");

        List<User.OrganizationMember> members = new ArrayList<>();
        members.add(new User.OrganizationMember(owner.getId(), "ADMIN"));
        org.setOrganizationMembers(members);

        return userRepository.save(org);
    }

    public User updateOrganization(String orgId, String newName, String bio, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) throw new SecurityException("Insufficient permissions.");

        if (newName != null && !newName.isBlank() && !newName.equals(org.getUsername())) {
            Optional<User> existing = userRepository.findByUsernameIgnoreCase(newName);
            if (existing.isPresent() && !existing.get().getId().equals(orgId)) {
                throw new IllegalArgumentException("Name already taken.");
            }

            if (!newName.matches("^[a-zA-Z0-9_.-]+$")) {
                throw new IllegalArgumentException("Name contains invalid characters.");
            }
            org.setUsername(newName);
        }

        if (bio != null) org.setBio(sanitizer.sanitizePlainText(bio));

        return userRepository.save(org);
    }

    public void inviteOrganizationMember(String orgId, String targetUsername, String role, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (org.getAccountType() != User.AccountType.ORGANIZATION) throw new IllegalArgumentException("Target is not an organization");

        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) throw new SecurityException("Only organization admins can invite members.");

        User target = userRepository.findByUsernameIgnoreCase(targetUsername).orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (org.getOrganizationMembers().stream().anyMatch(m -> m.getUserId().equals(target.getId()))) {
            throw new IllegalArgumentException("User is already a member.");
        }

        if (org.getPendingOrgInvites() != null && org.getPendingOrgInvites().stream().anyMatch(m -> m.getUserId().equals(target.getId()))) {
            throw new IllegalArgumentException("User has already been invited.");
        }

        if (org.getPendingOrgInvites() == null) org.setPendingOrgInvites(new ArrayList<>());
        org.getPendingOrgInvites().add(new User.OrganizationMember(target.getId(), role));
        userRepository.save(org);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("orgId", org.getId());
        metadata.put("action", "ORG_INVITE");

        notificationService.sendActionableNotification(
                List.of(target.getId()),
                "Organization Invite",
                "You have been invited to join " + org.getUsername() + " as a " + role.toLowerCase() + ".",
                "/dashboard/orgs",
                org.getAvatarUrl(),
                "ORG_INVITE",
                metadata
        );
    }

    public void resolveOrgInvite(String orgId, boolean accept, User responder) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        User.OrganizationMember invite = null;
        if (org.getPendingOrgInvites() != null) {
            invite = org.getPendingOrgInvites().stream()
                    .filter(m -> m.getUserId().equals(responder.getId()))
                    .findFirst()
                    .orElse(null);
        }

        if (invite == null) throw new IllegalArgumentException("No pending invite found for this organization.");

        if (accept) {
            org.getOrganizationMembers().add(invite);
            org.getPendingOrgInvites().remove(invite);
            userRepository.save(org);

            String msg = responder.getUsername() + " accepted the invitation to join " + org.getUsername();
            org.getOrganizationMembers().stream()
                    .filter(m -> "ADMIN".equals(m.getRole()) && !m.getUserId().equals(responder.getId()))
                    .forEach(admin -> notificationService.sendNotification(
                            List.of(admin.getUserId()),
                            "Invite Accepted",
                            msg,
                            "/dashboard/orgs",
                            responder.getAvatarUrl()
                    ));
        } else {
            org.getPendingOrgInvites().remove(invite);
            userRepository.save(org);
        }
    }

    public void voidOrgInvite(String orgId, String userId) {
        userRepository.findById(orgId).ifPresent(org -> {
            if (org.getPendingOrgInvites() != null) {
                boolean removed = org.getPendingOrgInvites().removeIf(m -> m.getUserId().equals(userId));
                if (removed) userRepository.save(org);
            }
        });
    }

    public void updateOrganizationMemberRole(String orgId, String targetUserId, String newRole, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        boolean isRequesterAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isRequesterAdmin) throw new SecurityException("Insufficient permissions.");

        if (!"ADMIN".equals(newRole) && !"MEMBER".equals(newRole)) {
            throw new IllegalArgumentException("Invalid role. Must be ADMIN or MEMBER.");
        }

        User.OrganizationMember targetMember = org.getOrganizationMembers().stream()
                .filter(m -> m.getUserId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization."));

        if ("MEMBER".equals(newRole) && targetUserId.equals(requester.getId())) {
            long adminCount = org.getOrganizationMembers().stream().filter(m -> "ADMIN".equals(m.getRole())).count();
            if (adminCount <= 1) {
                throw new IllegalArgumentException("You cannot demote yourself as the only Admin.");
            }
        }

        targetMember.setRole(newRole);
        userRepository.save(org);

        String roleName = "ADMIN".equals(newRole) ? "an Admin" : "a Member";
        notificationService.sendNotification(
                List.of(targetUserId),
                "Role Updated",
                "Your role in " + org.getUsername() + " has been updated to " + roleName + ".",
                "/dashboard/orgs",
                org.getAvatarUrl()
        );
    }

    public void removeOrganizationMember(String orgId, String targetUserId, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));

        if (!isAdmin && !requester.getId().equals(targetUserId)) {
            throw new SecurityException("Insufficient permissions.");
        }

        if (org.getOrganizationMembers().size() <= 1) {
            throw new IllegalArgumentException("Cannot remove the last member. Delete the organization instead.");
        }

        boolean removed = org.getOrganizationMembers().removeIf(m -> m.getUserId().equals(targetUserId));
        if (removed) userRepository.save(org);
    }

    public void updateOrganizationAvatar(String orgId, String url, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) throw new SecurityException("Insufficient permissions.");

        org.setAvatarUrl(url);
        userRepository.save(org);
    }

    public void updateOrganizationBanner(String orgId, String url, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) throw new SecurityException("Insufficient permissions.");

        org.setBannerUrl(url);
        userRepository.save(org);
    }

    public void deleteOrganization(String orgId, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (org.getAccountType() != User.AccountType.ORGANIZATION) throw new IllegalArgumentException("Target is not an organization");

        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) throw new SecurityException("Insufficient permissions.");

        deleteUser(orgId);
    }

    public List<User> getOrganizationMembers(String username) {
        User org = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (org.getOrganizationMembers() == null || org.getOrganizationMembers().isEmpty()) return new ArrayList<>();

        if (org.isDeleted()) return new ArrayList<>();

        List<String> memberIds = org.getOrganizationMembers().stream()
                .map(User.OrganizationMember::getUserId)
                .collect(Collectors.toList());

        Query query = new Query(Criteria.where("_id").in(memberIds).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id", "bio");
        return mongoTemplate.find(query, User.class);
    }

    public List<User> getUserOrganizations(String userId) {
        List<User> orgs = userRepository.findOrganizationsByMemberId(userId);
        return orgs.stream().filter(o -> !o.isDeleted()).collect(Collectors.toList());
    }

    public List<User> getUserOrganizationsByUsername(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null) return new ArrayList<>();

        List<User> orgs = userRepository.findOrganizationsByMemberId(user.getId());
        return orgs.stream()
                .filter(o -> !o.isDeleted())
                .peek(o -> {
                    o.setEmail(null);
                    o.setGithubAccessToken(null);
                    o.setGitlabAccessToken(null);
                })
                .collect(Collectors.toList());
    }

    public DefaultOAuth2User linkAccountToOrg(String orgId, String provider, OAuth2User oauthUser, String accessToken) {
        if ("discord".equalsIgnoreCase(provider) || "google".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Organizations cannot link " + provider + " accounts.");
        }

        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        String providerId = extractProviderId(provider, oauthUser);
        String username = extractUsername(provider, oauthUser);
        String profileUrl = extractProfileUrl(provider, oauthUser, username, providerId);
        boolean isVisible = true;

        Optional<User> conflict = userRepository.findByConnectedAccountsProviderId(providerId);
        if (conflict.isPresent() && !conflict.get().getId().equals(orgId)) {
            throw new IllegalArgumentException("This account is already linked to another user or organization.");
        }

        if ("github".equals(provider)) org.setGithubAccessToken(accessToken);
        if ("gitlab".equals(provider)) org.setGitlabAccessToken(accessToken);

        updateConnectedAccount(org, provider, providerId, username, profileUrl, isVisible);
        userRepository.save(org);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", org.getUsername());
        attributes.put("id", org.getId());
        attributes.put("is_linking", true);
        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }

    public void unlinkOrgAccount(String orgId, String provider, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) throw new SecurityException("Insufficient permissions.");

        boolean removed = org.getConnectedAccounts().removeIf(a -> a.getProvider().equals(provider));
        if (removed) {
            if ("github".equals(provider)) org.setGithubAccessToken(null);
            if ("gitlab".equals(provider)) {
                org.setGitlabAccessToken(null);
                org.setGitlabRefreshToken(null);
                org.setGitlabTokenExpiresAt(null);
            }
            userRepository.save(org);
        }
    }

    public void toggleOrgConnectionVisibility(String orgId, String provider, User requester) {
        if ("google".equals(provider)) throw new IllegalArgumentException("Google accounts cannot be made visible.");

        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        boolean isAdmin = org.getOrganizationMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requester.getId()) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) throw new SecurityException("Insufficient permissions.");

        org.getConnectedAccounts().stream()
                .filter(a -> a.getProvider().equals(provider))
                .findFirst()
                .ifPresent(a -> a.setVisible(!a.isVisible()));
        userRepository.save(org);
    }

    public DefaultOAuth2User processUserLogin(String provider, OAuth2User oauthUser, String accessToken) {
        String providerId = extractProviderId(provider, oauthUser);

        String oauthUsername = extractUsername(provider, oauthUser);
        String oauthAvatar = extractAvatarUrl(provider, oauthUser, providerId);

        String email = oauthUser.getAttribute("email");
        String profileUrl = extractProfileUrl(provider, oauthUser, oauthUsername, providerId);

        boolean isVisible = !"google".equals(provider);
        User user = null;

        if (email != null && bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Account suspended.");
        }

        Optional<User> linkedUser = userRepository.findByConnectedAccountsProviderId(providerId);
        if (linkedUser.isPresent()) {
            user = linkedUser.get();
        } else if (email != null && !email.isEmpty()) {
            Optional<User> emailUser = userRepository.findByEmail(email);
            if (emailUser.isPresent()) {
                user = emailUser.get();
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

            if ("github".equals(provider)) user.setGithubAccessToken(accessToken);
            if ("gitlab".equals(provider)) user.setGitlabAccessToken(accessToken);

            updateConnectedAccount(user, provider, providerId, oauthUsername, profileUrl, isVisible);
            userRepository.save(user);
        }
        else {
            isNewUser = true;

            user = new User();
            user.setEmail(email);
            user.setCreatedAt(LocalDate.now().toString());
            user.setEmailVerified(true);

            if ("google".equalsIgnoreCase(provider)) {
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

            if ("github".equals(provider)) user.setGithubAccessToken(accessToken);
            if ("gitlab".equals(provider)) user.setGitlabAccessToken(accessToken);

            updateConnectedAccount(user, provider, providerId, oauthUsername, profileUrl, isVisible);
            userRepository.save(user);
        }

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("login", user.getUsername());
        attributes.put("id", user.getId());
        attributes.remove("is_linking");

        if (isNewUser && "google".equalsIgnoreCase(provider)) {
            attributes.put("is_new_account", true);
            if (oauthUsername != null) attributes.put("suggested_username", oauthUsername);
            if (oauthAvatar != null) attributes.put("suggested_avatar", oauthAvatar);
        }

        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }

    public DefaultOAuth2User linkAccount(User currentUser, String provider, OAuth2User oauthUser, String accessToken) {
        String providerId = extractProviderId(provider, oauthUser);
        String username = extractUsername(provider, oauthUser);
        String profileUrl = extractProfileUrl(provider, oauthUser, username, providerId);
        boolean isVisible = !"google".equals(provider);

        Optional<User> conflict = userRepository.findByConnectedAccountsProviderId(providerId);
        if (conflict.isPresent() && !conflict.get().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("This account is already linked to another user.");
        }

        if ("github".equals(provider)) currentUser.setGithubAccessToken(accessToken);
        if ("gitlab".equals(provider)) currentUser.setGitlabAccessToken(accessToken);

        updateConnectedAccount(currentUser, provider, providerId, username, profileUrl, isVisible);
        userRepository.save(currentUser);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", currentUser.getUsername());
        attributes.put("id", currentUser.getId());
        attributes.put("is_linking", true);
        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
    }

    public void updateProviderTokens(String userId, String provider, String accessToken, String refreshToken, LocalDateTime expiresAt) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        if ("gitlab".equals(provider)) {
            user.setGitlabAccessToken(accessToken);
            if (refreshToken != null) user.setGitlabRefreshToken(refreshToken);
            if (expiresAt != null) user.setGitlabTokenExpiresAt(expiresAt);
            userRepository.save(user);
        }
    }

    private String extractProviderId(String provider, OAuth2User user) {
        if ("twitter".equals(provider)) {
            Map<String, Object> data = user.getAttribute("data");
            if (data != null) return String.valueOf(data.get("id"));
        }
        if ("bluesky".equals(provider)) {
            return user.getAttribute("did");
        }
        if ("google".equals(provider)) {
            return user.getAttribute("sub");
        }
        Object id = user.getAttribute("id");
        return id != null ? String.valueOf(id) : user.getName();
    }

    private String extractUsername(String provider, OAuth2User user) {
        if ("twitter".equals(provider)) {
            Map<String, Object> data = user.getAttribute("data");
            if (data != null) return (String) data.get("username");
        }
        if ("bluesky".equals(provider)) {
            return user.getAttribute("handle");
        }
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
        if ("bluesky".equals(provider)) {
            return user.getAttribute("avatar");
        }
        if ("google".equals(provider)) {
            return user.getAttribute("picture");
        }
        return user.getAttribute("avatar_url");
    }

    private void updateConnectedAccount(User user, String provider, String providerId, String username, String profileUrl, boolean visible) {
        if (user.getConnectedAccounts() == null) user.setConnectedAccounts(new ArrayList<>());
        user.getConnectedAccounts().removeIf(a -> a.getProvider().equals(provider));
        user.getConnectedAccounts().add(new User.ConnectedAccount(provider, providerId, username, profileUrl, visible));
    }

    public void toggleConnectionVisibility(String userId, String provider) {
        if ("google".equals(provider)) {
            throw new IllegalArgumentException("Google accounts cannot be made visible on public profiles.");
        }
        User user = userRepository.findById(userId).orElseThrow();
        user.getConnectedAccounts().stream()
                .filter(a -> a.getProvider().equals(provider))
                .findFirst()
                .ifPresent(a -> a.setVisible(!a.isVisible()));
        userRepository.save(user);
    }

    public void unlinkAccount(String userId, String provider) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getConnectedAccounts().size() <= 1 && user.getPassword() == null) {
            throw new IllegalArgumentException("You must have at least one connected account or a password to sign in.");
        }
        boolean removed = user.getConnectedAccounts().removeIf(a -> a.getProvider().equals(provider));
        if (removed) {
            if ("github".equals(provider)) user.setGithubAccessToken(null);
            if ("gitlab".equals(provider)) {
                user.setGitlabAccessToken(null);
                user.setGitlabRefreshToken(null);
                user.setGitlabTokenExpiresAt(null);
            }
            userRepository.save(user);
        }
    }

    public User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            Object principal = auth.getPrincipal();
            String username = null;
            if (principal instanceof User) {
                username = ((User) principal).getUsername();
            } else if (principal instanceof org.springframework.security.core.userdetails.User) {
                username = ((org.springframework.security.core.userdetails.User) principal).getUsername();
            } else if (principal instanceof OAuth2User) {
                username = ((OAuth2User) principal).getAttribute("login");
            }
            if (username != null) {
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null && !user.isDeleted()) return user;
            }
        } catch (Exception e) { logger.error("Error retrieving current user", e); }
        return null;
    }

    public User updateUserProfile(String userId, String bio, String newUsername) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (bio != null) user.setBio(sanitizer.sanitizePlainText(bio));

        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            if (!newUsername.matches("^[a-zA-Z0-9_.-]+$")) {
                throw new IllegalArgumentException("Username can only contain letters, numbers, hyphens, underscores, and periods.");
            }
            if (newUsername.length() < 3 || newUsername.length() > 30) {
                throw new IllegalArgumentException("Username must be between 3 and 30 characters.");
            }

            Optional<User> conflict = userRepository.findByUsernameIgnoreCase(newUsername);
            if (conflict.isPresent() && !conflict.get().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Username is already taken.");
            }

            String oldUsername = user.getUsername();
            user.setUsername(newUsername);

            mongoTemplate.updateMulti(
                    new Query(Criteria.where("author").is(oldUsername)),
                    new Update().set("author", newUsername),
                    Mod.class
            );
        }

        return userRepository.save(user);
    }

    public void updateUserAvatar(String userId, String url) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setAvatarUrl(url);
        userRepository.save(user);
    }

    public void updateUserBanner(String userId, String url) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setBannerUrl(url);
        userRepository.save(user);
    }

    public void updateNotificationPreferences(String userId, User.NotificationPreferences prefs) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setNotificationPreferences(prefs);
        userRepository.save(user);
    }

    public void followUser(String currentUserId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new IllegalArgumentException("Target not found"));
        if (target.getId().equals(currentUserId)) throw new IllegalArgumentException("Cannot follow yourself.");

        if (target.isDeleted()) throw new IllegalArgumentException("Target user not found.");

        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        if (currentUser.getFollowingIds() == null) currentUser.setFollowingIds(new ArrayList<>());
        if (target.getFollowerIds() == null) target.setFollowerIds(new ArrayList<>());

        if (!currentUser.getFollowingIds().contains(target.getId())) {
            currentUser.getFollowingIds().add(target.getId());
            target.getFollowerIds().add(currentUser.getId());

            userRepository.save(currentUser);
            userRepository.save(target);

            if (target.getNotificationPreferences().getNewFollowers() != User.NotificationLevel.OFF) {
                notificationService.sendNotification(
                        List.of(target.getId()),
                        "New Follower",
                        currentUser.getUsername() + " started following you.",
                        "/creator/" + currentUser.getUsername(),
                        currentUser.getAvatarUrl()
                );
            }
        }
    }

    public void unfollowUser(String currentUserId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new IllegalArgumentException("Target not found"));
        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        if (currentUser.getFollowingIds() != null) {
            currentUser.getFollowingIds().remove(target.getId());
            userRepository.save(currentUser);
        }
        if (target.getFollowerIds() != null) {
            target.getFollowerIds().remove(currentUser.getId());
            userRepository.save(target);
        }
    }

    public List<User> getFollowing(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return new ArrayList<>();

        if (userOpt.get().isDeleted()) return new ArrayList<>();

        List<String> ids = userOpt.get().getFollowingIds();
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Query query = new Query(Criteria.where("_id").in(ids).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id");
        return mongoTemplate.find(query, User.class);
    }

    public List<User> getFollowers(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return new ArrayList<>();

        if (userOpt.get().isDeleted()) return new ArrayList<>();

        List<String> ids = userOpt.get().getFollowerIds();
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Query query = new Query(Criteria.where("_id").in(ids).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id");
        return mongoTemplate.find(query, User.class);
    }

    public CreatorAnalytics getCreatorAnalytics(String username) {
        return analyticsService.getCreatorDashboard(username, "30d", null);
    }

    public void setUserTier(String username, ApiKey.Tier newTier) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setTier(newTier);
        userRepository.save(user);
        mongoTemplate.updateMulti(new Query(Criteria.where("userId").is(user.getId())), new Update().set("tier", newTier), ApiKey.class);
    }
}