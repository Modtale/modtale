package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.modtale.launcher.model.project.ProjectMeta;
import org.junit.jupiter.api.Test;

class ModtaleApiClientMetadataTest {
    @Test
    void separatesProvidersAndLoadsEveryBatchDespiteUnavailableCurseForgeProject() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger batches = new AtomicInteger();
        server.createContext("/api/v1/projects/meta", exchange -> {
            batches.incrementAndGet();
            String query = java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            assertFalse(query.contains("curseforge:"));
            byte[] body = "{\"modtale-0\":{\"title\":\"Modtale Mod\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            List<String> providerRequests = new ArrayList<>();
            ModtaleApiClient client = new ModtaleApiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1") {
                @Override
                public ProjectMeta getProjectMeta(String id) {
                    providerRequests.add(id);
                    if (id.equals("curseforge:1")) throw new ModtaleApiException("Unavailable");
                    return new ProjectMeta("CurseForge Mod", "Summary", "icon", "Author", "MOD", 10, "", id);
                }
            };
            List<String> ids = new ArrayList<>();
            for (int index = 0; index < 51; index++) ids.add("modtale-" + index);
            ids.addAll(List.of("curseforge:1", "curseforge:2", "curseforge:2"));
            var result = client.getProjectMetaBatch(ids);
            assertEquals(2, batches.get());
            assertEquals(List.of("curseforge:1", "curseforge:2"), providerRequests);
            assertEquals("Modtale Mod", result.get("modtale-0").title());
            assertEquals("CurseForge Mod", result.get("curseforge:2").title());
            assertFalse(result.containsKey("curseforge:1"));
        } finally {
            server.stop(0);
        }
    }
}
