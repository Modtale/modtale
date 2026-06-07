package net.modtale.service.user;

import net.modtale.model.project.Project;
import net.modtale.model.user.Notification;
import net.modtale.model.user.OAuthProvider;
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
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private TrackingService trackingService;
    @Autowired private SanitizationService sanitizer;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, LocalDateTime> avatarHealCooldown = new ConcurrentHashMap<>();

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
        if (userId == null || userId.isBlank()) return null;
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isDeleted()) return null;
        if (user != null) {
            maybeHealOAuthAvatar(user);
        }
        return user;
    }

    public List<User> getPublicProfilesByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return new ArrayList<>();
        Query query = new Query(Criteria.where("_id").in(userIds).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "accountType", "badges", "id", "roles", "tier");
        List<User> users = mongoTemplate.find(query, User.class);
        users.forEach(this::maybeHealOAuthAvatar);
        return users;
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
        if ("google".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Google accounts cannot be made visible on public profiles.");
        }
        User user = userRepository.findById(userId).orElseThrow();
        OAuthProvider targetProvider = OAuthProvider.fromString(provider);

        user.getConnectedAccounts().stream()
                .filter(a -> a.getProvider() == targetProvider)
                .findFirst()
                .ifPresent(a -> a.setVisible(!a.isVisible()));
        userRepository.save(user);
    }

    public void unlinkAccount(String userId, String provider) {
        User user = userRepository.findById(userId).orElseThrow();
        OAuthProvider targetProvider = OAuthProvider.fromString(provider);

        boolean isTargetLinked = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == targetProvider);

        if (isTargetLinked && !user.getHasPassword()) {
            long remainingAuthMethods = user.getConnectedAccounts().stream()
                    .filter(a -> a.getProvider() != targetProvider)
                    .count();

            if (remainingAuthMethods == 0) {
                throw new IllegalArgumentException("You must have at least one connected account or a password to sign in.");
            }
        }

        boolean removed = user.getConnectedAccounts().removeIf(a -> a.getProvider() == targetProvider);
        if (removed) {
            if ("github".equalsIgnoreCase(provider)) user.setGithubAccessToken(null);
            if ("gitlab".equalsIgnoreCase(provider)) {
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

        if ("gitlab".equalsIgnoreCase(provider)) {
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

    private void maybeHealOAuthAvatar(User user) {
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) return;
        if (user.getConnectedAccounts() == null || user.getConnectedAccounts().isEmpty()) return;
        if (!isOAuthManagedAvatar(user)) return;

        LocalDateTime lastAttempt = avatarHealCooldown.get(user.getId());
        if (lastAttempt != null && lastAttempt.isAfter(LocalDateTime.now().minusMinutes(30))) return;
        avatarHealCooldown.put(user.getId(), LocalDateTime.now());

        if (isImageUrlReachable(user.getAvatarUrl())) return;

        String refreshed = refreshAvatarFromLinkedProvider(user);
        if (refreshed != null && !refreshed.isBlank() && isImageUrlReachable(refreshed)) {
            user.setAvatarUrl(refreshed);
            userRepository.save(user);
            logger.info("Healed broken provider avatar for user {} using linked provider.", user.getId());
            return;
        }

        user.setAvatarUrl("https://ui-avatars.com/api/?name=" + user.getUsername() + "&background=random");
        userRepository.save(user);
        logger.warn("Reset broken provider avatar to default for user {}", user.getId());
    }

    private boolean isOAuthManagedAvatar(User user) {
        String avatarUrl = user.getAvatarUrl();
        if (avatarUrl == null) return false;

        boolean hasDiscord = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.DISCORD);
        boolean hasGithub = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.GITHUB);
        boolean hasGitlab = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.GITLAB);
        boolean hasGoogle = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.GOOGLE);
        boolean hasTwitter = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.TWITTER);
        boolean hasBluesky = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.BLUESKY);

        return (hasDiscord && avatarUrl.contains("cdn.discordapp.com/avatars/")) ||
                (hasGithub && (avatarUrl.contains("githubusercontent.com") || avatarUrl.contains("avatars.githubusercontent.com"))) ||
                (hasGitlab && avatarUrl.contains("gitlab")) ||
                (hasGoogle && (avatarUrl.contains("googleusercontent.com") || avatarUrl.contains("googleapis.com"))) ||
                (hasTwitter && (avatarUrl.contains("twimg.com") || avatarUrl.contains("twitter.com"))) ||
                (hasBluesky && avatarUrl.contains("bsky"));
    }

    private boolean isImageUrlReachable(String rawUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "ModtaleAvatarHealth/1.0");
            int status = connection.getResponseCode();
            return status >= 200 && status < 400;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String refreshAvatarFromLinkedProvider(User user) {
        for (User.ConnectedAccount account : user.getConnectedAccounts()) {
            if (account == null || account.getProvider() == null) continue;
            try {
                switch (account.getProvider()) {
                    case GITHUB -> {
                        String url = refreshGithubAvatar(user, account);
                        if (url != null) return url;
                    }
                    case GITLAB -> {
                        String url = refreshGitlabAvatar(user, account);
                        if (url != null) return url;
                    }
                    case DISCORD -> {
                        String url = resolveDiscordDefaultAvatar(account.getProviderId());
                        if (url != null) return url;
                    }
                    default -> {
                        // No reliable server-side refresh path for this provider without stored tokens.
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String refreshGithubAvatar(User user, User.ConnectedAccount account) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (user.getGithubAccessToken() != null && !user.getGithubAccessToken().isBlank()) {
            headers.setBearerAuth(user.getGithubAccessToken());
            ResponseEntity<Map> me = restTemplate.exchange("https://api.github.com/user", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (me.getStatusCode().is2xxSuccessful() && me.getBody() != null) {
                Object avatar = me.getBody().get("avatar_url");
                if (avatar instanceof String s && !s.isBlank()) return s;
            }
        }

        if (account.getUsername() != null && !account.getUsername().isBlank()) {
            ResponseEntity<Map> publicProfile = restTemplate.getForEntity("https://api.github.com/users/" + account.getUsername(), Map.class);
            if (publicProfile.getStatusCode().is2xxSuccessful() && publicProfile.getBody() != null) {
                Object avatar = publicProfile.getBody().get("avatar_url");
                if (avatar instanceof String s && !s.isBlank()) return s;
            }
        }
        return null;
    }

    private String refreshGitlabAvatar(User user, User.ConnectedAccount account) {
        if (user.getGitlabAccessToken() != null && !user.getGitlabAccessToken().isBlank()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(user.getGitlabAccessToken());
            ResponseEntity<Map> me = restTemplate.exchange("https://gitlab.com/api/v4/user", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (me.getStatusCode().is2xxSuccessful() && me.getBody() != null) {
                Object avatar = me.getBody().get("avatar_url");
                if (avatar instanceof String s && !s.isBlank()) return s;
            }
        }

        if (account.getUsername() != null && !account.getUsername().isBlank()) {
            ResponseEntity<List> users = restTemplate.getForEntity("https://gitlab.com/api/v4/users?username=" + account.getUsername(), List.class);
            if (users.getStatusCode().is2xxSuccessful() && users.getBody() != null && !users.getBody().isEmpty()) {
                Object first = users.getBody().get(0);
                if (first instanceof Map<?, ?> firstMap) {
                    Object avatar = firstMap.get("avatar_url");
                    if (avatar instanceof String s && !s.isBlank()) return s;
                }
            }
        }
        return null;
    }

    private String resolveDiscordDefaultAvatar(String providerId) {
        if (providerId == null || providerId.isBlank()) return null;
        try {
            long id = Long.parseLong(providerId);
            int index = (int) ((id >> 22) % 6);
            return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
