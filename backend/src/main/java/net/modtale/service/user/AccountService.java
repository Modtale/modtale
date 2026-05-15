package net.modtale.service.user;

import net.modtale.model.project.Project;
import net.modtale.model.user.Notification;
import net.modtale.model.user.User;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.security.SanitizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private TrackingService trackingService;
    @Autowired private SanitizationService sanitizer;

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

    public List<User> searchUsers(String query) {
        if (query == null || query.length() < 2) return new ArrayList<>();
        Query dbQuery = new Query(Criteria.where("username").regex(query, "i").and("deletedAt").is(null)).limit(10);
        dbQuery.fields().include("username", "avatarUrl", "accountType");
        return mongoTemplate.find(dbQuery, User.class);
    }

    public User getPublicProfile(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isDeleted()) return null;
        return user;
    }

    public List<User> getPublicProfilesByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return new ArrayList<>();
        Query query = new Query(Criteria.where("_id").in(userIds).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "accountType", "badges", "id", "roles", "tier");
        return mongoTemplate.find(query, User.class);
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
                    Project.class
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

        boolean isTargetLinked = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider().equals(provider));

        if (isTargetLinked && !user.getHasPassword()) {
            long remainingAuthMethods = user.getConnectedAccounts().stream()
                    .filter(a -> !a.getProvider().equals(provider))
                    .count();

            if (remainingAuthMethods == 0) {
                throw new IllegalArgumentException("You must have at least one connected account or a password to sign in.");
            }
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

    public void deleteUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setDeletedAt(LocalDateTime.now());

        apiKeyRepository.deleteByUserId(userId);
        user.setGithubAccessToken(null);
        user.setGitlabAccessToken(null);
        user.setGitlabRefreshToken(null);

        userRepository.save(user);

        if (user.getAccountType() == User.AccountType.ORGANIZATION) trackingService.logDeletedOrg(user.getId());
        else trackingService.logDeletedUser(user.getId());

        logger.info("Soft deleted user account: " + user.getUsername() + " (" + userId + ")");
    }

    public void recoverUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isDeleted()) return;
        user.setDeletedAt(null);
        userRepository.save(user);

        if (user.getAccountType() == User.AccountType.ORGANIZATION) trackingService.logNewOrg(user.getId());
        else trackingService.logNewUser(user.getId());

        logger.info("Recovered user account: " + user.getUsername() + " (" + userId + ")");
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
}