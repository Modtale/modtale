package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import net.modtale.launcher.model.project.ProjectPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModtaleApiClientCurseForgeTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void mapsBrowseFiltersAndNeverCachesCurseForgeResponses() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/v1/projects/external/curseforge", exchange -> {
            requests.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            assertTrue(query.contains("page=2"));
            assertTrue(query.contains("size=20"));
            assertTrue(query.contains("sort=updated"));
            assertTrue(query.contains("search=compost"));
            assertTrue(query.contains("gameVersion=2026.09"));
            byte[] body = """
                    {"content":[{"id":"curseforge:1450386","slug":"curseforge:1450386","title":"Simple Compost","description":"Compost things","author":"Builder","classification":"MOD","source":"CURSEFORGE","websiteUrl":"https://www.curseforge.com/hytale/mods/simple-compost","distributionAllowed":true,"versions":[{"id":"8227810","versionNumber":"1.2.0","gameVersions":["2026.09"],"channel":"RELEASE"}]}],"totalPages":3,"totalElements":41,"number":2,"last":true}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ModtaleApiClient client = new ModtaleApiClient(baseUrl());
        ProjectSearchQuery query = new ProjectSearchQuery("compost", null, "2026.09", "updated", 2, 20,
                null, null, null, null, null, null);

        ProjectPage first = client.searchCurseForgeMods(query);
        ProjectPage second = client.searchCurseForgeMods(query);

        assertEquals(2, requests.get());
        assertTrue(first.content().getFirst().isCurseForge());
        assertEquals(1450386, first.content().getFirst().curseForgeProjectId());
        assertFalse(first.content().getFirst().versions().isEmpty());
        assertEquals(first, second);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }
}
