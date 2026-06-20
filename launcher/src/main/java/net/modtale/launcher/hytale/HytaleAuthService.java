package net.modtale.launcher.hytale;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;

public class HytaleAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long LOGIN_TIMEOUT_MINUTES = 15;
    private static final Pattern JWT_EXP_PATTERN = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");
    private static final Pattern GAME_VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*$");

    private final HytaleApiClient apiClient;
    private final SettingsStore settingsStore;

    public HytaleAuthService(HytaleApiClient apiClient, SettingsStore settingsStore) {
        this.apiClient = apiClient;
        this.settingsStore = settingsStore;
    }

    public HytaleAuthSession loginAndSave(LauncherSettings settings) {
        OAuthGrant grant = requestAuthorizationCode();
        HytaleApiClient.TokenResponse token = apiClient.exchangeCode(grant.code(), grant.codeVerifier());
        List<HytaleProfile> profiles = apiClient.fetchProfiles(token.accessToken);
        HytaleProfile profile = profiles.getFirst();
        HytaleGameSession gameSession = apiClient.createGameSession(token.accessToken, profile.uuid());
        if (!gameSession.hasLaunchTokens()) {
            throw new HytaleApiException("Hytale did not return launch session tokens. The game was not launched.");
        }

        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken(token.accessToken);
        session.setRefreshToken(token.refreshToken);
        session.setExpiresAt(expiresAt(token.expiresIn));
        session.setUsername(profile.username());
        session.setUuid(profile.uuid());
        session.setAccountOwnerId(profile.owner());
        session.setProfiles(profiles);
        session.setSessionToken(gameSession.sessionToken());
        session.setIdentityToken(gameSession.identityToken());
        settings.upsertHytaleAuthSession(session);
        settingsStore.save(settings);
        return session;
    }

    public void selectAccount(LauncherSettings settings, String accountId) {
        if (settings == null || accountId == null || accountId.isBlank()) {
            return;
        }
        settings.selectHytaleAccount(accountId);
        settingsStore.save(settings);
    }

    public void selectProfile(LauncherSettings settings, HytaleProfile selectedProfile) {
        if (settings == null || selectedProfile == null || selectedProfile.uuid().isBlank()) {
            return;
        }
        HytaleAuthSession session = settings.getHytaleAuthSession();
        if (session == null) {
            throw new HytaleApiException("Sign in with Hytale before choosing a profile.");
        }
        HytaleProfile profile = session.getProfiles().stream()
                .filter(candidate -> selectedProfile.uuid().equals(candidate.uuid()))
                .findFirst()
                .orElse(selectedProfile);
        session.setUsername(profile.username());
        session.setUuid(profile.uuid());
        settings.upsertHytaleAuthSession(session);
        settingsStore.save(settings);
    }

    public void logout(LauncherSettings settings) {
        settings.removeActiveHytaleAuthSession();
        settingsStore.save(settings);
    }

    public void logoutAccount(LauncherSettings settings, String accountId) {
        if (settings == null || accountId == null || accountId.isBlank()) {
            return;
        }
        settings.removeHytaleAuthSession(accountId);
        settingsStore.save(settings);
    }

    public List<HytaleVersion> getAvailableVersions(LauncherSettings settings, String branch) {
        HytaleAuthSession session = ensureValidAccessToken(settings);
        try {
            return HytaleGameVersionResolver.labelVersions(settings, branch,
                    apiClient.getAvailableVersions(session.getAccessToken(), branch));
        } catch (HytaleApiException ex) {
            if (!ex.isAuthFailure()) {
                throw ex;
            }
            session = refresh(settings, session);
            return HytaleGameVersionResolver.labelVersions(settings, branch,
                    apiClient.getAvailableVersions(session.getAccessToken(), branch));
        }
    }

    public List<String> getAvailablePatchlines(LauncherSettings settings) {
        HytaleAuthSession session = ensureValidAccessToken(settings);
        List<String> candidates = previousPatchlineCandidates(settings);
        try {
            return apiClient.getAvailablePatchlines(session.getAccessToken(), candidates);
        } catch (HytaleApiException ex) {
            if (!ex.isAuthFailure()) {
                throw ex;
            }
            session = refresh(settings, session);
            return apiClient.getAvailablePatchlines(session.getAccessToken(), candidates);
        }
    }

    public List<HytaleFriend> getFriends(LauncherSettings settings) {
        HytaleAuthSession cachedSession = settings.getHytaleAuthSession();
        if (canUseCachedFriendsSession(cachedSession)) {
            try {
                return enrichFriendsWithPublicProfiles(
                        cachedSession.getSessionToken(),
                        apiClient.fetchFriends(cachedSession.getSessionToken())
                );
            } catch (HytaleApiException ex) {
                if (!ex.isAuthFailure()) {
                    throw ex;
                }
            }
        }

        HytaleAuthSession session = ensureValidAccessToken(settings);
        try {
            return fetchFriendsWithFreshGameSession(settings, session);
        } catch (HytaleApiException ex) {
            if (!ex.isAuthFailure()) {
                throw ex;
            }
            session = refresh(settings, session);
            return fetchFriendsWithFreshGameSession(settings, session);
        }
    }

    public long getProfilePlaytimeSeconds(LauncherSettings settings) {
        HytaleAuthSession session = ensureValidAccessToken(settings);
        try {
            return refreshProfilesAndGetPlaytime(settings, session);
        } catch (HytaleApiException ex) {
            if (!ex.isAuthFailure()) {
                throw ex;
            }
            session = refresh(settings, session);
            return refreshProfilesAndGetPlaytime(settings, session);
        }
    }

    public List<HytaleBlogPost> getBlogPosts(int count) {
        return apiClient.getBlogPosts(count);
    }

    public List<HytaleBlogPost> getAllBlogPosts() {
        return apiClient.getAllBlogPosts();
    }

    public HytaleAuthSession ensureFreshSessionForLaunch(LauncherSettings settings) {
        HytaleAuthSession existingSession = settings.getHytaleAuthSession();
        if (existingSession == null || !existingSession.hasRefreshToken()) {
            throw new HytaleApiException("Sign in with Hytale before launching or loading Hytale versions.");
        }
        try {
            HytaleAuthSession session = ensureValidAccessToken(settings);
            HytaleGameSession gameSession = createGameSessionWithRefresh(settings, session);
            if (!gameSession.hasLaunchTokens()) {
                throw new HytaleApiException("Hytale did not return launch session tokens. The game was not launched.");
            }
            return saveGameSession(settings, session, gameSession);
        } catch (HytaleApiException ex) {
            HytaleAuthSession cachedSession = settings.getHytaleAuthSession();
            if (canUseCachedLaunchSession(cachedSession, ex)) {
                return cachedSession;
            }
            throw ex;
        }
    }

    private List<HytaleFriend> fetchFriendsWithFreshGameSession(LauncherSettings settings, HytaleAuthSession session) {
        HytaleGameSession gameSession = createGameSessionWithRefresh(settings, session);
        if (!gameSession.hasLaunchTokens()) {
            throw new HytaleApiException("Hytale did not return social session tokens.");
        }
        HytaleAuthSession updatedSession = saveGameSession(settings, session, gameSession);
        return enrichFriendsWithPublicProfiles(
                updatedSession.getSessionToken(),
                apiClient.fetchFriends(updatedSession.getSessionToken())
        );
    }

    private long refreshProfilesAndGetPlaytime(LauncherSettings settings, HytaleAuthSession session) {
        List<HytaleProfile> profiles = apiClient.fetchProfiles(session.getAccessToken());
        String selectedUuid = session.getUuid();
        HytaleProfile selectedProfile = profiles.stream()
                .filter(profile -> profile.uuid().equals(selectedUuid))
                .findFirst()
                .orElseGet(() -> profiles.isEmpty() ? null : profiles.getFirst());
        session.setProfiles(profiles);
        if (selectedProfile != null) {
            session.setUsername(selectedProfile.username());
            session.setUuid(selectedProfile.uuid());
            session.setAccountOwnerId(selectedProfile.owner());
        }
        settings.upsertHytaleAuthSession(session);
        settingsStore.save(settings);
        return selectedProfile == null ? 0 : selectedProfile.playtimeSeconds();
    }

    static List<String> previousPatchlineCandidates(LauncherSettings settings) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (settings != null) {
            HytaleApiClient.normalizePatchlineId(settings.getHytaleBranch())
                    .filter(patchline -> patchline.startsWith("v"))
                    .ifPresent(candidates::add);
        }
        HytaleGameVersionResolver.resolveBuildVersions(settings).values().stream()
                .map(HytaleAuthService::parseGameVersion)
                .flatMap(Optional::stream)
                .max(GameVersion::compareTo)
                .flatMap(GameVersion::previousPatchline)
                .ifPresent(candidates::add);
        return List.copyOf(candidates);
    }

    private static Optional<GameVersion> parseGameVersion(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = GAME_VERSION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new GameVersion(
                parseVersionPart(matcher.group(1)),
                parseVersionPart(matcher.group(2)),
                parseVersionPart(matcher.group(3))
        ));
    }

    private static int parseVersionPart(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<HytaleFriend> enrichFriendsWithPublicProfiles(String sessionToken, List<HytaleFriend> friends) {
        if (friends == null || friends.isEmpty()) {
            return List.of();
        }
        List<String> unresolvedUuids = friends.stream()
                .filter(friend -> friend.username().isBlank() && !friend.uuid().isBlank())
                .map(HytaleFriend::uuid)
                .toList();
        if (unresolvedUuids.isEmpty()) {
            return friends;
        }
        Map<String, String> usernamesByUuid = apiClient.fetchPublicProfileUsernames(sessionToken, unresolvedUuids);
        if (usernamesByUuid.isEmpty()) {
            return friends;
        }
        return friends.stream()
                .map(friend -> {
                    if (!friend.username().isBlank() || friend.uuid().isBlank()) {
                        return friend;
                    }
                    String username = usernamesByUuid.get(friend.uuid().toLowerCase(Locale.ROOT));
                    return username == null || username.isBlank() ? friend : friend.withUsername(username);
                })
                .toList();
    }

    private HytaleAuthSession saveGameSession(
            LauncherSettings settings,
            HytaleAuthSession session,
            HytaleGameSession gameSession
    ) {
        session.setSessionToken(gameSession.sessionToken());
        session.setIdentityToken(gameSession.identityToken());
        settings.upsertHytaleAuthSession(session);
        settingsStore.save(settings);
        return session;
    }

    private HytaleGameSession createGameSessionWithRefresh(LauncherSettings settings, HytaleAuthSession session) {
        try {
            return apiClient.createGameSession(session.getAccessToken(), session.getUuid());
        } catch (HytaleApiException ex) {
            if (!ex.isAuthFailure()) {
                throw ex;
            }
            HytaleAuthSession refreshed = refresh(settings, session);
            return apiClient.createGameSession(refreshed.getAccessToken(), refreshed.getUuid());
        }
    }

    private HytaleAuthSession ensureValidAccessToken(LauncherSettings settings) {
        HytaleAuthSession session = settings.getHytaleAuthSession();
        if (session == null || !session.hasRefreshToken()) {
            throw new HytaleApiException("Sign in with Hytale before launching or loading Hytale versions.");
        }
        if (!session.hasAccessToken() || session.getExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
            return refresh(settings, session);
        }
        return session;
    }

    private HytaleAuthSession refresh(LauncherSettings settings, HytaleAuthSession session) {
        try {
            HytaleApiClient.TokenResponse token = apiClient.refreshToken(session.getRefreshToken());
            session.setAccessToken(token.accessToken);
            if (token.refreshToken != null && !token.refreshToken.isBlank()) {
                session.setRefreshToken(token.refreshToken);
            }
            session.setExpiresAt(expiresAt(token.expiresIn));
            settings.upsertHytaleAuthSession(session);
            settingsStore.save(settings);
            return session;
        } catch (HytaleApiException ex) {
            if (ex.requiresSignIn()) {
                removeExpiredSession(settings, session);
            }
            throw ex;
        }
    }

    private void removeExpiredSession(LauncherSettings settings, HytaleAuthSession session) {
        String accountId = LauncherSettings.hytaleAccountId(session);
        if (accountId.isBlank()) {
            settings.removeActiveHytaleAuthSession();
        } else {
            settings.removeHytaleAuthSession(accountId);
        }
        settingsStore.save(settings);
    }

    private static boolean canUseCachedLaunchSession(HytaleAuthSession session, HytaleApiException failure) {
        return session != null
                && session.hasRefreshToken()
                && session.hasLaunchTokens()
                && permitsCachedLaunchSession(failure);
    }

    private static boolean permitsCachedLaunchSession(HytaleApiException failure) {
        return failure != null
                && (failure.isAuthFailure() || failure.statusCode() < 0 || failure.statusCode() >= 500);
    }

    private static boolean canUseCachedFriendsSession(HytaleAuthSession session) {
        return session != null
                && session.hasLaunchTokens()
                && jwtExpiresAfter(session.getSessionToken(), Instant.now().plusSeconds(60));
    }

    private static boolean jwtExpiresAfter(String token, Instant threshold) {
        if (token == null || token.isBlank() || threshold == null) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(padBase64Url(parts[1])), StandardCharsets.UTF_8);
            Matcher matcher = JWT_EXP_PATTERN.matcher(payload);
            if (!matcher.find()) {
                return false;
            }
            return Instant.ofEpochSecond(Long.parseLong(matcher.group(1))).isAfter(threshold);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String padBase64Url(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return value + "=".repeat(padding);
    }

    private OAuthGrant requestAuthorizationCode() {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = codeChallenge(codeVerifier);
        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
            int port = server.getAddress().getPort();
            OAuthState state = stateForPort(port);
            server.createContext("/", exchange -> handleCallback(exchange, codeFuture, state));
            server.start();

            URI authUri = URI.create(HytaleApiClient.AUTH_URL
                    + "?access_type=offline"
                    + "&client_id=" + encode(HytaleApiClient.CLIENT_ID)
                    + "&code_challenge=" + encode(codeChallenge)
                    + "&code_challenge_method=S256"
                    + "&redirect_uri=" + encode(HytaleApiClient.REDIRECT_URI)
                    + "&response_type=code"
                    + "&scope=" + encode(HytaleApiClient.SCOPES)
                    + "&state=" + encode(state.encodedState()));
            openBrowser(authUri);
            return new OAuthGrant(codeFuture.get(LOGIN_TIMEOUT_MINUTES, TimeUnit.MINUTES), codeVerifier);
        } catch (TimeoutException ex) {
            throw new HytaleApiException("Hytale sign-in timed out. Please try again.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HytaleApiException("Hytale sign-in was interrupted.", ex);
        } catch (Exception ex) {
            if (ex instanceof HytaleApiException hytaleEx) {
                throw hytaleEx;
            }
            throw new HytaleApiException("Hytale sign-in failed: " + ex.getMessage(), ex);
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private void handleCallback(HttpExchange exchange, CompletableFuture<String> codeFuture, OAuthState expectedState) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String code = query.get("code");
        String error = query.get("error");
        String returnedState = query.get("state");

        String html;
        if (error != null && !error.isBlank()) {
            codeFuture.completeExceptionally(new HytaleApiException("Hytale authorization failed: " + error));
            html = page("Authorization failed", "Return to Modtale and try signing in again.");
        } else if (code != null && !code.isBlank()) {
            if (expectedState.matches(returnedState)) {
                codeFuture.complete(code);
                html = page("Authorization successful", "You can close this window and return to Modtale.");
            } else {
                codeFuture.completeExceptionally(new HytaleApiException("Hytale authorization returned an unexpected state."));
                html = page("Authorization failed", "Return to Modtale and try signing in again.");
            }
        } else {
            html = page("Waiting for authorization", "Return to the Hytale authorization page to finish signing in.");
        }

        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void openBrowser(URI uri) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new HytaleApiException("Desktop browser integration is not available. Could not open Hytale sign-in.");
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException ex) {
            throw new HytaleApiException("Could not open Hytale sign-in in your browser.", ex);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String page(String title, String message) {
        return """
                <html>
                  <head><title>%s</title><script>setTimeout(function(){ window.close(); }, 1500);</script></head>
                  <body><h1>%s</h1><p>%s</p></body>
                </html>
                """.formatted(title, title, message);
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    private static String codeChallenge(String verifier) {
        try {
            return base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new HytaleApiException("SHA-256 is not available for Hytale sign-in.", ex);
        }
    }

    static OAuthState stateForPort(int port) {
        String callbackState = randomBase32(26);
        String json = "{\"state\":\"" + callbackState + "\",\"port\":\"" + port + "\"}";
        String encodedState = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return new OAuthState(encodedState, callbackState, port);
    }

    private static String randomBase32(int length) {
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        char[] result = new char[length];
        for (int index = 0; index < length; index++) {
            result[index] = chars[Byte.toUnsignedInt(bytes[index]) % chars.length];
        }
        return new String(result);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static Instant expiresAt(long expiresInSeconds) {
        long seconds = expiresInSeconds <= 0 ? 3600 : expiresInSeconds;
        return Instant.now().plusSeconds(seconds);
    }

    private record OAuthGrant(String code, String codeVerifier) {
    }

    private record GameVersion(int major, int minor, int patch) implements Comparable<GameVersion> {

        private Optional<String> previousPatchline() {
            if (minor <= 0) {
                return Optional.empty();
            }
            return Optional.of("v" + major + "." + (minor - 1));
        }

        @Override
        public int compareTo(GameVersion other) {
            int majorCompare = Integer.compare(major, other.major);
            if (majorCompare != 0) {
                return majorCompare;
            }
            int minorCompare = Integer.compare(minor, other.minor);
            if (minorCompare != 0) {
                return minorCompare;
            }
            return Integer.compare(patch, other.patch);
        }
    }

    record OAuthState(String encodedState, String callbackState, int port) {

        boolean matches(String returnedState) {
            return returnedState != null && !returnedState.isBlank()
                    && (returnedState.equals(callbackState) || returnedState.equals(encodedState));
        }
    }
}
