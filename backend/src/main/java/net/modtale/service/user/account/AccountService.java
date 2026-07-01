package net.modtale.service.user.account;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.exception.InvalidAccountRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.user.LauncherSettingsSnapshot;
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

    private static final int MAX_LAUNCHER_SYNC_PROJECTS = 500;
    private static final int MAX_LAUNCHER_SYNC_LIST_ITEMS = 64;
    private static final int MAX_LAUNCHER_SYNC_STRING = 512;
    private static final int MAX_LAUNCHER_SYNC_HASH = 128;

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

    public LauncherSettingsSnapshot getLauncherSettings(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return user.getLauncherSettings() == null ? new LauncherSettingsSnapshot() : user.getLauncherSettings();
    }

    public LauncherSettingsSnapshot updateLauncherSettings(String userId, LauncherSettingsSnapshot snapshot) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        LauncherSettingsSnapshot normalized = normalizeLauncherSettings(snapshot);
        user.setLauncherSettings(normalized);
        userRepository.save(user);
        return normalized;
    }

    public LauncherSettingsSnapshot updateLauncherSettingsPreferences(String userId, LauncherSettingsSnapshot snapshot) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        LauncherSettingsSnapshot normalized = normalizeLauncherSettingsPreferences(snapshot, user.getLauncherSettings());
        user.setLauncherSettings(normalized);
        userRepository.save(user);
        return normalized;
    }

    public void toggleConnectionVisibility(String userId, String provider) {
        if ("google".equalsIgnoreCase(provider)) {
            throw new InvalidAccountRequestException("Google accounts cannot be made visible on public profiles.");
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

    private LauncherSettingsSnapshot normalizeLauncherSettings(LauncherSettingsSnapshot snapshot) {
        LauncherSettingsSnapshot source = snapshot == null ? new LauncherSettingsSnapshot() : snapshot;
        LauncherSettingsSnapshot normalized = new LauncherSettingsSnapshot();
        normalized.setSchemaVersion(source.getSchemaVersion());
        normalized.setSettingsHash(limit(source.getSettingsHash(), MAX_LAUNCHER_SYNC_HASH));
        normalized.setUpdatedAt(LocalDateTime.now().toString());
        normalized.setPreferences(normalizeLauncherPreferences(source.getPreferences()));
        normalized.setInstalledProjects(normalizeInstalledProjects(source.getInstalledProjects()));
        return normalized;
    }

    private LauncherSettingsSnapshot normalizeLauncherSettingsPreferences(
            LauncherSettingsSnapshot snapshot,
            LauncherSettingsSnapshot existing
    ) {
        LauncherSettingsSnapshot source = snapshot == null ? new LauncherSettingsSnapshot() : snapshot;
        LauncherSettingsSnapshot stored = existing == null ? new LauncherSettingsSnapshot() : existing;
        LauncherSettingsSnapshot normalized = new LauncherSettingsSnapshot();
        normalized.setSchemaVersion(source.getSchemaVersion());
        normalized.setSettingsHash(limit(source.getSettingsHash(), MAX_LAUNCHER_SYNC_HASH));
        normalized.setUpdatedAt(LocalDateTime.now().toString());
        normalized.setPreferences(normalizeLauncherPreferences(source.getPreferences()));
        normalized.setInstalledProjects(normalizeInstalledProjects(stored.getInstalledProjects()));
        return normalized;
    }

    private LauncherSettingsSnapshot.Preferences normalizeLauncherPreferences(LauncherSettingsSnapshot.Preferences source) {
        LauncherSettingsSnapshot.Preferences preferences = new LauncherSettingsSnapshot.Preferences();
        if (source == null) {
            return preferences;
        }
        preferences.setHytaleModsPath(limit(source.getHytaleModsPath(), MAX_LAUNCHER_SYNC_STRING));
        preferences.setHytaleGamePath(limit(source.getHytaleGamePath(), MAX_LAUNCHER_SYNC_STRING));
        preferences.setHytaleUserDataPath(limit(source.getHytaleUserDataPath(), MAX_LAUNCHER_SYNC_STRING));
        preferences.setHytaleJavaPath(limit(source.getHytaleJavaPath(), MAX_LAUNCHER_SYNC_STRING));
        preferences.setHytaleBranch(limit(source.getHytaleBranch(), MAX_LAUNCHER_SYNC_STRING));
        preferences.setHytaleBuild(source.getHytaleBuild());
        preferences.setGameVersion(limit(source.getGameVersion(), MAX_LAUNCHER_SYNC_STRING));
        preferences.setIncludeDependencies(source.isIncludeDependencies());
        preferences.setIncludeOptionalDependencies(source.isIncludeOptionalDependencies());
        preferences.setAutoCheckUpdates(source.isAutoCheckUpdates());
        preferences.setLauncherAutoUpdates(source.isLauncherAutoUpdates());
        return preferences;
    }

    private List<LauncherSettingsSnapshot.InstalledProject> normalizeInstalledProjects(
            List<LauncherSettingsSnapshot.InstalledProject> source
    ) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<LauncherSettingsSnapshot.InstalledProject> normalized = new ArrayList<>();
        for (LauncherSettingsSnapshot.InstalledProject project : source) {
            if (project == null || isBlank(project.getProjectId()) || normalized.size() >= MAX_LAUNCHER_SYNC_PROJECTS) {
                continue;
            }
            LauncherSettingsSnapshot.InstalledProject copy = new LauncherSettingsSnapshot.InstalledProject();
            copy.setProjectId(limit(project.getProjectId(), MAX_LAUNCHER_SYNC_STRING));
            copy.setSlug(limit(project.getSlug(), MAX_LAUNCHER_SYNC_STRING));
            copy.setTitle(limit(project.getTitle(), MAX_LAUNCHER_SYNC_STRING));
            copy.setClassification(limit(project.getClassification(), MAX_LAUNCHER_SYNC_STRING));
            copy.setInstalledVersion(limit(project.getInstalledVersion(), MAX_LAUNCHER_SYNC_STRING));
            copy.setInstalledVersionId(limit(project.getInstalledVersionId(), MAX_LAUNCHER_SYNC_STRING));
            copy.setGameVersion(limit(project.getGameVersion(), MAX_LAUNCHER_SYNC_STRING));
            copy.setSource(defaultValue(limit(project.getSource(), MAX_LAUNCHER_SYNC_STRING), "MODTALE"));
            copy.setInstallType(defaultValue(limit(project.getInstallType(), MAX_LAUNCHER_SYNC_STRING), "DIRECT"));
            copy.setModpackUnlocked(project.isModpackUnlocked());
            copy.setDependencyProjectIds(normalizeStringList(project.getDependencyProjectIds()));
            copy.setExternalDependencies(normalizeStringList(project.getExternalDependencies()));
            copy.setBundledProjects(normalizeInstalledProjectReferences(project.getBundledProjects()));
            normalized.add(copy);
        }
        return normalized;
    }

    private List<LauncherSettingsSnapshot.InstalledProjectReference> normalizeInstalledProjectReferences(
            List<LauncherSettingsSnapshot.InstalledProjectReference> source
    ) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<LauncherSettingsSnapshot.InstalledProjectReference> normalized = new ArrayList<>();
        for (LauncherSettingsSnapshot.InstalledProjectReference reference : source) {
            if (reference == null || normalized.size() >= MAX_LAUNCHER_SYNC_LIST_ITEMS) {
                continue;
            }
            LauncherSettingsSnapshot.InstalledProjectReference copy =
                    new LauncherSettingsSnapshot.InstalledProjectReference();
            copy.setId(limit(reference.getId(), MAX_LAUNCHER_SYNC_STRING));
            copy.setProjectId(limit(reference.getProjectId(), MAX_LAUNCHER_SYNC_STRING));
            copy.setSlug(limit(reference.getSlug(), MAX_LAUNCHER_SYNC_STRING));
            copy.setTitle(limit(reference.getTitle(), MAX_LAUNCHER_SYNC_STRING));
            copy.setClassification(limit(reference.getClassification(), MAX_LAUNCHER_SYNC_STRING));
            copy.setVersionNumber(limit(reference.getVersionNumber(), MAX_LAUNCHER_SYNC_STRING));
            copy.setDependencyType(limit(reference.getDependencyType(), MAX_LAUNCHER_SYNC_STRING));
            copy.setSource(limit(reference.getSource(), MAX_LAUNCHER_SYNC_STRING));
            copy.setExternalId(limit(reference.getExternalId(), MAX_LAUNCHER_SYNC_STRING));
            copy.setExternalUrl(limit(reference.getExternalUrl(), MAX_LAUNCHER_SYNC_STRING));
            copy.setExternalFileUrl(limit(reference.getExternalFileUrl(), MAX_LAUNCHER_SYNC_STRING));
            copy.setExternalFileName(limit(reference.getExternalFileName(), MAX_LAUNCHER_SYNC_STRING));
            copy.setCachedFileUrl(limit(reference.getCachedFileUrl(), MAX_LAUNCHER_SYNC_STRING));
            copy.setIcon(limit(reference.getIcon(), MAX_LAUNCHER_SYNC_STRING));
            copy.setOptional(reference.getOptional());
            copy.setEmbedded(reference.getEmbedded());
            normalized.add(copy);
        }
        return normalized;
    }

    private List<String> normalizeStringList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : source) {
            String next = limit(value, MAX_LAUNCHER_SYNC_STRING);
            if (!next.isBlank() && !normalized.contains(next)) {
                normalized.add(next);
            }
            if (normalized.size() >= MAX_LAUNCHER_SYNC_LIST_ITEMS) {
                break;
            }
        }
        return normalized;
    }

    private static String limit(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupDeletedUsers() {
        accountLifecycleService.cleanupDeletedUsers();
    }
}
