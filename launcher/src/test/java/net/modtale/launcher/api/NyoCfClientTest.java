package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import net.modtale.launcher.model.project.DownloadUrlResponse;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NyoCfClientTest {

    private HttpServer server;
    private NyoCfClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        client = new NyoCfClient(HttpClient.newHttpClient(), URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsBrowseDetailVersionsAndExactVerifiedDownload() {
        AtomicInteger browseRequests = new AtomicInteger();
        AtomicInteger detailRequests = new AtomicInteger();
        server.createContext("/api/v1/hytale/mods/search", exchange -> {
            browseRequests.incrementAndGet();
            assertTrue(exchange.getRequestURI().getRawQuery().contains("q=compost"));
            assertTrue(exchange.getRequestURI().getRawQuery().contains("include_files=true"));
            respond(exchange, """
                {"data":[{"id":1450386,"slug":"simple-compost","name":"Simple Compost","summary":"Compost things","primary_author":"Builder","logo_thumbnail_url":"https://media.forgecdn.net/icon.png","download_count":798,"recent_files":[{"id":8747324,"display_name":"SimpleCompost 1.0.0","release_type":"release","file_date":"2026-08-27T15:11:51Z","download_count":18,"game_versions":["Early Access"]}]}],"pagination":{"total":1}}
                """);
        });
        server.createContext("/api/v1/hytale/mods/1450386/files/8747324", exchange -> respond(exchange, """
                {"id":8747324,"mod_id":1450386,"game_id":70216,"is_available":true,"file_name":"SimpleCompost-1.0.0.jar","file_length":100897,"hashes":{"sha1":"298f05d4294ad6573f1860239b667f76ab510716","md5":"fa6046b286a1823bd4d13789bdd58157"}}
                """));
        server.createContext("/api/v1/hytale/mods/1450386/files", exchange -> respond(exchange, """
                [{"id":8747324,"display_name":"SimpleCompost 1.0.0","release_type":"release","file_date":"2026-08-27T15:11:51Z","file_length":100897,"download_count":18,"game_versions":["Early Access"]}]
                """));
        server.createContext("/api/v1/hytale/mods/1450386/description", exchange -> respond(exchange,
                "{\"description\":\"<p>Rich project description</p>\"}"));
        server.createContext("/api/v1/hytale/mods/1450386", exchange -> {
            detailRequests.incrementAndGet();
            respond(exchange, """
                {"id":1450386,"game_id":70216,"is_available":true,"name":"Simple Compost","slug":"simple-compost","summary":"Compost things","download_count":798,"logo":{"thumbnail_url":"https://media.forgecdn.net/icon.png"},"links":{"website":"https://www.curseforge.com/hytale/mods/simple-compost"},"authors":[{"name":"Builder"}],"categories":[{"name":"Gameplay"}],"screenshots":[{"url":"https://media.forgecdn.net/screenshot.png","thumbnail_url":"https://media.forgecdn.net/screenshot-thumb.png"}],"dates":{"modified":"2026-08-27T15:17:03Z"}}
                """);
        });

        ProjectPage browse = client.search(new ProjectSearchQuery("compost", "mods", "Early Access", "downloads",
                0, 20, null, null, null, null, null, null));
        client.search(new ProjectSearchQuery("compost", "mods", "Early Access", "downloads",
                0, 20, null, null, null, null, null, null));
        ProjectDetail detail = client.project(1450386);
        DownloadUrlResponse download = client.download(1450386, 8747324);

        assertEquals("curseforge:1450386", browse.content().getFirst().routeKey());
        assertEquals("https://media.forgecdn.net/screenshot-thumb.png", browse.content().getFirst().bannerUrl());
        assertEquals(2, browseRequests.get());
        assertTrue(browse.content().getFirst().isCurseForge());
        assertEquals("<p>Rich project description</p>", detail.about());
        assertEquals("https://media.forgecdn.net/screenshot.png", detail.bannerUrl());
        assertEquals(1, detailRequests.get());
        assertEquals("8747324", detail.versions().getFirst().id());
        assertEquals("https://www.curseforge.com/api/v1/mods/1450386/files/8747324/download",
                download.downloadUrl());
        assertEquals(100897L, download.fileSize());
        assertEquals("298f05d4294ad6573f1860239b667f76ab510716", download.hashes().get("sha1"));
    }

    @Test
    void routesCurseForgePillSelectionsToTheirProjectClass() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/v1/hytale/prefabs/search", exchange -> {
            requests.incrementAndGet();
            assertTrue(exchange.getRequestURI().getRawQuery().contains("q=castle"));
            respond(exchange, "{\"data\":[],\"pagination\":{\"total\":0}}");
        });

        ProjectPage result = client.search(new ProjectSearchQuery(
                "castle", "prefabs", null, "downloads", 0, 12,
                null, null, null, null, null, null));

        assertEquals(1, requests.get());
        assertEquals(0, result.totalElements());
    }

    @Test
    void allSelectionCombinesEveryCurseForgeProjectClass() {
        AtomicInteger requests = new AtomicInteger();
        for (String projectClass : java.util.List.of("mods", "prefabs", "worlds", "bootstrap", "translations")) {
            server.createContext("/api/v1/hytale/" + projectClass + "/search", exchange -> {
                requests.incrementAndGet();
                respond(exchange, "{\"data\":[],\"pagination\":{\"total\":2}}");
            });
        }

        ProjectPage result = client.search(new ProjectSearchQuery(
                "", "", null, "downloads", 0, 12,
                null, null, null, null, null, null));

        assertEquals(5, requests.get());
        assertEquals(10, result.totalElements());
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
