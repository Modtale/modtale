package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModtaleApiClientCacheTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void cachesProjectSearchResponses() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer("/api/v1/projects", exchange -> {
            requests.incrementAndGet();
            respond(exchange, """
                    {
                      "content": [
                        {
                          "id": "project-one",
                          "slug": "project-one",
                          "title": "Project One",
                          "versions": []
                        }
                      ],
                      "totalPages": 1,
                      "totalElements": 1,
                      "number": 0,
                      "last": true
                    }
                    """);
        });
        ModtaleApiClient client = client();
        ProjectSearchQuery query = new ProjectSearchQuery("", "", "", "downloads", 0, 20,
                "", null, null, "", null, null);

        assertEquals("Project One", client.searchProjects(query).content().getFirst().title());
        assertEquals("Project One", client.searchProjects(query).content().getFirst().title());

        assertEquals(1, requests.get());
    }

    @Test
    void doesNotCacheSignedDownloadUrls() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer("/api/v1/projects/project-one/versions/1.0.0/download-url", exchange -> {
            int requestNumber = requests.incrementAndGet();
            respond(exchange, """
                    {"downloadUrl":"https://cdn.example.test/download-%d.jar","expiresIn":60}
                    """.formatted(requestNumber));
        });
        ModtaleApiClient client = client();

        assertEquals("https://cdn.example.test/download-1.jar",
                client.getDownloadUrl("project-one", "1.0.0", "").downloadUrl());
        assertEquals("https://cdn.example.test/download-2.jar",
                client.getDownloadUrl("project-one", "1.0.0", "").downloadUrl());

        assertEquals(2, requests.get());
    }

    @Test
    void clearResponseCacheForcesNextPublicGetToRefetch() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer("/api/v1/projects", exchange -> {
            int requestNumber = requests.incrementAndGet();
            respond(exchange, """
                    {
                      "content": [
                        {
                          "id": "project-one",
                          "slug": "project-one",
                          "title": "Project %d",
                          "versions": []
                        }
                      ],
                      "totalPages": 1,
                      "totalElements": 1,
                      "number": 0,
                      "last": true
                    }
                    """.formatted(requestNumber));
        });
        ModtaleApiClient client = client();
        ProjectSearchQuery query = new ProjectSearchQuery("", "", "", "downloads", 0, 20,
                "", null, null, "", null, null);

        assertEquals("Project 1", client.searchProjects(query).content().getFirst().title());
        assertEquals("Project 1", client.searchProjects(query).content().getFirst().title());

        client.clearResponseCache();

        assertEquals("Project 2", client.searchProjects(query).content().getFirst().title());
        assertEquals(2, requests.get());
    }

    @Test
    void sendsOpenSourceProjectSearchFilter() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        startServer("/api/v1/projects", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, """
                    {
                      "content": [],
                      "totalPages": 0,
                      "totalElements": 0,
                      "number": 0,
                      "last": true
                    }
                    """);
        });
        ModtaleApiClient client = client();
        ProjectSearchQuery query = new ProjectSearchQuery("", "", "", "downloads", 0, 20,
                "", null, null, "", null, true);

        client.searchProjects(query);

        assertTrue(rawQuery.get().contains("openSource=true"));
    }

    private ModtaleApiClient client() {
        return new ModtaleApiClient(HttpClient.newHttpClient(), baseUrl(), new ApiResponseCache(tempDir));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    private void startServer(String path, ThrowingHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } catch (Throwable ex) {
                byte[] body = ex.getMessage().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Throwable;
    }
}
