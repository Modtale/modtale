package net.modtale.launcher.api;

import com.fasterxml.jackson.core.type.TypeReference;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.modtale.launcher.model.auth.SignInResponse;
import net.modtale.launcher.model.notification.LauncherNotification;
import net.modtale.launcher.model.project.DownloadUrlResponse;
import net.modtale.launcher.model.project.GameVersionCatalog;
import net.modtale.launcher.model.project.ProjectComment;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectGallery;
import net.modtale.launcher.model.project.ProjectMeta;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.project.ProjectVersionChangelog;
import net.modtale.launcher.model.project.VersionDependenciesView;
import net.modtale.launcher.model.sync.LauncherSettingsSnapshot;
import net.modtale.launcher.model.user.CreatorProfile;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.model.user.UserSummary;
import net.modtale.launcher.model.worldlist.CreateWorldModListRequest;
import net.modtale.launcher.model.worldlist.WorldModList;

public class ModtaleApiClient {

    public static final String DEFAULT_API_BASE_URL = "https://api.modtale.net/api/v1";
    public static final String DEFAULT_SITE_BASE_URL = "https://modtale.net";

    private final CookieManager cookieManager;
    private final ModtaleApiTransport transport;
    private final ModtaleDownloadClient downloadClient;
    private final LauncherSessionStore sessionStore;
    private volatile URI apiBaseUri;
    private volatile boolean storedSessionLoaded;

    public ModtaleApiClient(String apiBaseUrl) {
        this(apiBaseUrl, null);
    }

    public ModtaleApiClient(String apiBaseUrl, Path sessionPath) {
        this(defaultCookieManager(), apiBaseUrl, new ApiResponseCache(),
                sessionPath == null ? null : new LauncherSessionStore(sessionPath));
    }

    private ModtaleApiClient(
            CookieManager cookieManager,
            String apiBaseUrl,
            ApiResponseCache responseCache,
            LauncherSessionStore sessionStore
    ) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), cookieManager, apiBaseUrl, responseCache, sessionStore);
    }

    ModtaleApiClient(HttpClient httpClient, String apiBaseUrl) {
        this(httpClient, apiBaseUrl, new ApiResponseCache());
    }

    ModtaleApiClient(HttpClient httpClient, String apiBaseUrl, ApiResponseCache responseCache) {
        this(httpClient, null, apiBaseUrl, responseCache, null);
    }

    private ModtaleApiClient(
            HttpClient httpClient,
            CookieManager cookieManager,
            String apiBaseUrl,
            ApiResponseCache responseCache,
            LauncherSessionStore sessionStore
    ) {
        this.cookieManager = cookieManager;
        this.transport = new ModtaleApiTransport(httpClient, responseCache, this::csrfToken);
        this.downloadClient = new ModtaleDownloadClient(httpClient, this::apiBaseUri);
        this.sessionStore = sessionStore;
        configure(apiBaseUrl);
        if (this.sessionStore != null && this.cookieManager != null) {
            storedSessionLoaded = this.sessionStore.loadInto(this.cookieManager.getCookieStore(), apiBaseUri);
        }
    }

    public final void configure(String apiBaseUrl) {
        this.apiBaseUri = ApiPathBuilder.normalizeBaseUri(apiBaseUrl, DEFAULT_API_BASE_URL);
    }

    public URI apiBaseUri() {
        return apiBaseUri;
    }

    public SignInResponse signIn(String username, char[] password) {
        SignInResponse response = post("/auth/signin", java.util.Map.of(
                "username", username == null ? "" : username.trim(),
                "password", password == null ? "" : new String(password)
        ), SignInResponse.class);
        if (response != null && !response.mfaRequired()) {
            saveStoredSession();
        }
        return response;
    }

    public void validateMfa(String preAuthToken, String code) {
        post("/auth/mfa/validate-login", java.util.Map.of(
                "pre_auth_token", preAuthToken == null ? "" : preAuthToken,
                "code", code == null ? "" : code.trim()
        ), Object.class);
        saveStoredSession();
    }

    public void exchangeLauncherCode(String code) {
        post("/auth/launcher/exchange", java.util.Map.of(
                "code", code == null ? "" : code.trim()
        ), Object.class);
        saveStoredSession();
    }

    public CurrentUser currentUser() {
        CurrentUser user = get("/user/me", CurrentUser.class);
        saveStoredSession();
        return user;
    }

    public void logout() {
        try {
            post("/auth/logout", java.util.Map.of(), Object.class);
        } finally {
            clearStoredSession();
        }
    }

    public boolean hasStoredSession() {
        return storedSessionLoaded || (sessionStore != null && sessionStore.hasSessionFile());
    }

    public void clearStoredSession() {
        storedSessionLoaded = false;
        if (sessionStore != null && cookieManager != null) {
            sessionStore.clear(cookieManager.getCookieStore());
        }
    }

    public void clearResponseCache() {
        transport.clearResponseCache();
    }

    public ProjectPage searchProjects(ProjectSearchQuery query) {
        List<String> params = new ArrayList<>();
        addParam(params, "page", Integer.toString(query.page()));
        addParam(params, "size", Integer.toString(query.size()));
        addParam(params, "sort", query.sort());
        addParam(params, "search", query.search());
        addParam(params, "classification", query.classification());
        addParam(params, "gameVersion", query.gameVersion());
        addParam(params, "tags", query.tags());
        if (query.minDownloads() != null) {
            addParam(params, "minDownloads", Integer.toString(query.minDownloads()));
        }
        if (query.minFavorites() != null) {
            addParam(params, "minFavorites", Integer.toString(query.minFavorites()));
        }
        addParam(params, "category", query.category());
        addParam(params, "dateRange", query.dateRange());
        if (Boolean.TRUE.equals(query.openSource())) {
            addParam(params, "openSource", "true");
        }
        return get("/projects" + (params.isEmpty() ? "" : "?" + String.join("&", params)), ProjectPage.class);
    }

    public ProjectDetail getProject(String idOrSlug) {
        return get("/projects/" + encodePath(idOrSlug), ProjectDetail.class);
    }

    public ProjectGallery getProjectGallery(String idOrSlug) {
        ProjectGallery gallery = get("/projects/" + encodePath(idOrSlug) + "/gallery", ProjectGallery.class);
        return gallery == null ? new ProjectGallery(List.of(), java.util.Map.of()) : gallery;
    }

    public ProjectMeta getProjectMeta(String idOrSlug) {
        return get("/projects/" + encodePath(idOrSlug) + "/meta", ProjectMeta.class);
    }

    public Map<String, ProjectMeta> getProjectMetaBatch(List<String> projectIds) {
        List<String> ids = projectIds == null
                ? List.of()
                : projectIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .limit(50)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<String> params = new ArrayList<>();
        addParam(params, "ids", String.join(",", ids));
        return get("/projects/meta?" + String.join("&", params), new TypeReference<>() {});
    }

    public CreatorProfile getUserProfile(String idOrHandle) {
        return get("/user/profile/" + encodePath(idOrHandle), CreatorProfile.class);
    }

    public ProjectPage getCreatorProjects(String userId, int page, int size) {
        List<String> params = new ArrayList<>();
        addParam(params, "page", Integer.toString(Math.max(0, page)));
        addParam(params, "size", Integer.toString(Math.max(1, size)));
        addParam(params, "sort", "relevance");
        return get("/creators/" + encodePath(userId) + "/projects?" + String.join("&", params), ProjectPage.class);
    }

    public List<CreatorProfile> getOrganizationMembers(String organizationId) {
        return get("/orgs/" + encodePath(organizationId) + "/members", new TypeReference<>() {});
    }

    public List<CreatorProfile> getUserOrganizations(String userId) {
        return get("/users/" + encodePath(userId) + "/organizations", new TypeReference<>() {});
    }

    public List<UserSummary> getUsersBatch(List<String> userIds) {
        List<String> ids = userIds == null
                ? List.of()
                : userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return post("/users/batch", java.util.Map.of("userIds", ids), new TypeReference<>() {});
    }

    public List<ProjectComment> getComments(String projectId) {
        CommentsResponse response = get("/projects/" + encodePath(projectId) + "/comments", CommentsResponse.class);
        return response == null ? List.of() : response.comments();
    }

    public void postComment(String projectId, String content) {
        post("/projects/" + encodePath(projectId) + "/comments",
                java.util.Map.of("content", content == null ? "" : content), Object.class);
    }

    public void updateComment(String projectId, String commentId, String content) {
        put("/projects/" + encodePath(projectId) + "/comments/" + encodePath(commentId),
                java.util.Map.of("content", content == null ? "" : content), Object.class);
    }

    public void deleteComment(String projectId, String commentId) {
        delete("/projects/" + encodePath(projectId) + "/comments/" + encodePath(commentId), Object.class);
    }

    public void replyToComment(String projectId, String commentId, String reply) {
        post("/projects/" + encodePath(projectId) + "/comments/" + encodePath(commentId) + "/reply",
                java.util.Map.of("reply", reply == null ? "" : reply), Object.class);
    }

    public void voteComment(String projectId, String commentId, boolean upvote, boolean reply) {
        String target = reply
                ? "/projects/" + encodePath(projectId) + "/comments/" + encodePath(commentId) + "/reply/vote"
                : "/projects/" + encodePath(projectId) + "/comments/" + encodePath(commentId) + "/vote";
        post(target + "?upvote=" + upvote, java.util.Map.of(), Object.class);
    }

    public String submitReport(String targetId, String targetType, String reason, String description) {
        ReportResponse response = post("/reports", java.util.Map.of(
                "targetId", targetId == null ? "" : targetId,
                "targetType", targetType == null ? "" : targetType,
                "reason", reason == null ? "" : reason,
                "description", description == null ? "" : description
        ), ReportResponse.class);
        return response == null ? "" : response.id();
    }

    public List<ProjectVersionChangelog> getProjectVersionChangelogs(String idOrSlug) {
        return get("/projects/" + encodePath(idOrSlug) + "/versions/changelogs", new TypeReference<>() {});
    }

    public List<ProjectVersion> getProjectVersions(String idOrSlug) {
        ProjectVersionsResponse response = get("/projects/" + encodePath(idOrSlug) + "/versions", ProjectVersionsResponse.class);
        return response == null ? List.of() : response.versions();
    }

    public void toggleFavorite(String projectId) {
        post("/projects/" + encodePath(projectId) + "/favorite", java.util.Map.of(), Object.class);
    }

    public List<LauncherNotification> getNotifications() {
        return get("/notifications", new TypeReference<>() {});
    }

    public void markNotificationRead(String notificationId, boolean read) {
        post("/notifications/" + encodePath(notificationId) + "/" + (read ? "read" : "unread"),
                java.util.Map.of(), Object.class);
    }

    public void markAllNotificationsRead() {
        post("/notifications/read-all", java.util.Map.of(), Object.class);
    }

    public void deleteNotification(String notificationId) {
        delete("/notifications/" + encodePath(notificationId), Object.class);
    }

    public void clearNotifications() {
        delete("/notifications/clear-all", Object.class);
    }

    public void updateNotificationPreferences(CurrentUser.NotificationPreferences preferences) {
        put("/user/settings/notifications",
                preferences == null ? CurrentUser.NotificationPreferences.defaults() : preferences,
                Object.class);
    }

    public LauncherSettingsSnapshot getLauncherSettings() {
        return get("/user/launcher-settings", LauncherSettingsSnapshot.class);
    }

    public LauncherSettingsSnapshot updateLauncherSettings(LauncherSettingsSnapshot snapshot) {
        return put("/user/launcher-settings",
                snapshot == null ? new LauncherSettingsSnapshot() : snapshot,
                LauncherSettingsSnapshot.class);
    }

    public LauncherSettingsSnapshot updateLauncherSettingsPreferences(LauncherSettingsSnapshot snapshot) {
        LauncherSettingsSnapshot payload = snapshot == null
                ? new LauncherSettingsSnapshot()
                : snapshot.preferencesOnly();
        return put("/user/launcher-settings/preferences", payload, LauncherSettingsSnapshot.class);
    }

    public List<UserSummary> getFollowing(String userId) {
        return get("/users/" + encodePath(userId) + "/following", new TypeReference<>() {});
    }

    public void followUser(String userId) {
        post("/user/follow/" + encodePath(userId), java.util.Map.of(), Object.class);
    }

    public void unfollowUser(String userId) {
        post("/user/unfollow/" + encodePath(userId), java.util.Map.of(), Object.class);
    }

    public void resolveNotificationAction(LauncherNotification notification, boolean accept) {
        if (notification == null) {
            throw new ModtaleApiException("Notification is missing.");
        }
        LauncherNotification.ActionType actionType = notification.actionType()
                .orElseThrow(() -> new ModtaleApiException("This notification does not have an accept or decline action."));
        java.util.Map<String, String> metadata = notification.metadata();
        switch (actionType) {
            case TRANSFER_REQUEST -> {
                String projectId = metadata.get("modId");
                if (projectId == null || projectId.isBlank()) {
                    throw new ModtaleApiException("Transfer request is missing its project id.");
                }
                post("/projects/" + encodePath(projectId) + "/transfer/resolve",
                        java.util.Map.of("accept", accept), Object.class);
            }
            case ORG_INVITE -> {
                String orgId = metadata.get("orgId");
                if (orgId == null || orgId.isBlank()) {
                    throw new ModtaleApiException("Organization invite is missing its organization id.");
                }
                post("/orgs/" + encodePath(orgId) + "/invite/" + (accept ? "accept" : "decline"),
                        java.util.Map.of(), Object.class);
            }
            case CONTRIBUTOR_INVITE -> {
                String projectId = metadata.get("modId");
                if (projectId == null || projectId.isBlank()) {
                    throw new ModtaleApiException("Contributor invite is missing its project id.");
                }
                post("/projects/" + encodePath(projectId) + "/invite/" + (accept ? "accept" : "decline"),
                        java.util.Map.of(), Object.class);
            }
        }
    }

    public List<String> getGameVersions() {
        return get("/meta/game-versions", new TypeReference<>() {});
    }

    public GameVersionCatalog getGameVersionCatalog() {
        return get("/meta/game-versions/catalog", GameVersionCatalog.class);
    }

    public VersionDependenciesView getDependencies(String projectId, String versionNumber, String gameVersion) {
        String query = gameVersion == null || gameVersion.isBlank()
                ? ""
                : "?gameVersion=" + encodeQuery(gameVersion);
        return get("/projects/" + encodePath(projectId) + "/versions/" + encodePath(versionNumber) + "/dependencies" + query,
                VersionDependenciesView.class);
    }

    public WorldModList createWorldModList(CreateWorldModListRequest request) {
        return post("/lists", request, WorldModList.class);
    }

    public WorldModList getWorldModListForInstall(String listId) {
        return get("/lists/" + encodePath(listId) + "/install", WorldModList.class);
    }

    public DownloadUrlResponse getDownloadUrl(String projectId, String versionNumber, String gameVersion) {
        String query = gameVersion == null || gameVersion.isBlank()
                ? ""
                : "?gameVersion=" + encodeQuery(gameVersion);
        return get("/projects/" + encodePath(projectId) + "/versions/" + encodePath(versionNumber) + "/download-url" + query,
                DownloadUrlResponse.class);
    }

    public DownloadUrlResponse getBundleDownloadUrl(
            String projectId,
            String versionNumber,
            List<String> dependencyProjectIds,
            String gameVersion
    ) {
        List<String> params = new ArrayList<>();
        if (dependencyProjectIds != null && !dependencyProjectIds.isEmpty()) {
            addParam(params, "deps", String.join(",", dependencyProjectIds));
        }
        addParam(params, "gameVersion", gameVersion);
        String query = params.isEmpty() ? "" : "?" + String.join("&", params);
        return get("/projects/" + encodePath(projectId) + "/versions/" + encodePath(versionNumber) + "/download-bundle-url" + query,
                DownloadUrlResponse.class);
    }

    public DownloadedFile download(String rawUrl) {
        return downloadClient.download(rawUrl);
    }

    URI resolveDownloadUri(String rawUrl) {
        return downloadClient.resolve(rawUrl);
    }

    private <T> T get(String pathAndQuery, Class<T> type) {
        return transport.get(apiUri(pathAndQuery), type, ApiCachePolicy.ttlFor(pathAndQuery));
    }

    private <T> T get(String pathAndQuery, TypeReference<T> type) {
        return transport.get(apiUri(pathAndQuery), type, ApiCachePolicy.ttlFor(pathAndQuery));
    }

    private <T> T post(String pathAndQuery, Object payload, Class<T> type) {
        return transport.post(apiUri(pathAndQuery), payload, type);
    }

    private <T> T post(String pathAndQuery, Object payload, TypeReference<T> type) {
        return transport.post(apiUri(pathAndQuery), payload, type);
    }

    private <T> T put(String pathAndQuery, Object payload, Class<T> type) {
        return transport.put(apiUri(pathAndQuery), payload, type);
    }

    private <T> T delete(String pathAndQuery, Class<T> type) {
        return transport.delete(apiUri(pathAndQuery), type);
    }

    private void saveStoredSession() {
        if (sessionStore != null && cookieManager != null) {
            sessionStore.saveFrom(cookieManager.getCookieStore(), apiBaseUri);
            storedSessionLoaded = sessionStore.hasSessionFile();
        }
    }

    private Optional<String> csrfToken() {
        if (cookieManager == null || apiBaseUri == null) {
            return Optional.empty();
        }
        return cookieManager.getCookieStore().get(apiBaseUri).stream()
                .filter(cookie -> "XSRF-TOKEN".equalsIgnoreCase(cookie.getName()))
                .filter(cookie -> !cookie.hasExpired())
                .map(HttpCookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private static CookieManager defaultCookieManager() {
        return new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    }

    private URI apiUri(String pathAndQuery) {
        return ApiPathBuilder.apiUri(apiBaseUri, pathAndQuery);
    }

    private static void addParam(List<String> params, String name, String value) {
        ApiPathBuilder.addParam(params, name, value);
    }

    private static String encodePath(String value) {
        return ApiPathBuilder.encodePath(value);
    }

    private static String encodeQuery(String value) {
        return ApiPathBuilder.encodeQuery(value);
    }

    public record DownloadedFile(Path path, String filename, String contentType) {
    }

    private record ProjectVersionsResponse(List<ProjectVersion> versions) {
        private ProjectVersionsResponse {
            versions = versions == null ? List.of() : List.copyOf(versions);
        }
    }

    private record CommentsResponse(List<ProjectComment> comments) {
        private CommentsResponse {
            comments = comments == null ? List.of() : List.copyOf(comments);
        }
    }

    private record ReportResponse(String id) {
    }

}
