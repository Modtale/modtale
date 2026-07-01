package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class HytaleApiClientTest {

    @Test
    void normalizesSupportedOfficialBranches() {
        assertEquals("release", HytaleApiClient.normalizeBranch(null));
        assertEquals("release", HytaleApiClient.normalizeBranch("release"));
        assertEquals("pre-release", HytaleApiClient.normalizeBranch("prerelease"));
        assertEquals("pre-release", HytaleApiClient.normalizeBranch("pre_release"));
        assertEquals("pre-release", HytaleApiClient.normalizeBranch("pre release"));
        assertEquals("pre-release", HytaleApiClient.normalizeBranch("pre-release"));
        assertEquals("v0.4", HytaleApiClient.normalizeBranch("v0.4"));
        assertEquals("v0.4", HytaleApiClient.normalizeBranch("0.4"));
        assertEquals("release", HytaleApiClient.normalizeBranch("experimental"));
    }

    @Test
    void parsesHytaleBlogCardsWithImages() {
        String html = """
                <main>
                  <article>
                    <a href="/news/2026/5/update-5/">
                      <img src="/static/images/update-5.jpg" alt="">
                      <h4>HYTALE PATCH NOTES - UPDATE 5</h4>
                      <time>May 26, 2026</time>
                    </a>
                  </article>
                </main>
                """;

        List<HytaleBlogPost> posts = HytaleApiClient.parseBlogPosts(html, 1);

        assertEquals(1, posts.size());
        assertEquals("HYTALE PATCH NOTES - UPDATE 5", posts.getFirst().title());
        assertEquals("https://hytale.com/news/2026/5/update-5/", posts.getFirst().url());
        assertEquals("https://hytale.com/static/images/update-5.jpg", posts.getFirst().imageUrl());
        assertEquals(Instant.parse("2026-05-26T12:00:00Z"), posts.getFirst().publishedAt());
    }

    @Test
    void parsesHytaleBlogRssItemsWithoutBodyText() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>HYTALE PATCH NOTES - UPDATE 5</title>
                      <link>https://hytale.com/news/2026/5/update-5/</link>
                      <description>Update 5 is out! Hytale&amp;#39;s social sidebar is here...</description>
                      <pubDate>Tue, 26 May 2026 10:36:45 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """;

        List<HytaleBlogPost> posts = HytaleApiClient.parseBlogPosts(rss, 1);

        assertEquals(1, posts.size());
        assertEquals("HYTALE PATCH NOTES - UPDATE 5", posts.getFirst().title());
        assertEquals("https://hytale.com/news/2026/5/update-5/", posts.getFirst().url());
        assertEquals("", posts.getFirst().imageUrl());
        assertEquals(Instant.parse("2026-05-26T10:36:45Z"), posts.getFirst().publishedAt());
    }

    @Test
    void parsesHytaleBlogRssImagesWhenProvided() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>HYTALE PATCH NOTES - UPDATE 5</title>
                      <link>https://hytale.com/news/2026/5/update-5/</link>
                      <description><![CDATA[<img src="/static/images/update-5.jpg">Update 5 is out...]]></description>
                      <pubDate>Tue, 26 May 2026 10:36:45 GMT</pubDate>
                    </item>
                    <item>
                      <title>HOTFIXES: UPDATE 5</title>
                      <link>https://hytale.com/news/2026/5/hotfixes-update-5/</link>
                      <enclosure url="https://cdn.hytale.com/hotfixes.png" type="image/png"/>
                      <pubDate>Wed, 27 May 2026 20:28:50 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """;

        List<HytaleBlogPost> posts = HytaleApiClient.parseBlogPosts(rss, 10);

        assertEquals(2, posts.size());
        assertEquals("https://hytale.com/static/images/update-5.jpg", posts.getFirst().imageUrl());
        assertEquals("https://cdn.hytale.com/hotfixes.png", posts.get(1).imageUrl());
    }

    @Test
    void extractsFriendsFromLauncherDataPayload() throws Exception {
        String json = """
                {
                  "social": {
                    "friends": [
                      {
                        "username": "Geometrically",
                        "uuid": "friend-uuid",
                        "presence": { "status": "playing" },
                        "online": true
                      }
                    ]
                  }
                }
                """;

        List<HytaleFriend> friends = HytaleApiClient.parseFriends(new ObjectMapper().readTree(json));

        assertEquals(1, friends.size());
        assertEquals("Geometrically", friends.getFirst().displayName());
        assertEquals("friend-uuid", friends.getFirst().uuid());
        assertEquals("Playing", friends.getFirst().displayStatus());
        assertTrue(friends.getFirst().online());
    }

    @Test
    void extractsFriendsFromSocialListPayload() throws Exception {
        String json = """
                [
                  {
                    "player": {
                      "displayName": "Builder",
                      "uuid": "friend-uuid-2",
                      "avatarUrl": "https://cdn.hytale.com/avatar.png"
                    },
                    "presence": { "status": "IN_GAME", "online": true }
                  },
                  {
                    "profile": {
                      "username": "OfflinePal",
                      "id": "friend-uuid-3"
                    },
                    "state": "offline"
                  }
                ]
                """;

        List<HytaleFriend> friends = HytaleApiClient.parseFriends(new ObjectMapper().readTree(json));

        assertEquals(2, friends.size());
        assertEquals("Builder", friends.getFirst().displayName());
        assertEquals("friend-uuid-2", friends.getFirst().uuid());
        assertEquals("In game", friends.getFirst().displayStatus());
        assertEquals("https://cdn.hytale.com/avatar.png", friends.getFirst().avatarUrl());
        assertTrue(friends.getFirst().online());
        assertEquals("OfflinePal", friends.get(1).displayName());
        assertEquals("Offline", friends.get(1).displayStatus());
        assertFalse(friends.get(1).online());
    }

    @Test
    void fetchFriendsUsesSocialSessionToken() {
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                HytaleApiClient.LAUNCHER_INFO_URL,
                new StubResponse(200, "{\"version\":\"test-launcher\"}"),
                HytaleApiClient.SOCIAL_FRIENDS_URL,
                new StubResponse(200, "[{\"username\":\"Geometrically\",\"uuid\":\"friend-uuid\"}]")
        ));

        List<HytaleFriend> friends = new HytaleApiClient(httpClient).fetchFriends("session-token");

        assertEquals(1, friends.size());
        assertEquals("Geometrically", friends.getFirst().displayName());
        assertEquals(List.of(
                URI.create(HytaleApiClient.LAUNCHER_INFO_URL),
                URI.create(HytaleApiClient.SOCIAL_FRIENDS_URL)
        ), httpClient.requestUris());
        assertEquals("Bearer session-token", httpClient.requestHeader(HytaleApiClient.SOCIAL_FRIENDS_URL, "Authorization"));
    }

    @Test
    void fetchFriendsDoesNotFallBackToLauncherDataWhenSocialRejectsToken() {
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                HytaleApiClient.LAUNCHER_INFO_URL,
                new StubResponse(200, "{\"version\":\"test-launcher\"}"),
                HytaleApiClient.SOCIAL_FRIENDS_URL,
                new StubResponse(403, "session validation failed with status 404")
        ));

        assertThrows(HytaleApiException.class, () -> new HytaleApiClient(httpClient).fetchFriends("access-token"));

        assertEquals(List.of(
                URI.create(HytaleApiClient.LAUNCHER_INFO_URL),
                URI.create(HytaleApiClient.SOCIAL_FRIENDS_URL)
        ), httpClient.requestUris());
    }

    @Test
    void fetchProfilesIncludesOfficialPlaytimeSeconds() {
        String launcherDataUrl = HytaleApiClient.LAUNCHER_DATA_URL + "?client_id=" + HytaleApiClient.CLIENT_ID;
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                HytaleApiClient.LAUNCHER_INFO_URL,
                new StubResponse(200, "{\"version\":\"test-launcher\"}"),
                launcherDataUrl,
                new StubResponse(200, """
                        {
                          "owner": "owner-id",
                          "profiles": [
                            {
                              "username": "Villagers654",
                              "uuid": "profile-uuid",
                              "playtimeSeconds": 66321
                            },
                            {
                              "username": "Wtrlmn",
                              "uuid": "alt-profile-uuid",
                              "playtimeSeconds": 0
                            }
                          ]
                        }
                        """)
        ));

        List<HytaleProfile> profiles = new HytaleApiClient(httpClient).fetchProfiles("access-token");

        assertEquals(2, profiles.size());
        assertEquals("Villagers654", profiles.getFirst().username());
        assertEquals("profile-uuid", profiles.getFirst().uuid());
        assertEquals("owner-id", profiles.getFirst().owner());
        assertEquals(66321, profiles.getFirst().playtimeSeconds());
        assertEquals(0, profiles.get(1).playtimeSeconds());
        assertEquals("Bearer access-token", httpClient.requestHeader(launcherDataUrl, "Authorization"));
    }

    @Test
    void discoversAvailableOfficialPatchlines() {
        String launcherDataUrl = HytaleApiClient.LAUNCHER_DATA_URL + "?client_id=" + HytaleApiClient.CLIENT_ID;
        String releaseUrl = patchesUrl("release", 0);
        String preReleaseUrl = patchesUrl("pre-release", 0);
        String previousUrl = patchesUrl("v0.4", 0);
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                HytaleApiClient.LAUNCHER_INFO_URL,
                new StubResponse(200, "{\"version\":\"test-launcher\"}"),
                launcherDataUrl,
                new StubResponse(200, """
                        {
                          "patchlines": {
                            "v0.4": { "entitled": true },
                            "experimental": { "entitled": false }
                          }
                        }
                        """),
                releaseUrl,
                new StubResponse(200, "{\"steps\":[{\"from\":0,\"to\":19}]}"),
                preReleaseUrl,
                new StubResponse(200, "{\"steps\":[{\"from\":0,\"to\":52}]}"),
                previousUrl,
                new StubResponse(200, "{\"steps\":[{\"from\":0,\"to\":7}]}")
        ));

        List<String> patchlines = new HytaleApiClient(httpClient)
                .getAvailablePatchlines("access-token", List.of("0.4"));

        assertEquals(List.of("release", "pre-release", "v0.4"), patchlines);
    }

    @Test
    void rateLimitExceptionIncludesRetryAfterHeader() {
        String releaseUrl = patchesUrl("release", 0);
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                HytaleApiClient.LAUNCHER_INFO_URL,
                new StubResponse(200, "{\"version\":\"test-launcher\"}"),
                releaseUrl,
                new StubResponse(429, "rate limited", Map.of("Retry-After", List.of("7")))
        ));

        HytaleApiException error = assertThrows(HytaleApiException.class,
                () -> new HytaleApiClient(httpClient).getAvailableVersions("access-token", "release"));

        assertEquals(429, error.statusCode());
        assertEquals(7_000, error.retryAfterMillis());
    }

    @Test
    void parsesPublicProfileUsername() throws Exception {
        String json = """
                {
                  "uuid": "friend-uuid",
                  "username": "ItsNeil",
                  "skin": "{\\"haircut\\":\\"Sideslick.BrownDark\\"}"
                }
                """;

        Optional<String> username = HytaleApiClient.parsePublicProfileUsername(new ObjectMapper().readTree(json));

        assertEquals(Optional.of("ItsNeil"), username);
    }

    @Test
    void fetchPublicProfileUsernamesUsesGameSessionToken() {
        String uuid = "6e2fc9b9-0bf3-4aa4-8697-bc8b04643356";
        String profileUrl = HytaleApiClient.PUBLIC_PROFILE_BY_UUID_URL + "/" + uuid;
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                HytaleApiClient.LAUNCHER_INFO_URL,
                new StubResponse(200, "{\"version\":\"test-launcher\"}"),
                profileUrl,
                new StubResponse(200, "{\"uuid\":\"" + uuid + "\",\"username\":\"ItsNeil\",\"skin\":\"{}\"}")
        ));

        Map<String, String> usernames = new HytaleApiClient(httpClient)
                .fetchPublicProfileUsernames("session-token", List.of(uuid));

        assertEquals("ItsNeil", usernames.get(uuid));
        assertEquals(List.of(
                URI.create(HytaleApiClient.LAUNCHER_INFO_URL),
                URI.create(profileUrl)
        ), httpClient.requestUris());
        assertEquals("Bearer session-token", httpClient.requestHeader(profileUrl, "Authorization"));
    }

    private record StubResponse(int statusCode, String body, Map<String, List<String>> headers) {

        private StubResponse(int statusCode, String body) {
            this(statusCode, body, Map.of());
        }
    }

    private static String patchesUrl(String branch, int fromBuild) {
        return HytaleApiClient.PATCHES_BASE_URL + "/"
                + HytalePlatform.os() + "/"
                + HytalePlatform.arch() + "/"
                + HytaleApiClient.normalizeBranch(branch) + "/"
                + fromBuild;
    }

    private static final class FakeHttpClient extends HttpClient {

        private final HttpClient delegate = HttpClient.newHttpClient();
        private final Map<String, StubResponse> responses;
        private final List<HttpRequest> requests = new ArrayList<>();

        private FakeHttpClient(Map<String, StubResponse> responses) {
            this.responses = responses;
        }

        private List<URI> requestUris() {
            return requests.stream().map(HttpRequest::uri).toList();
        }

        private String requestHeader(String url, String name) {
            return requests.stream()
                    .filter(request -> request.uri().toString().equals(url))
                    .findFirst()
                    .flatMap(request -> request.headers().firstValue(name))
                    .orElse("");
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            requests.add(request);
            StubResponse response = responses.getOrDefault(request.uri().toString(), new StubResponse(404, "not found"));
            return new FakeHttpResponse<>(request, response.statusCode(), (T) response.body(), response.headers());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException("sendAsync is not used by these tests.");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException("sendAsync is not used by these tests.");
        }
    }

    private record FakeHttpResponse<T>(
            HttpRequest request,
            int statusCode,
            T body,
            Map<String, List<String>> headerValues
    ) implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(headerValues == null ? Map.of() : headerValues, (left, right) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }
}
