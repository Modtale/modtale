package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.modtale.launcher.model.project.CurseForgeCommentsPage;
import net.modtale.launcher.model.project.ProjectComment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurseForgeCommentsClientTest {

    private HttpServer server;
    private CurseForgeCommentsClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        client = new CurseForgeCommentsClient(HttpClient.newHttpClient(), URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void loadsRequestedPagesAndPreservesReadOnlyNestedComments() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/v1/mods/42/comments", exchange -> {
            assertEquals("ModtaleLauncher/0.1 (+https://modtale.net)",
                    exchange.getRequestHeaders().getFirst("User-Agent"));
            int request = requests.getAndIncrement();
            assertEquals("page=" + request, exchange.getRequestURI().getQuery());
            String body = request == 0 ? """
                    {"data":[{"id":5,"projectId":42,"text":"Unpinned first from provider","author":{"displayName":"Reader"}},
                      {"id":1,"projectId":42,"text":"First","datePosted":1787419517100,
                      "isPinned":true,"author":{"displayName":"Builder","twitchAvatarUrl":"https://static-cdn.jtvnw.net/avatar-{0}.png"},
                      "replies":[{"id":2,"projectId":42,"text":"Nested","author":{"username":"helper",
                        "twitchAvatarUrl":"https://evil.example/avatar.png"}}]}],
                     "pagination":{"index":0,"totalCount":40,"pageSize":20}}
                    """ : """
                    {"data":[{"id":3,"projectId":42,"text":"Second","isPinned":true,"author":{"username":"reader"}},
                     {"id":4,"projectId":999,"text":"Wrong project","author":{"username":"other"}}],
                     "pagination":{"index":1,"totalCount":40,"pageSize":20}}
                    """;
            respond(exchange, 200, body);
        });
        server.start();

        var firstPage = client.getComments(42, 0);
        var secondPage = client.getComments(42, 1);
        List<ProjectComment> comments = firstPage.comments();

        assertEquals(2, requests.get());
        assertEquals(2, comments.size());
        assertEquals(40, firstPage.totalCount());
        assertTrue(firstPage.hasMore());
        ProjectComment first = comments.getFirst();
        assertEquals("curseforge:1", first.id());
        assertEquals("Builder", first.user());
        assertEquals("https://static-cdn.jtvnw.net/avatar-70x70.png", first.author().avatarUrl());
        assertTrue(first.pinned());
        assertTrue(first.readOnly());
        assertEquals("Nested", first.replies().getFirst().content());
        assertNull(first.replies().getFirst().author().avatarUrl());
        assertEquals("Unpinned first from provider", comments.get(1).content());
        assertEquals("Second", secondPage.comments().getFirst().content());
        assertTrue(!secondPage.hasMore());
        assertEquals(
                List.of("curseforge:1", "curseforge:3", "curseforge:5"),
                CurseForgeCommentsPage.pinnedFirst(Stream.concat(
                                firstPage.comments().stream(), secondPage.comments().stream())
                        .toList()).stream().map(ProjectComment::id).toList()
        );
    }

    @Test
    void rejectsInvalidAndFailedProviderResponses() {
        server.createContext("/api/v1/mods/43/comments", exchange -> respond(exchange, 200, "{\"comments\":[]}"));
        server.createContext("/api/v1/mods/44/comments", exchange -> respond(exchange, 403, "blocked"));
        server.createContext("/api/v1/mods/45/comments", exchange -> respond(exchange, 200, "not-json"));
        server.start();

        assertThrows(ModtaleApiException.class, () -> client.getComments(43, 0));
        assertEquals(403, assertThrows(ModtaleApiException.class, () -> client.getComments(44, 0)).statusCode());
        assertThrows(ModtaleApiException.class, () -> client.getComments(45, 0));
        assertThrows(IllegalArgumentException.class, () -> client.getComments(0, 0));
        assertThrows(IllegalArgumentException.class, () -> client.getComments(42, -1));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
