package net.modtale.service.user;

import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuthAvatarHealingService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthAvatarHealingService.class);

    private final UserRepository userRepository;
    private final RestClient restClient;
    private final Map<String, LocalDateTime> avatarHealCooldown = new ConcurrentHashMap<>();

    public OAuthAvatarHealingService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.restClient = RestClient.create();
    }

    public void maybeHealOAuthAvatar(User user) {
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            return;
        }
        if (user.getConnectedAccounts() == null || user.getConnectedAccounts().isEmpty()) {
            return;
        }
        if (!isOAuthManagedAvatar(user)) {
            return;
        }

        LocalDateTime lastAttempt = avatarHealCooldown.get(user.getId());
        if (lastAttempt != null && lastAttempt.isAfter(LocalDateTime.now().minusMinutes(30))) {
            return;
        }
        avatarHealCooldown.put(user.getId(), LocalDateTime.now());

        if (isImageUrlReachable(user.getAvatarUrl())) {
            return;
        }

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
        if (avatarUrl == null) {
            return false;
        }

        boolean hasDiscord = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.DISCORD);
        boolean hasGithub = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.GITHUB);
        boolean hasGitlab = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.GITLAB);
        boolean hasGoogle = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.GOOGLE);
        boolean hasTwitter = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.TWITTER);
        boolean hasBluesky = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == OAuthProvider.BLUESKY);

        return (hasDiscord && avatarUrl.contains("cdn.discordapp.com/avatars/"))
                || (hasGithub && (avatarUrl.contains("githubusercontent.com") || avatarUrl.contains("avatars.githubusercontent.com")))
                || (hasGitlab && avatarUrl.contains("gitlab"))
                || (hasGoogle && (avatarUrl.contains("googleusercontent.com") || avatarUrl.contains("googleapis.com")))
                || (hasTwitter && (avatarUrl.contains("twimg.com") || avatarUrl.contains("twitter.com")))
                || (hasBluesky && avatarUrl.contains("bsky"));
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
        } catch (Exception ex) {
            logger.debug("Avatar URL health check failed for {}", rawUrl, ex);
            return false;
        }
    }

    private String refreshAvatarFromLinkedProvider(User user) {
        for (User.ConnectedAccount account : user.getConnectedAccounts()) {
            if (account == null || account.getProvider() == null) {
                continue;
            }
            try {
                switch (account.getProvider()) {
                    case GITHUB -> {
                        String url = refreshGithubAvatar(user, account);
                        if (url != null) {
                            return url;
                        }
                    }
                    case GITLAB -> {
                        String url = refreshGitlabAvatar(user, account);
                        if (url != null) {
                            return url;
                        }
                    }
                    case DISCORD -> {
                        String url = resolveDiscordDefaultAvatar(account.getProviderId());
                        if (url != null) {
                            return url;
                        }
                    }
                    default -> {
                        // No reliable server-side refresh path for this provider without stored tokens.
                    }
                }
            } catch (RuntimeException ex) {
                logger.debug("Failed to refresh avatar from provider {} for user {}", account.getProvider(), user.getId(), ex);
            }
        }
        return null;
    }

    private String refreshGithubAvatar(User user, User.ConnectedAccount account) {
        if (user.getGithubAccessToken() != null && !user.getGithubAccessToken().isBlank()) {
            Map<?, ?> me = restClient.get()
                    .uri("https://api.github.com/user")
                    .headers(headers -> headers.setBearerAuth(user.getGithubAccessToken()))
                    .retrieve()
                    .body(Map.class);
            if (me != null) {
                Object avatar = me.get("avatar_url");
                if (avatar instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
        }

        if (account.getUsername() != null && !account.getUsername().isBlank()) {
            Map<?, ?> publicProfile = restClient.get()
                    .uri("https://api.github.com/users/{username}", account.getUsername())
                    .retrieve()
                    .body(Map.class);
            if (publicProfile != null) {
                Object avatar = publicProfile.get("avatar_url");
                if (avatar instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
        }

        return null;
    }

    private String refreshGitlabAvatar(User user, User.ConnectedAccount account) {
        if (user.getGitlabAccessToken() != null && !user.getGitlabAccessToken().isBlank()) {
            Map<?, ?> me = restClient.get()
                    .uri("https://gitlab.com/api/v4/user")
                    .headers(headers -> headers.setBearerAuth(user.getGitlabAccessToken()))
                    .retrieve()
                    .body(Map.class);
            if (me != null) {
                Object avatar = me.get("avatar_url");
                if (avatar instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
        }

        if (account.getUsername() != null && !account.getUsername().isBlank()) {
            List<?> users = restClient.get()
                    .uri("https://gitlab.com/api/v4/users?username={username}", account.getUsername())
                    .retrieve()
                    .body(List.class);
            if (users != null && !users.isEmpty()) {
                Object first = users.getFirst();
                if (first instanceof Map<?, ?> firstMap) {
                    Object avatar = firstMap.get("avatar_url");
                    if (avatar instanceof String value && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }

        return null;
    }

    private String resolveDiscordDefaultAvatar(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }

        try {
            long id = Long.parseLong(providerId);
            int index = (int) ((id >> 22) % 6);
            return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
        } catch (NumberFormatException ex) {
            logger.debug("Unable to derive Discord default avatar for provider id {}", providerId, ex);
            return null;
        }
    }
}
