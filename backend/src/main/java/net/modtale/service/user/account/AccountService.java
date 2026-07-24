package net.modtale.service.user.account;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.exception.InvalidAccountRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.validation.SanitizationService;
import net.modtale.service.user.connection.ConnectedAccountMutationService;
import net.modtale.util.MongoIdUtils;
import net.modtale.validation.AccountNameRules;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final SanitizationService sanitizer;
    private final CurrentUserResolutionService currentUserResolutionService;
    private final OAuthAvatarHealingService oauthAvatarHealingService;
    private final AccountLifecycleService accountLifecycleService;
    private final ConnectedAccountMutationService connectedAccountMutationService;

    public AccountService(
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            SanitizationService sanitizer,
            CurrentUserResolutionService currentUserResolutionService,
            OAuthAvatarHealingService oauthAvatarHealingService,
            AccountLifecycleService accountLifecycleService,
            ConnectedAccountMutationService connectedAccountMutationService
    ) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.sanitizer = sanitizer;
        this.currentUserResolutionService = currentUserResolutionService;
        this.oauthAvatarHealingService = oauthAvatarHealingService;
        this.accountLifecycleService = accountLifecycleService;
        this.connectedAccountMutationService = connectedAccountMutationService;
    }

    public User getCurrentUser() {
        return getCurrentUser(SecurityContextHolder.getContext().getAuthentication());
    }

    public User getCurrentUser(Authentication authentication) {
        return currentUserResolutionService.resolveCurrentUser(authentication);
    }

    public User requireCurrentUser(String actionDescription) {
        return requireCurrentUser(SecurityContextHolder.getContext().getAuthentication(), actionDescription);
    }

    public User requireCurrentUser(Authentication authentication, String actionDescription) {
        return currentUserResolutionService.requireCurrentUser(authentication, actionDescription);
    }

    public List<User> searchUsers(String query) {
        if (query == null || query.length() < 2) {
            return new ArrayList<>();
        }
        Query dbQuery = new Query(Criteria.where("username").regex(query, "i").and("deletedAt").is(null)).limit(10);
        dbQuery.fields().include("username", "avatarUrl", "accountType");
        return mongoTemplate.find(dbQuery, User.class);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public User getPublicProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        User user = userRepository.findByUsernameIgnoreCase(userId).orElse(null);
        if (user == null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && userId.contains("~")) {
            String legacyHandle = userId.substring(0, userId.lastIndexOf('~'));
            String legacyId = userId.substring(userId.lastIndexOf('~') + 1);
            user = userRepository.findById(legacyId).orElse(null);
            if (user == null) {
                user = userRepository.findByUsernameIgnoreCase(legacyHandle).orElse(null);
            }
        }
        if (user == null || user.isDeleted()) {
            return null;
        }
        oauthAvatarHealingService.maybeHealOAuthAvatar(user);
        return user;
    }

    public List<User> getPublicProfilesByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(userIds)).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "accountType", "badges", "id", "roles", "tier");
        List<User> users = mongoTemplate.find(query, User.class);
        users.forEach(oauthAvatarHealingService::maybeHealOAuthAvatar);
        return users;
    }

    public User updateUserProfile(String userId, String bio, String newUsername) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (bio != null) {
            user.setBio(sanitizer.sanitizePlainText(bio));
        }

        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            AccountNameRules.validateUsernameUpdate(newUsername);

            Optional<User> conflict = userRepository.findByUsernameIgnoreCase(newUsername);
            if (conflict.isPresent() && !conflict.get().getId().equals(user.getId())) {
                throw new InvalidAccountRequestException("Username is already taken.");
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
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setAvatarUrl(url);
        userRepository.save(user);
    }

    public void updateUserBanner(String userId, String url) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setBannerUrl(url);
        userRepository.save(user);
    }

    public void updateNotificationPreferences(String userId, User.NotificationPreferences prefs) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setNotificationPreferences(prefs);
        userRepository.save(user);
    }

    public void toggleConnectionVisibility(String userId, String provider) {
        if ("google".equalsIgnoreCase(provider) || "hytale".equalsIgnoreCase(provider)) {
            throw new InvalidAccountRequestException(provider + " accounts cannot be made visible on public profiles.");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        OAuthProvider targetProvider = OAuthProvider.fromString(provider);
        if (connectedAccountMutationService.toggleVisibility(user, targetProvider)) {
            userRepository.save(user);
        }
    }

    public void unlinkAccount(String userId, String provider) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        OAuthProvider targetProvider = OAuthProvider.fromString(provider);

        boolean isTargetLinked = user.getConnectedAccounts().stream().anyMatch(a -> a.getProvider() == targetProvider);
        if (isTargetLinked && !user.getHasPassword()) {
            long remainingAuthMethods = user.getConnectedAccounts().stream()
                    .filter(a -> a.getProvider() != targetProvider)
                    .count();

            if (remainingAuthMethods == 0) {
                throw new InvalidAccountRequestException("You must have at least one connected account or a password to sign in.");
            }
        }

        if (connectedAccountMutationService.unlink(user, targetProvider)) {
            userRepository.save(user);
        }
    }

    public void updateProviderTokens(String userId, String provider, String accessToken, String refreshToken, LocalDateTime expiresAt) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        OAuthProvider targetProvider = OAuthProvider.fromString(provider);
        if (targetProvider != null
                && connectedAccountMutationService.updateProviderTokens(user, targetProvider, accessToken, refreshToken, expiresAt)) {
            userRepository.save(user);
        }
    }

    public void deleteUser(String userId) {
        accountLifecycleService.deleteUser(userId);
    }

    public void recoverUser(String userId) {
        accountLifecycleService.recoverUser(userId);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupDeletedUsers() {
        accountLifecycleService.cleanupDeletedUsers();
    }
}
