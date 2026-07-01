package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleAuthServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void oauthStateAcceptsHytaleConsentCallbackState() {
        HytaleAuthService.OAuthState state = HytaleAuthService.stateForPort(33241);

        String decoded = new String(Base64.getDecoder().decode(state.encodedState()), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("\"port\":\"33241\""));
        assertTrue(decoded.contains("\"state\":\"" + state.callbackState() + "\""));
        assertTrue(state.matches(state.callbackState()));
        assertTrue(state.matches(state.encodedState()));
        assertFalse(state.matches("RTYGKDL7RCQRKYQS6YOSTAXYES"));
        assertFalse(state.matches(null));
    }

    @Test
    void launchSessionCreationRefreshesAndRetriesOnAuthFailure() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("stale-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        settings.setHytaleAuthSession(session);

        HytaleAuthSession launched = authService.ensureFreshSessionForLaunch(settings);

        assertEquals(2, apiClient.createGameSessionCalls);
        assertEquals(1, apiClient.refreshTokenCalls);
        assertEquals("fresh-access", launched.getAccessToken());
        assertEquals("fresh-identity-token", launched.getIdentityToken());
        assertEquals("fresh-session-token", launched.getSessionToken());
    }

    @Test
    void launchFallsBackToCachedHytaleTokensWhenOfficialAuthIsUnavailable() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        apiClient.createGameSessionFailure = new HytaleApiException("auth offline");
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("cached-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        session.setIdentityToken("cached-identity-token");
        session.setSessionToken("cached-session-token");
        settings.setHytaleAuthSession(session);

        HytaleAuthSession launched = authService.ensureFreshSessionForLaunch(settings);

        assertEquals(1, apiClient.createGameSessionCalls);
        assertEquals("cached-access", launched.getAccessToken());
        assertEquals("cached-identity-token", launched.getIdentityToken());
        assertEquals("cached-session-token", launched.getSessionToken());
    }

    @Test
    void launchDoesNotInventOfflineTokensWhenNoCachedHytaleLaunchSessionExists() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        apiClient.createGameSessionFailure = new HytaleApiException("auth offline");
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("cached-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        settings.setHytaleAuthSession(session);

        assertThrows(HytaleApiException.class, () -> authService.ensureFreshSessionForLaunch(settings));
    }

    @Test
    void friendsUseCachedHytaleSessionTokenWhenStillValid() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        String cachedSessionToken = jwtWithExpiration(Instant.now().plusSeconds(3600));
        session.setAccessToken("cached-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        session.setIdentityToken("cached-identity-token");
        session.setSessionToken(cachedSessionToken);
        settings.setHytaleAuthSession(session);

        List<HytaleFriend> friends = authService.getFriends(settings);

        assertEquals(0, apiClient.createGameSessionCalls);
        assertEquals(1, apiClient.fetchFriendsCalls);
        assertEquals(cachedSessionToken, apiClient.fetchFriendsSessionToken);
        assertEquals("Friend", friends.getFirst().displayName());
    }

    @Test
    void friendsCreateHytaleSessionAndUseSessionTokenForSocialEndpoint() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("valid-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        settings.setHytaleAuthSession(session);

        List<HytaleFriend> friends = authService.getFriends(settings);

        assertEquals(1, apiClient.createGameSessionCalls);
        assertEquals(1, apiClient.fetchFriendsCalls);
        assertEquals("fresh-session-token", apiClient.fetchFriendsSessionToken);
        assertEquals("fresh-session-token", settings.getHytaleAuthSession().getSessionToken());
        assertEquals("fresh-identity-token", settings.getHytaleAuthSession().getIdentityToken());
        assertEquals("Friend", friends.getFirst().displayName());
    }

    @Test
    void friendsResolveUuidOnlyEntriesWithPublicProfiles() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        apiClient.friends = List.of(new HytaleFriend("", "friend-uuid", "Offline", "", false));
        apiClient.publicProfileUsernames = Map.of("friend-uuid", "ResolvedFriend");
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        String cachedSessionToken = jwtWithExpiration(Instant.now().plusSeconds(3600));
        session.setAccessToken("cached-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        session.setIdentityToken("cached-identity-token");
        session.setSessionToken(cachedSessionToken);
        settings.setHytaleAuthSession(session);

        List<HytaleFriend> friends = authService.getFriends(settings);

        assertEquals("ResolvedFriend", friends.getFirst().displayName());
        assertEquals(1, apiClient.fetchPublicProfileUsernamesCalls);
        assertEquals(cachedSessionToken, apiClient.fetchPublicProfileSessionToken);
        assertEquals(List.of("friend-uuid"), apiClient.fetchPublicProfileUuids);
    }

    @Test
    void playtimeRefreshesProfilesFromOfficialLauncherData() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        apiClient.profiles = List.of(
                new HytaleProfile("Player", "player-uuid", "owner-id", 66321),
                new HytaleProfile("Alt", "alt-profile-uuid", "owner-id", 0)
        );
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("valid-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        settings.setHytaleAuthSession(session);

        long playtimeSeconds = authService.getProfilePlaytimeSeconds(settings);

        assertEquals(66321, playtimeSeconds);
        assertEquals(1, apiClient.fetchProfilesCalls);
        assertEquals("valid-access", apiClient.fetchProfilesAccessToken);
        assertEquals(2, settings.getHytaleAuthSession().getProfiles().size());
        assertEquals(66321, settings.getHytaleAuthSession().getProfiles().getFirst().playtimeSeconds());
    }

    @Test
    void previousPatchlineCandidateFollowsLatestObservedGameVersion(@TempDir Path home) throws Exception {
        withPlatform("Linux", home.toString(), () -> {
            Path log = home.resolve(Path.of(".var", "app", "com.hypixel.HytaleLauncher", "data", "Hytale", "hytale-launcher.log"));
            Files.createDirectories(log.getParent());
            Files.writeString(log, """
                    time=2026-06-14T16:05:06.000-04:00 level=INFO msg="versions" releases=[{build:12 version:0.4.9} {build:19 version:0.5.6}]
                    """);

            LauncherSettings settings = new LauncherSettings();

            assertEquals(List.of("v0.4"), HytaleAuthService.previousPatchlineCandidates(settings));
        });
    }

    @Test
    void invalidRefreshTokenRemovesHytaleSession() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        apiClient.refreshTokenFailure = new HytaleApiException("Hytale API returned HTTP 400: invalid_grant", 400, null);
        HytaleAuthService authService = new HytaleAuthService(
                apiClient,
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("expired-access");
        session.setRefreshToken("refresh-token");
        session.setExpiresAt(Instant.now().minusSeconds(60));
        session.setUuid("player-uuid");
        session.setUsername("Player");
        settings.setHytaleAuthSession(session);

        assertThrows(HytaleApiException.class, () -> authService.getFriends(settings));

        assertEquals(1, apiClient.refreshTokenCalls);
        assertNull(settings.getHytaleAuthSession());
    }

    private static String jwtWithExpiration(Instant expiresAt) {
        return base64Url("{}")
                + "."
                + base64Url("{\"exp\":" + expiresAt.getEpochSecond() + "}")
                + ".signature";
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void withPlatform(String osName, String home, CheckedRunnable runnable) throws Exception {
        String previousOs = System.getProperty("os.name");
        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("os.name", osName);
            System.setProperty("user.home", home);
            runnable.run();
        } finally {
            restoreProperty("os.name", previousOs);
            restoreProperty("user.home", previousHome);
        }
    }

    private static void restoreProperty(String property, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previousValue);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class FakeHytaleApiClient extends HytaleApiClient {

        private int createGameSessionCalls;
        private int refreshTokenCalls;
        private int fetchFriendsCalls;
        private int fetchPublicProfileUsernamesCalls;
        private int fetchProfilesCalls;
        private String fetchFriendsSessionToken = "";
        private String fetchPublicProfileSessionToken = "";
        private String fetchProfilesAccessToken = "";
        private List<String> fetchPublicProfileUuids = List.of();
        private List<HytaleFriend> friends = List.of(new HytaleFriend("Friend", "friend-uuid", "Offline", "", false));
        private Map<String, String> publicProfileUsernames = Map.of();
        private List<HytaleProfile> profiles = List.of(new HytaleProfile("Player", "player-uuid", "owner-id", 0));
        private HytaleApiException refreshTokenFailure;
        private HytaleApiException createGameSessionFailure;

        @Override
        public TokenResponse refreshToken(String refreshToken) {
            refreshTokenCalls++;
            if (refreshTokenFailure != null) {
                throw refreshTokenFailure;
            }
            TokenResponse response = new TokenResponse();
            response.accessToken = "fresh-access";
            response.refreshToken = "next-refresh-token";
            response.expiresIn = 3600;
            return response;
        }

        @Override
        public HytaleGameSession createGameSession(String accessToken, String uuid) {
            createGameSessionCalls++;
            if (createGameSessionFailure != null) {
                throw createGameSessionFailure;
            }
            if ("stale-access".equals(accessToken)) {
                throw new HytaleApiException("expired", 401, null);
            }
            assertTrue(List.of("fresh-access", "valid-access", "cached-access").contains(accessToken));
            assertEquals("player-uuid", uuid);
            return new HytaleGameSession("fresh-session-token", "fresh-identity-token");
        }

        @Override
        public List<HytaleFriend> fetchFriends(String sessionToken) {
            fetchFriendsCalls++;
            fetchFriendsSessionToken = sessionToken;
            return friends;
        }

        @Override
        public Map<String, String> fetchPublicProfileUsernames(String sessionToken, List<String> uuids) {
            fetchPublicProfileUsernamesCalls++;
            fetchPublicProfileSessionToken = sessionToken;
            fetchPublicProfileUuids = uuids;
            return publicProfileUsernames;
        }

        @Override
        public List<HytaleProfile> fetchProfiles(String accessToken) {
            fetchProfilesCalls++;
            fetchProfilesAccessToken = accessToken;
            return profiles;
        }
    }
}
