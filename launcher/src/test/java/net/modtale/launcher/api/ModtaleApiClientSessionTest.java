package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.model.sync.LauncherSettingsSnapshot;
import net.modtale.launcher.model.auth.SignInResponse;
import net.modtale.launcher.model.project.ProjectComment;
import net.modtale.launcher.model.project.ProjectGallery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModtaleApiClientSessionTest {

    private static final String CSRF_TOKEN = "csrf-token-one";

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String launcherSettingsJson = """
            {"schemaVersion":1,"settingsHash":"","preferences":{},"installedProjects":[]}
            """;
    private String lastLauncherSettingsMutation = "";
    private String commentsJson = """
            {"comments":[{"id":"comment-1","userId":"user-1","user":"ada","content":"First","date":"2026-06-01T12:00:00Z","upvoteCount":2,"downvoteCount":1}]}
            """;
    private String galleryJson = """
            {"galleryImages":["https://cdn.example/gallery/one.png","https://youtu.be/video123"],
             "galleryImageCaptions":{"https://cdn.example/gallery/one.png":"Opening shot"}}
            """;
    private String lastCommentMutation = "";

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void launcherSessionCookieIsSavedAndRestored() throws IOException {
        startSessionServer();
        Path sessionPath = tempDir.resolve("session-cookies.json");

        ModtaleApiClient firstClient = new ModtaleApiClient(baseUrl(), sessionPath);
        firstClient.exchangeLauncherCode("launcher-code");

        assertTrue(Files.exists(sessionPath));

        ModtaleApiClient restoredClient = new ModtaleApiClient(baseUrl(), sessionPath);

        assertTrue(restoredClient.hasStoredSession());
        assertEquals("ada", restoredClient.currentUser().username());
        assertEquals("/avatars/ada.png", restoredClient.currentUser().avatarUrl());
    }

    @Test
    void logoutClearsPersistedLauncherSession() throws IOException {
        startSessionServer();
        Path sessionPath = tempDir.resolve("session-cookies.json");
        ModtaleApiClient client = new ModtaleApiClient(baseUrl(), sessionPath);

        client.exchangeLauncherCode("launcher-code");
        assertTrue(Files.exists(sessionPath));

        client.logout();

        assertFalse(Files.exists(sessionPath));
        assertFalse(client.hasStoredSession());
    }

    @Test
    void passwordSignInSessionCookieIsSavedAndRestored() throws IOException {
        startSessionServer();
        Path sessionPath = tempDir.resolve("password-session-cookies.json");

        ModtaleApiClient firstClient = new ModtaleApiClient(baseUrl(), sessionPath);
        SignInResponse response = firstClient.signIn("ada", "correct horse battery staple".toCharArray());

        assertFalse(response.mfaRequired());
        assertTrue(Files.exists(sessionPath));

        ModtaleApiClient restoredClient = new ModtaleApiClient(baseUrl(), sessionPath);

        assertTrue(restoredClient.hasStoredSession());
        assertEquals("ada", restoredClient.currentUser().username());
    }

    @Test
    void mfaSessionCookieIsSavedAfterValidation() throws IOException {
        startSessionServer();
        Path sessionPath = tempDir.resolve("mfa-session-cookies.json");

        ModtaleApiClient firstClient = new ModtaleApiClient(baseUrl(), sessionPath);
        SignInResponse response = firstClient.signIn("mfa", "correct horse battery staple".toCharArray());

        assertTrue(response.mfaRequired());
        assertEquals("pre-auth-one", response.preAuthToken());
        assertFalse(Files.exists(sessionPath));

        firstClient.validateMfa(response.preAuthToken(), "123456");

        assertTrue(Files.exists(sessionPath));

        ModtaleApiClient restoredClient = new ModtaleApiClient(baseUrl(), sessionPath);

        assertTrue(restoredClient.hasStoredSession());
        assertEquals("ada", restoredClient.currentUser().username());
    }

    @Test
    void launcherSettingsCanBeSavedAndLoaded() throws IOException {
        startSessionServer();
        Path sessionPath = tempDir.resolve("sync-session-cookies.json");
        ModtaleApiClient client = new ModtaleApiClient(baseUrl(), sessionPath);
        client.exchangeLauncherCode("launcher-code");

        LauncherSettingsSnapshot snapshot = new LauncherSettingsSnapshot();
        LauncherSettingsSnapshot.Preferences preferences = new LauncherSettingsSnapshot.Preferences();
        preferences.setGameVersion("1.2.3");
        snapshot.setPreferences(preferences);
        LauncherSettingsSnapshot.InstalledProjectSnapshot installed = new LauncherSettingsSnapshot.InstalledProjectSnapshot();
        installed.setProjectId("project-1");
        installed.setInstalledVersion("2.0.0");
        snapshot.setInstalledProjects(java.util.List.of(installed));
        snapshot.refreshHash();

        LauncherSettingsSnapshot saved = client.updateLauncherSettings(snapshot);
        LauncherSettingsSnapshot loaded = client.getLauncherSettings();

        assertEquals(snapshot.effectiveHash(), saved.effectiveHash());
        assertEquals(snapshot.effectiveHash(), loaded.effectiveHash());
        assertEquals("project-1", loaded.installedProjects().getFirst().getProjectId());
    }

    @Test
    void launcherPreferenceSettingsUseCompactEndpoint() throws IOException {
        startSessionServer();
        Path sessionPath = tempDir.resolve("sync-preferences-session-cookies.json");
        ModtaleApiClient client = new ModtaleApiClient(baseUrl(), sessionPath);
        client.exchangeLauncherCode("launcher-code");

        LauncherSettingsSnapshot snapshot = new LauncherSettingsSnapshot();
        LauncherSettingsSnapshot.Preferences preferences = new LauncherSettingsSnapshot.Preferences();
        preferences.setGameVersion("1.2.4");
        snapshot.setPreferences(preferences);
        LauncherSettingsSnapshot.InstalledProjectSnapshot installed = new LauncherSettingsSnapshot.InstalledProjectSnapshot();
        installed.setProjectId("project-1");
        installed.setInstalledVersion("2.0.0");
        snapshot.setInstalledProjects(java.util.List.of(installed));
        snapshot.refreshHash();

        LauncherSettingsSnapshot saved = client.updateLauncherSettingsPreferences(snapshot);

        assertEquals(snapshot.computeHash(), saved.effectiveHash());
        assertTrue(lastLauncherSettingsMutation.contains("\"installedProjects\":[]"));
        assertFalse(lastLauncherSettingsMutation.contains("project-1"));
    }

    @Test
    void commentsAreNotServedFromProjectCacheAndMutationsSendCsrf() throws IOException {
        startSessionServer();
        ModtaleApiClient client = new ModtaleApiClient(baseUrl(), tempDir.resolve("comments-session.json"));
        client.exchangeLauncherCode("launcher-code");

        List<ProjectComment> first = client.getComments("voile");
        assertEquals(1, first.size());
        assertEquals("First", first.getFirst().content());
        assertEquals(1, first.getFirst().score());

        commentsJson = """
                {"comments":[
                  {"id":"comment-1","userId":"user-1","user":"ada","content":"First","date":"2026-06-01T12:00:00Z"},
                  {"id":"comment-2","authorId":"user-2","user":"bea","content":"Second","date":"2026-06-02T12:00:00Z",
                   "developerReply":{"authorId":"creator-1","user":"Creator","content":"Thanks!","date":"2026-06-03T12:00:00Z","upvoteCount":3}}
                ]}
                """;

        List<ProjectComment> second = client.getComments("voile");
        assertEquals(2, second.size());
        assertEquals("Second", second.get(1).content());
        assertEquals("creator-1", second.get(1).developerReply().userId());

        client.postComment("voile", "Hello launcher");
        assertTrue(lastCommentMutation.contains("\"content\":\"Hello launcher\""));

        client.voteComment("voile", "comment-1", true, false);
        assertEquals("POST /api/v1/projects/voile/comments/comment-1/vote?upvote=true", lastCommentMutation);
    }

    @Test
    void galleryIsFetchedFromFocusedProjectGalleryEndpoint() throws IOException {
        startSessionServer();
        ModtaleApiClient client = new ModtaleApiClient(baseUrl(), tempDir.resolve("gallery-session.json"));

        ProjectGallery gallery = client.getProjectGallery("voile");

        assertEquals(2, gallery.galleryImages().size());
        assertEquals("https://cdn.example/gallery/one.png", gallery.galleryImages().getFirst());
        assertEquals("Opening shot", gallery.galleryImageCaptions().get("https://cdn.example/gallery/one.png"));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    private void startSessionServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/auth/launcher/exchange", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=session-one; Path=/; HttpOnly");
            exchange.getResponseHeaders().add("Set-Cookie", "XSRF-TOKEN=" + CSRF_TOKEN + "; Path=/");
            respond(exchange, 200, "{\"status\":\"success\"}");
        });
        server.createContext("/api/v1/auth/signin", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"username\":\"mfa\"")) {
                respond(exchange, 202, "{\"status\":\"mfa_required\",\"mfaRequired\":true,\"preAuthToken\":\"pre-auth-one\"}");
                return;
            }
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=session-password; Path=/; HttpOnly");
            exchange.getResponseHeaders().add("Set-Cookie", "XSRF-TOKEN=" + CSRF_TOKEN + "; Path=/");
            respond(exchange, 200, "{\"status\":\"success\",\"mfaRequired\":false}");
        });
        server.createContext("/api/v1/auth/mfa/validate-login", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=session-mfa; Path=/; HttpOnly");
            exchange.getResponseHeaders().add("Set-Cookie", "XSRF-TOKEN=" + CSRF_TOKEN + "; Path=/");
            respond(exchange, 200, "{\"status\":\"success\"}");
        });
        server.createContext("/api/v1/user/me", exchange -> {
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookie == null || (!cookie.contains("SESSION=session-one")
                    && !cookie.contains("SESSION=session-password")
                    && !cookie.contains("SESSION=session-mfa"))) {
                respond(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            respond(exchange, 200, "{\"id\":\"user-1\",\"username\":\"ada\",\"avatarUrl\":\"/avatars/ada.png\",\"likedProjectIds\":[]}");
        });
        server.createContext("/api/v1/user/launcher-settings/preferences", exchange -> {
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookie == null || (!cookie.contains("SESSION=session-one")
                    && !cookie.contains("SESSION=session-password")
                    && !cookie.contains("SESSION=session-mfa"))) {
                respond(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            if (!CSRF_TOKEN.equals(exchange.getRequestHeaders().getFirst("X-XSRF-TOKEN"))) {
                respond(exchange, 403, "{\"error\":\"Missing CSRF token\"}");
                return;
            }
            lastLauncherSettingsMutation = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            launcherSettingsJson = lastLauncherSettingsMutation;
            respond(exchange, 200, launcherSettingsJson);
        });
        server.createContext("/api/v1/user/launcher-settings", exchange -> {
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookie == null || (!cookie.contains("SESSION=session-one")
                    && !cookie.contains("SESSION=session-password")
                    && !cookie.contains("SESSION=session-mfa"))) {
                respond(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!CSRF_TOKEN.equals(exchange.getRequestHeaders().getFirst("X-XSRF-TOKEN"))) {
                    respond(exchange, 403, "{\"error\":\"Missing CSRF token\"}");
                    return;
                }
                launcherSettingsJson = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                lastLauncherSettingsMutation = launcherSettingsJson;
            }
            respond(exchange, 200, launcherSettingsJson);
        });
        server.createContext("/api/v1/projects/voile/comments", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 200, commentsJson);
                return;
            }
            if (!CSRF_TOKEN.equals(exchange.getRequestHeaders().getFirst("X-XSRF-TOKEN"))) {
                respond(exchange, 403, "{\"error\":\"Missing CSRF token\"}");
                return;
            }
            lastCommentMutation = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, 200, "{\"status\":\"success\"}");
        });
        server.createContext("/api/v1/projects/voile/comments/comment-1/vote", exchange -> {
            if (!CSRF_TOKEN.equals(exchange.getRequestHeaders().getFirst("X-XSRF-TOKEN"))) {
                respond(exchange, 403, "{\"error\":\"Missing CSRF token\"}");
                return;
            }
            lastCommentMutation = exchange.getRequestMethod() + " " + exchange.getRequestURI();
            respond(exchange, 200, "{\"status\":\"success\"}");
        });
        server.createContext("/api/v1/projects/voile/gallery", exchange -> respond(exchange, 200, galleryJson));
        server.createContext("/api/v1/auth/logout", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=; Path=/; Max-Age=0");
            respond(exchange, 200, "{\"status\":\"success\"}");
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
