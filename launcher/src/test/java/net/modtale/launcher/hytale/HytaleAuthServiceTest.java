package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertSame;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    void switchingProfileDiscardsOldTokensAndRequestsSelectedProfile() {
        FakeHytaleApiClient api = new FakeHytaleApiClient();
        SettingsStore store = new SettingsStore(tempDir.resolve("switch.json"));
        HytaleAuthService auth = new HytaleAuthService(api, store);
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = linkedAccount("previous-uuid", true);
        session.setAccountOwnerId("owner");
        session.setSessionToken(jwtWithExpiration(Instant.now().plusSeconds(3600)));
        session.setIdentityToken("previous-identity");
        session.setSessionProfileId("previous-uuid");
        settings.setHytaleAuthSession(session);
        auth.selectProfile(settings, new HytaleProfile("Wtrlmn", "player-uuid", "owner", 0));
        assertEquals("Wtrlmn", session.getUsername());
        assertEquals("", session.getSessionToken());
        assertEquals("", session.getIdentityToken());
        assertEquals("", store.load().getHytaleAuthSession().getSessionToken());
        assertEquals("fresh-session-token", auth.freshSessionToken(settings));
        assertEquals(1, api.createGameSessionCalls);
        assertEquals("player-uuid", store.load().getHytaleAuthSession().getSessionProfileId());
    }

    @Test
    void legacyOrMismatchedCachedSessionIsRefreshed() {
        for (String binding : List.of("", "previous-uuid")) {
            FakeHytaleApiClient api = new FakeHytaleApiClient();
            HytaleAuthService auth = new HytaleAuthService(api, new SettingsStore(tempDir.resolve("legacy.json")));
            LauncherSettings settings = new LauncherSettings();
            HytaleAuthSession session = linkedAccount("player-uuid", true);
            session.setSessionToken(jwtWithExpiration(Instant.now().plusSeconds(3600)));
            session.setIdentityToken("old-identity");
            session.setSessionProfileId(binding);
            settings.setHytaleAuthSession(session);
            assertEquals("fresh-session-token", auth.freshSessionToken(settings));
            assertEquals(1, api.createGameSessionCalls);
        }
    }

    @Test
    void profileChangeDuringSessionCreationDoesNotSaveOldProfileTokens() {
        FakeHytaleApiClient api = new FakeHytaleApiClient();
        HytaleAuthService auth = new HytaleAuthService(api, new SettingsStore(tempDir.resolve("race.json")));
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = linkedAccount("player-uuid", true);
        settings.setHytaleAuthSession(session);
        api.onCreateGameSession = () -> session.setUuid("other-uuid");
        assertThrows(HytaleApiException.class, () -> auth.freshSessionToken(settings));
        assertEquals("", session.getSessionToken());
        assertEquals("", session.getSessionProfileId());
    }

    @Test
    void freshAccessTokenRefreshesAndPersistsRotationWithoutCreatingGameSession() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        SettingsStore store = new SettingsStore(tempDir.resolve("settings.json"));
        HytaleAuthService authService = new HytaleAuthService(apiClient, store);
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setRefreshToken("old-refresh");
        session.setUuid("player-uuid");
        session.setUsername("Player");
        session.setExpiresAt(Instant.now().minusSeconds(1));
        settings.setHytaleAuthSession(session);

        assertEquals("fresh-access", authService.freshAccessToken(settings));
        assertEquals("next-refresh-token", settings.getHytaleAuthSession().getRefreshToken());
        assertEquals("next-refresh-token", store.load().getHytaleAuthSession().getRefreshToken());
        assertEquals("fresh-access", authService.freshAccessToken(settings));
        assertEquals(1, apiClient.refreshTokenCalls);
        assertEquals(0, apiClient.createGameSessionCalls);
    }

    @Test
    void concurrentExpiredTokenRequestsShareOnePersistedRefresh() throws Exception {
        CountDownLatch refreshing = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        HytaleApiClient api = new HytaleApiClient() {
            @Override public TokenResponse refreshToken(String refreshToken) {
                if (calls.incrementAndGet() > 1) {
                    throw new HytaleApiException("invalid_grant", 400, null);
                }
                refreshing.countDown();
                try {
                    assertTrue(releaseRefresh.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
                TokenResponse token = new TokenResponse();
                token.accessToken = "renewed-access";
                token.refreshToken = "rotated-refresh";
                token.expiresIn = 3600;
                return token;
            }
        };
        SettingsStore store = new SettingsStore(tempDir.resolve("concurrent.json"));
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleAuthSession(linkedAccount("player-uuid", false));
        HytaleAuthService auth = new HytaleAuthService(api, store);
        FutureTask<String> first = new FutureTask<>(() -> auth.freshAccessToken(settings));
        FutureTask<String> second = new FutureTask<>(() -> auth.freshAccessToken(settings));
        Thread firstThread = new Thread(first);
        Thread secondThread = new Thread(second);
        firstThread.start();
        try {
            assertTrue(refreshing.await(5, TimeUnit.SECONDS));
            secondThread.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (secondThread.getState() != Thread.State.BLOCKED && !second.isDone()
                    && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(Thread.State.BLOCKED, secondThread.getState());
        } finally {
            releaseRefresh.countDown();
            firstThread.join(5000);
            secondThread.join(5000);
        }
        assertEquals("renewed-access", first.get(5, TimeUnit.SECONDS));
        assertEquals("renewed-access", second.get(5, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
        assertEquals("rotated-refresh", store.load().getHytaleAuthSession().getRefreshToken());
        assertEquals("renewed-access", new HytaleAuthService(api, store).freshAccessToken(store.load()));
        assertEquals(1, calls.get());
    }

    @Test
    void lateAccessTokenRejectionReusesAlreadyRefreshedCredentials() {
        FakeHytaleApiClient api = new FakeHytaleApiClient();
        SettingsStore store = new SettingsStore(tempDir.resolve("late-rejection.json"));
        HytaleAuthService auth = new HytaleAuthService(api, store);
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = linkedAccount("player-uuid", true);
        settings.setHytaleAuthSession(session);
        api.onFetchProfiles = () -> {
            api.onFetchProfiles = () -> {};
            // Simulate another request renewing while this request is in flight.
            session.setExpiresAt(Instant.now().minusSeconds(1));
            assertEquals("fresh-access", auth.freshAccessToken(settings));
            throw new HytaleApiException("old access token rejected", 401, null);
        };

        auth.getProfilePlaytimeSeconds(settings);

        assertEquals(1, api.refreshTokenCalls);
        assertEquals(2, api.fetchProfilesCalls);
        assertEquals("fresh-access", api.fetchProfilesAccessToken);
        assertEquals("next-refresh-token", store.load().getHytaleAuthSession().getRefreshToken());
    }

    @Test
    void temporaryRefreshFailuresKeepCredentialsForLaterRetry() {
        for (int status : new int[] {-1, 429, 500, 503}) {
            FakeHytaleApiClient api = new FakeHytaleApiClient();
            SettingsStore store = new SettingsStore(tempDir.resolve("retry-" + status + ".json"));
            LauncherSettings settings = new LauncherSettings();
            settings.setHytaleAuthSession(linkedAccount("player-uuid", false));
            store.save(settings);
            HytaleAuthService auth = new HytaleAuthService(api, store);
            api.refreshTokenFailure = new HytaleApiException("temporarily unavailable", status, null);
            assertThrows(HytaleApiException.class, () -> auth.freshAccessToken(settings));
            assertEquals("refresh-token", store.load().getHytaleAuthSession().getRefreshToken());
            api.refreshTokenFailure = null;
            assertEquals("fresh-access", auth.freshAccessToken(settings));
        }
    }

    @Test
    void tokenRefreshPreservesAccountSelectedDuringRequest() {
        assertSelectionPreservedDuringRequest(false);
    }

    @Test
    void gameSessionRefreshPreservesAccountSelectedDuringRequest() {
        assertSelectionPreservedDuringRequest(true);
    }

    private void assertSelectionPreservedDuringRequest(boolean gameSession) {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        SettingsStore store = new SettingsStore(tempDir.resolve("settings.json"));
        HytaleAuthService authService = new HytaleAuthService(apiClient, store);
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession accountA = linkedAccount("player-uuid", gameSession);
        HytaleAuthSession accountB = linkedAccount("other-uuid", true);
        settings.upsertHytaleAuthSession(accountB);
        settings.upsertHytaleAuthSession(accountA);
        Runnable switchAccount = () -> settings.selectHytaleAccount("other-uuid");
        if (gameSession) {
            apiClient.onCreateGameSession = switchAccount;
            assertEquals("fresh-session-token", authService.freshSessionToken(settings));
        } else {
            apiClient.onRefreshToken = switchAccount;
            assertEquals("fresh-access", authService.freshAccessToken(settings));
        }
        assertEquals("other-uuid", settings.getHytaleAuthSession().getUuid());
        LauncherSettings persisted = store.load();
        assertEquals("other-uuid", persisted.getHytaleAuthSession().getUuid());
        assertEquals(2, persisted.getHytaleAuthSessions().size());
        HytaleAuthSession persistedA = persisted.getHytaleAuthSessions().stream()
                .filter(session -> session.getUuid().equals("player-uuid")).findFirst().orElseThrow();
        assertEquals(gameSession ? "fresh-session-token" : "next-refresh-token",
                gameSession ? persistedA.getSessionToken() : persistedA.getRefreshToken());
        assertEquals("refresh-token", accountB.getRefreshToken());
        assertEquals("", accountB.getSessionToken());
    }

    @Test
    void tokenRefreshDoesNotRestoreRemovedAccount() {
        assertRemovedDuringRequest(false);
    }

    @Test
    void gameSessionRefreshDoesNotRestoreRemovedAccount() {
        assertRemovedDuringRequest(true);
    }

    private void assertRemovedDuringRequest(boolean gameSession) {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        SettingsStore store = new SettingsStore(tempDir.resolve("settings.json"));
        HytaleAuthService authService = new HytaleAuthService(apiClient, store);
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession accountA = linkedAccount("player-uuid", gameSession);
        settings.upsertHytaleAuthSession(accountA);
        Runnable removeAccount = () -> authService.logoutAccount(settings, "player-uuid");
        if (gameSession) {
            apiClient.onCreateGameSession = removeAccount;
            assertThrows(HytaleApiException.class, () -> authService.freshSessionToken(settings));
        } else {
            apiClient.onRefreshToken = removeAccount;
            assertThrows(HytaleApiException.class, () -> authService.freshAccessToken(settings));
        }
        assertTrue(settings.getHytaleAuthSessions().isEmpty());
        assertTrue(store.load().getHytaleAuthSessions().isEmpty());
        assertEquals("refresh-token", accountA.getRefreshToken());
        assertEquals("", accountA.getSessionToken());
    }

    @Test
    void failedRefreshDoesNotRemoveReplacementLoginForSameAccount() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        SettingsStore store = new SettingsStore(tempDir.resolve("settings.json"));
        HytaleAuthService authService = new HytaleAuthService(apiClient, store);
        LauncherSettings settings = new LauncherSettings();
        settings.upsertHytaleAuthSession(linkedAccount("player-uuid", false));
        HytaleAuthSession replacement = linkedAccount("player-uuid", true);
        apiClient.onRefreshToken = () -> {
            settings.upsertHytaleAuthSession(replacement);
            store.save(settings);
        };
        apiClient.refreshTokenFailure = new HytaleApiException("invalid_grant", 400, null);
        assertThrows(HytaleApiException.class, () -> authService.freshAccessToken(settings));
        assertEquals(replacement, settings.getHytaleAuthSession());
        assertEquals("valid-access", store.load().getHytaleAuthSession().getAccessToken());
    }

    private static HytaleAuthSession linkedAccount(String uuid, boolean validToken) {
        HytaleAuthSession session = new HytaleAuthSession();
        session.setUuid(uuid);
        session.setUsername(uuid);
        session.setRefreshToken("refresh-token");
        session.setAccessToken(validToken ? "valid-access" : "expired-access");
        session.setExpiresAt(Instant.now().plusSeconds(validToken ? 600 : -60));
        return session;
    }

    @Test void rejectedUnexpiredSessionIsReplacedUsingExistingHytaleCredentials() {
        FakeHytaleApiClient api = new FakeHytaleApiClient();
        SettingsStore store = new SettingsStore(tempDir.resolve("renewed.json"));
        HytaleAuthService auth = new HytaleAuthService(api, store);
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = linkedAccount("player-uuid", true);
        String rejected = jwtWithExpiration(Instant.now().plusSeconds(3600));
        session.setSessionProfileId("player-uuid");
        session.setSessionToken(rejected);
        session.setIdentityToken("identity");
        settings.setHytaleAuthSession(session);
        assertEquals(rejected, auth.freshSessionToken(settings));
        assertEquals("fresh-session-token", auth.renewRejectedSessionToken(settings, "player-uuid", rejected));
        assertEquals(1, api.createGameSessionCalls);
        assertEquals(0, api.refreshTokenCalls);
        assertEquals("fresh-session-token", store.load().getHytaleAuthSession().getSessionToken());
    }

    @Test void rejectedSessionRenewalReusesConcurrentReplacementAndGuardsProfile() {
        FakeHytaleApiClient api = new FakeHytaleApiClient();
        HytaleAuthService auth = new HytaleAuthService(api, new SettingsStore(tempDir.resolve("renewed.json")));
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = linkedAccount("player-uuid", true);
        String replacement = jwtWithExpiration(Instant.now().plusSeconds(3600));
        session.setSessionProfileId("player-uuid");
        session.setSessionToken(replacement);
        session.setIdentityToken("identity");
        settings.setHytaleAuthSession(session);
        assertEquals(replacement, auth.renewRejectedSessionToken(settings, "player-uuid", "rejected"));
        assertThrows(HytaleApiException.class, () -> auth.renewRejectedSessionToken(settings, "other-uuid", replacement));
        assertEquals(0, api.createGameSessionCalls);
    }

    @Test void rejectedSessionRenewalDoesNotFallBackToRejectedToken() {
        FakeHytaleApiClient api = new FakeHytaleApiClient();
        api.createGameSessionFailure = new HytaleApiException("unavailable", 503, null);
        HytaleAuthService auth = new HytaleAuthService(api, new SettingsStore(tempDir.resolve("renewed.json")));
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = linkedAccount("player-uuid", true);
        String rejected = jwtWithExpiration(Instant.now().plusSeconds(3600));
        session.setSessionProfileId("player-uuid");
        session.setSessionToken(rejected);
        session.setIdentityToken("identity");
        settings.setHytaleAuthSession(session);
        assertThrows(HytaleApiException.class, () -> auth.renewRejectedSessionToken(settings, "player-uuid", rejected));
        assertSame(session, settings.getHytaleAuthSession());
        assertEquals(0, api.refreshTokenCalls);
    }

    @Test
    void freshProfileSessionDoesNotUseLaunchOfflineFallback() {
        FakeHytaleApiClient apiClient = new FakeHytaleApiClient();
        apiClient.createGameSessionFailure = new HytaleApiException("unavailable", 503, null);
        HytaleAuthService authService = new HytaleAuthService(apiClient, new SettingsStore(tempDir.resolve("settings.json")));
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("valid-access");
        session.setRefreshToken("refresh");
        session.setUuid("player-uuid");
        session.setUsername("Player");
        session.setExpiresAt(Instant.now().plusSeconds(600));
        session.setSessionProfileId("player-uuid");
        session.setSessionToken("expired-session");
        session.setIdentityToken("expired-identity");
        settings.setHytaleAuthSession(session);
        assertThrows(HytaleApiException.class, () -> authService.freshSessionToken(settings));
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
        session.setSessionProfileId("player-uuid");
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
        session.setSessionProfileId("player-uuid");
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
        session.setSessionProfileId("player-uuid");
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

        private Runnable onRefreshToken = () -> {};
        private Runnable onFetchProfiles = () -> {};
        private Runnable onCreateGameSession = () -> {};
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
            onRefreshToken.run();
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
            onCreateGameSession.run();
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
            onFetchProfiles.run();
            fetchProfilesAccessToken = accessToken;
            return profiles;
        }
    }
}
