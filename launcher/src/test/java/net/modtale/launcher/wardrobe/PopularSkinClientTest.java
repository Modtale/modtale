package net.modtale.launcher.wardrobe;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PopularSkinClientTest {
    private static final String HASH = "aa6a8b217c26ee19a02791eb2064b513";
    private HttpServer server;
    private URI origin;
    private PopularSkinClient client;
    private final List<String> requests = new ArrayList<>();
    private String html = "<h1>Skin archive</h1><a href='/skin/" + HASH + "'>Skin</a>";
    private String skin = "{\"bodyCharacteristic\":\"Muscular.15\",\"cape\":null}";
    private int status = 200;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            assertEquals("GET", exchange.getRequestMethod());
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
            assertEquals(0, exchange.getRequestBody().readAllBytes().length);
            boolean json = exchange.getRequestURI().getPath().startsWith("/api/skin/");
            byte[] bytes = (json ? skin : html).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", json ? "application/json" : "text/html");
            exchange.getResponseHeaders().set("Location", "/unwanted");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        client = new PopularSkinClient(HttpClient.newHttpClient(), origin);
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void browsesPopularityAndDeduplicatesOnlySameOriginSkinLinks() {
        html += "<a href='" + origin + "skin/" + HASH + "'>Duplicate</a><a href='https://evil.test/skin/00000000000000000000000000000000'>External</a>";
        var page = client.page(1);
        assertEquals(1, page.items().size()); assertFalse(page.hasNext());
        assertSame(page, client.page(1));
        assertEquals(List.of("/skins?page=1&sort=user_count&order=desc"), requests);
        assertEquals("https://hyvatar.io/render/full/NPC?size=256&skin_id=" + HASH, client.thumbnail(HASH));
        assertThrows(IllegalArgumentException.class, () -> client.thumbnail("../username"));
    }

    @Test void followsPaginationOnlyWhenProviderSuppliesNextPage() {
        html = "<h1>Skin archive</h1>";
        for (int i = 0; i < 20; i++) html += "<a href='/skin/" + String.format("%032x", i) + "'>Skin</a>";
        html += "<a href='/skins?page=2' rel='next'>Next</a>";
        assertTrue(client.page(1).hasNext());
        assertEquals(20, client.page(1).items().size());
        html = "<h1>Skin archive</h1>";
        assertTrue(client.page(2).items().isEmpty()); assertFalse(client.page(2).hasNext());
    }

    @Test void downloadProducesCompleteOfflineSkinWithoutIdentityOrImageUrl() throws Exception {
        var item = client.download(HASH);
        var payload = new ObjectMapper().readTree(item.payload());
        assertEquals("Muscular.15", payload.path("skin").path("bodyCharacteristic").asText());
        assertEquals(Set.of("skinId", "skin"), payload.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        var official = new WardrobeApiClient(new net.modtale.launcher.hytale.HytaleAuthService(null, null));
        assertEquals(item, official.hydrate(item));
        assertEquals(List.of("/api/skin/" + HASH), requests);
    }

    @Test void rejectsUnavailableFeedRedirectsAndInvalidDefinitions() {
        html = "<html>Challenge</html>";
        assertThrows(IllegalStateException.class, () -> client.page(1));
        status = 302;
        assertThrows(IllegalStateException.class, () -> client.page(1));
        assertFalse(requests.contains("/unwanted")); status = 200;
        for (String invalid : List.of("null", "{}", "{\"bodyCharacteristic\":\"Default\",\"username\":\"Person\"}", "{\"bodyCharacteristic\":\"../bad\"}")) {
            skin = invalid; assertThrows(RuntimeException.class, () -> client.download(HASH));
        }
        assertThrows(IllegalArgumentException.class, () -> client.page(0));
        assertThrows(IllegalArgumentException.class, () -> client.download("../../profile"));
    }

    @Test void refusesCredentialsAndForeignOrigins() {
        assertThrows(IllegalArgumentException.class, () -> new PopularSkinClient(HttpClient.newBuilder().cookieHandler(new CookieManager()).build(), origin));
        assertThrows(IllegalArgumentException.class, () -> new PopularSkinClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), origin));
        assertThrows(IllegalArgumentException.class, () -> new PopularSkinClient(HttpClient.newHttpClient(), URI.create("https://evil.test/")));
    }
}
