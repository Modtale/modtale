package net.modtale.launcher.api;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CurseForgeClientTest {
    HttpServer server;
    CurseForgeClient client;
    List<String> requests = new ArrayList<>();
    String file = """
        {"id":123,"modId":42,"gameId":70216,"isAvailable":true,"fileName":"example.jar",
        "fileLength":100,"downloadUrl":"https://edge.forgecdn.net/files/1/example.jar",
        "hashes":[{"algo":1,"value":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}
        """;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String data = switch(exchange.getRequestURI().getPath()) {
                case "/mods/search" -> """
                    {"data":[{"id":42,"gameId":70216,"name":"Provider first","downloadCount":1,"allowModDistribution":false},
                    {"id":43,"gameId":70216,"name":"Provider second","downloadCount":999}],
                    "pagination":{"totalCount":125}}
                    """;
                case "/mods/42/files/123" -> "{\"data\":" + file + "}";
                case "/mods/42" -> """
                    {"data":{"id":42,"gameId":70216,"isAvailable":true,"name":"Example","authors":[{"name":"Creator"}],
                    "links":{"websiteUrl":"https://www.curseforge.com/hytale/mods/example"},
                    "logo":{"thumbnailUrl":"https://media.forgecdn.net/icon.png"}}}
                    """;
                case "/mods/42/description" -> "{\"data\":\"<p>Description</p>\"}";
                case "/mods/42/files" -> "{\"data\":[]}";
                default -> "{\"data\":null}";
            };
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new CurseForgeClient(HttpClient.newHttpClient(), new ApiResponseCache(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void everySortUsesProviderOrderAndGlobalPagination() {
        Map<String,Integer> sorts = Map.of("relevance",1,"popular",2,"updated",3,"name",4,"author",5,"downloads",6,"newest",11);
        for (var sort : sorts.entrySet()) {
            var page = client.search(new ProjectSearchQuery("some mod", "worlds", "0.5", sort.getKey(), 2, 20, "", null, null, "", null, null));
            assertEquals(List.of("Provider first", "Provider second"), page.content().stream().map(p -> p.title()).toList());
            assertEquals(7, page.totalPages());
            assertEquals(125, page.totalElements());
            assertFalse(page.content().getFirst().distributionAllowed());
            String path = requests.getLast();
            assertTrue(path.contains("index=40"));
            assertTrue(path.contains("sortField=" + sort.getValue()));
            assertTrue(path.contains("classId=9184"));
            assertTrue(path.contains("gameVersion=0.5"));
            assertTrue(path.contains("searchFilter=some+mod"));
            assertTrue(path.contains("sortOrder=" + (Set.of("name","author").contains(sort.getKey()) ? "asc" : "desc")));
        }
    }
    @Test void detailsAndDownloadsUseSameProviderAndPreserveVerifiedIdentity() {
        assertEquals("Creator", client.projectMeta(42).author());
        assertEquals("<p>Description</p>", client.project(42).about());
        var result = client.download(42,123);
        assertEquals("example.jar", result.fileName());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result.hashes().get("sha1"));
        assertTrue(result.downloadUrl().startsWith("https://edge.forgecdn.net/"));
    }
    @Test void rejectsMismatchedOrUnavailableFilesAndUntrustedDownloadUrls() {
        file = file.replace("\"modId\":42", "\"modId\":43");
        assertThrows(ModtaleApiException.class, () -> client.download(42,123));
        file = file.replace("\"modId\":43", "\"modId\":42").replace("\"isAvailable\":true", "\"isAvailable\":false");
        assertThrows(ModtaleApiException.class, () -> client.download(42,123));
        file = file.replace("\"isAvailable\":false", "\"isAvailable\":true").replace("edge.forgecdn.net", "evil.example");
        assertEquals("https://www.curseforge.com/api/v1/mods/42/files/123/download", client.download(42,123).downloadUrl());
    }
}
