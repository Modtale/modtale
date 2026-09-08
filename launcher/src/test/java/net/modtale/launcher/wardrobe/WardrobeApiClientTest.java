package net.modtale.launcher.wardrobe;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.*;

class WardrobeApiClientTest {
    private static final String ID = "3c540c31-5e8e-4039-83ea-33bed2dd65d3";
    private static final String HASH = "8647343d887f1ca8b21ccb8a70b2446f";
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> headers = Collections.synchronizedList(new ArrayList<>());
    private HttpServer server;
    private URI base;
    private WardrobeApiClient api;
    private int tokenCalls;
    private final List<String> methods = Collections.synchronizedList(new ArrayList<>());
    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    private Runnable afterSlots;
    private Runnable duringSessionRefresh;
    private Runnable duringRejectedSessionRenewal;
    private int renewals;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            assertEquals("ModtaleLauncher/1.0", exchange.getRequestHeaders().getFirst("User-Agent"));
            requests.add(path);
            methods.add(exchange.getRequestMethod());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if ((path.startsWith("/player-skins?") || path.equals("/player-skins")) && afterSlots != null) afterSlots.run();
            headers.add(exchange.getRequestHeaders().getFirst("Authorization"));
            Reply reply = replies.getOrDefault(path, new Reply(404, "application/json", "{}"));
            byte[] bytes = reply.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", reply.type);
            exchange.sendResponseHeaders(reply.status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        HytaleAuthService auth = new HytaleAuthService(null, null) {
            @Override public String freshAccessToken(LauncherSettings settings) { tokenCalls++; return "official-oauth"; }
            @Override public String freshSessionToken(LauncherSettings settings) { tokenCalls++; if (duringSessionRefresh != null) duringSessionRefresh.run(); return "official-session"; }
            @Override public String renewRejectedSessionToken(LauncherSettings settings, String profile, String rejected) {
                assertEquals(ID, profile);
                assertEquals("official-session", rejected);
                renewals++;
                if (duringRejectedSessionRenewal != null) duringRejectedSessionRenewal.run();
                return "renewed-session";
            }
        };
        api = new WardrobeApiClient(HttpClient.newHttpClient(), auth, base, base);
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void rejectedCurrentLookSessionIsRenewedAndApplyUsesReplacement() {
        replies.put("/player-skins", new Reply(403, "text/plain", "invalid token\n"));
        duringRejectedSessionRenewal = () -> officialSlots(5);
        api.apply(new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN, "Test", false, "",
                "{\"skin\":{\"bodyCharacteristic\":\"Default.01\"}}"), settings());
        assertEquals(1, renewals);
        assertEquals(List.of("Bearer official-session", "Bearer renewed-session", "Bearer renewed-session"), headers);
        assertEquals(List.of("GET", "GET", "PUT"), methods);
    }

    @Test void rejectedOwnershipSessionIsRenewedOnce() {
        replies.put("/my-account/cosmetics", new Reply(403, "text/plain", "invalid token\n"));
        duringRejectedSessionRenewal = () -> json("/my-account/cosmetics", "{\"cape\":[\"Cape_Royal_Emissary\"]}");
        assertEquals(Set.of("Cape_Royal_Emissary"), api.unlockedCosmetics(settings()).get("cape"));
        assertEquals(1, renewals);
        assertEquals(List.of("Bearer official-session", "Bearer renewed-session"), headers);
    }

    @Test void persistentRejectionStopsAfterOneRetry() {
        replies.put("/player-skins", new Reply(403, "text/plain", "invalid token\n"));
        assertThrows(IllegalStateException.class, () -> api.currentSkin(settings()));
        assertEquals(1, renewals);
        assertEquals(2, requests.size());
    }

    @Test void ordinaryForbiddenDoesNotRenewSession() {
        replies.put("/my-account/cosmetics", new Reply(403, "text/html", "Forbidden"));
        assertThrows(IllegalStateException.class, () -> api.unlockedCosmetics(settings()));
        assertEquals(0, renewals);
        assertEquals(1, requests.size());
    }

    @Test void rejectedApplyIsNeverRepeated() {
        officialSlots(5);
        replies.put("/player-skins/" + SLOT, new Reply(403, "text/plain", "invalid token\n"));
        WardrobeItem item = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN, "Test", false, "",
                "{\"skin\":{\"bodyCharacteristic\":\"Default.01\"}}");
        assertThrows(IllegalStateException.class, () -> api.apply(item, settings()));
        assertEquals(0, renewals);
        assertEquals(List.of("GET", "PUT"), methods);
    }

    @Test void accountChangeDuringRenewalStopsRetry() {
        LauncherSettings settings = settings();
        replies.put("/player-skins", new Reply(403, "text/plain", "invalid token\n"));
        duringRejectedSessionRenewal = () -> settings.getHytaleAuthSession().setUuid(UUID.randomUUID().toString());
        assertThrows(IllegalStateException.class, () -> api.currentSkin(settings));
        assertEquals(1, renewals);
        assertEquals(1, requests.size());
    }

    @Test void usernameLookupPreservesCosmeticsAndNeverSendsOfficialTokenToHyTags() throws Exception {
        json("/api/username/KayNeko", profile(ID, "KayNeko"));
        archivedSkin();
        html("/username/KayNeko", "<a class='wardrobe-card is-current' href='/skin/" + HASH + "'>Current</a>");
        WardrobeItem item = api.lookupSkin("KayNeko");
        var payload = new ObjectMapper().readTree(item.payload());
        assertEquals("Muscular.01", payload.path("skin").path("bodyCharacteristic").asText());
        assertTrue(payload.path("skin").path("cape").isNull());
        assertEquals(ID, payload.path("playerUuid").asText());
        assertEquals("https://hyvatar.io/render/full/NPC?size=256&skin_id=" + HASH + "", payload.path("thumbnailUrl").asText());
        assertEquals(item.id(), api.lookupSkin("KayNeko").id());
        assertEquals(UUID.fromString(ID), api.profile("KayNeko").uuid());
        assertEquals(0, tokenCalls);
        assertTrue(headers.stream().allMatch(Objects::isNull));
    }
    @Test void malformedOrWrongProfilesFailInsteadOfProducingEmptySkins() {
        for (String body : List.of("{}", "null", "[]", "not-json", profile(ID, "OtherName"),
                "{\"uuid\":\"bad\",\"username\":\"KayNeko\"}",
                "{\"uuid\":\"" + ID + "\",\"username\":\"KayNeko\",\"skin\":null}")) {
            json("/api/username/KayNeko", body);
            assertThrows(RuntimeException.class, () -> api.lookupSkin("KayNeko"));
        }
    }
    @Test void failedSkinLookupsRejectErrorsAndInvalidNames() {
        for (int status : List.of(401, 403, 404, 429, 500)) {
            replies.put("/api/username/KayNeko", new Reply(status, "application/json", "secret upstream body"));
            Exception ex = assertThrows(IllegalStateException.class, () -> api.lookupSkin("KayNeko"));
            assertFalse(ex.getMessage().contains("secret"));
        }
        assertThrows(IllegalArgumentException.class, () -> api.lookupSkin("../../bad"));
    }
    @Test void catalogUsesPublishedPaginationAndSortAndDeduplicatesLocalLinks() throws Exception {
        String body = "<html><body><h2>Skin archive</h2><select name='sort'><option value='users'>Most used</option></select>"
                + "<a href='/skin/" + HASH + "'>Skin</a><a href='/skin/" + HASH + "'>duplicate</a>"
                + "<a href='https://evil.test/skin/" + HASH + "'>external</a></body></html>";
        html("/skins?page=2", body);
        html("/skins?page=2&sort=users", body);
        archivedSkin();
        List<WardrobeItem> items = api.browseSkins(2, "Most used");
        assertEquals(List.of("/skins?page=2", "/skins?page=2&sort=users"), requests, "Browsing should not fetch every cosmetic definition; hydrate on save or apply");
        assertEquals(1, items.size());
        assertEquals(HASH, new ObjectMapper().readTree(items.getFirst().payload()).path("skinId").asText());
        assertThrows(IllegalArgumentException.class, () -> api.browseSkins(2, "invented"));
        assertThrows(IllegalArgumentException.class, () -> api.browseSkins(0, "default"));
    }
    @Test void uuidLookupUsesOfficialSessionAndVerifiesIdentity() throws Exception {
        json("/profile/uuid/" + ID, profile(ID, "Renamed"));
        assertEquals("Renamed", api.profile(UUID.fromString(ID), new LauncherSettings()).username());
        assertEquals("Bearer official-session", headers.getFirst());
        assertEquals(1, tokenCalls);
        json("/profile/uuid/" + ID, profile("11111111-1111-1111-1111-111111111111", "WrongPlayer"));
        assertThrows(IllegalStateException.class, () -> api.profile(UUID.fromString(ID), new LauncherSettings()).username());
    }
    @Test void skinApplyWritesSerializedDefinitionToActiveSlotAndPreservesName() throws Exception {
        archivedSkin();
        slots();
        api.apply(api.lookupSkinHash(HASH), settings());
        assertEquals("PUT", methods.getLast());
        assertEquals("/player-skins/" + SLOT, requests.getLast());
        assertEquals("Bearer official-session", headers.getLast());
        var body = new ObjectMapper().readTree(bodies.getLast());
        assertEquals("My original slot", body.path("name").asText());
        assertTrue(body.path("skinData").isTextual());
        assertEquals("Muscular.01", new ObjectMapper().readTree(body.path("skinData").asText()).path("bodyCharacteristic").asText());
    }
    @Test void capeApplyPreservesOtherCosmeticsAndCurrentSkinProvidesRestoreSnapshot() throws Exception {
        slots();
        LauncherSettings settings = settings();
        WardrobeItem backup = api.currentSkin(settings);
        var payload = new ObjectMapper().readTree(backup.payload());
        assertFalse(payload.has("username"));
        var cape = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.CAPE, "Cape", false, "", "{\"cape\":\"Cape_Forest_Guardian.Green.Neck_Piece\"}");
        api.apply(cape, settings);
        var skin = new ObjectMapper().readTree(new ObjectMapper().readTree(bodies.getLast()).path("skinData").asText());
        assertEquals("Original", skin.path("bodyCharacteristic").asText());
        assertEquals("Cape_Forest_Guardian.Green.Neck_Piece", skin.path("cape").asText());
        api.apply(backup, settings);
        var restored = new ObjectMapper().readTree(new ObjectMapper().readTree(bodies.getLast()).path("skinData").asText());
        assertEquals(payload.path("skin"), restored);
    }
    @Test void hydratePreservesLocalMetadataAndDoesNotResolveMovingUsernames() throws Exception {
        archivedSkin();
        UUID id = UUID.randomUUID();
        var input = new WardrobeItem(id, WardrobeItem.Kind.SKIN, "Favorite", true, "Collection", "{\"skinId\":\"" + HASH + "\",\"username\":\"OldName\"}");
        var saved = api.hydrate(input);
        assertEquals(id, saved.id()); assertEquals("Favorite", saved.name()); assertTrue(saved.favorite());
        assertEquals("Collection", saved.collection());
        assertTrue(new ObjectMapper().readTree(saved.payload()).path("skin").isObject());
        assertEquals(List.of("/api/skin/" + HASH), requests);
        assertEquals(saved, api.hydrate(saved));
        assertEquals(1, requests.size());
    }
    @Test void targetChangeAndMissingSlotBlockWrites() {
        slots();
        LauncherSettings settings = settings();
        afterSlots = settings::removeActiveHytaleAuthSession;
        var cape = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.CAPE, "Cape", false, "", "{\"cape\":null}");
        assertThrows(IllegalStateException.class, () -> api.apply(cape, settings));
        assertFalse(methods.contains("PUT"));
        afterSlots = null;
        json("/player-skins", "{\"activeSkin\":\"missing\",\"skins\":[]}");
        assertThrows(IllegalStateException.class, () -> api.apply(cape, settings()));
        assertFalse(methods.contains("PUT"));
    }
    @Test void rejectedWriteIsNotReportedAsSuccess() {
        slots();
        replies.put("/player-skins/" + SLOT, new Reply(403, "application/json", "{}"));
        var cape = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.CAPE, "Cape", false, "", "{\"cape\":null}");
        assertThrows(IllegalStateException.class, () -> api.apply(cape, settings()));
    }
    @Test void backupTargetMustStillBeSelectedBeforeApplyStarts() {
        var cape = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.CAPE, "Cape", false, "", "{\"cape\":null}");
        assertThrows(IllegalStateException.class, () -> api.apply(cape, settings(), UUID.randomUUID()));
        assertTrue(requests.isEmpty());
        assertEquals(0, tokenCalls);
    }
    @Test void changedUsernameSkinDoesNotPairSavedDefinitionWithWrongPreview() {
        json("/api/username/KayNeko", profile(ID, "KayNeko"));
        html("/username/KayNeko", "<a class='wardrobe-card is-current' href='/skin/" + HASH + "'>Current</a>");
        json("/api/skin/" + HASH, "{\"bodyCharacteristic\":\"Different\",\"cape\":null}");
        assertThrows(IllegalStateException.class, () -> api.lookupSkin("KayNeko"));
    }
    private LauncherSettings settings() {
        var settings = new LauncherSettings();
        var session = new net.modtale.launcher.hytale.HytaleAuthSession();
        session.setUuid(ID); session.setUsername("KayNeko"); session.setRefreshToken("refresh");
        settings.setHytaleAuthSession(session);
        return settings;
    }
    private void slots() {
        try {
            var mapper = new ObjectMapper();
            var root = mapper.createObjectNode().put("activeSkin", SLOT).put("maxSkins", 5);
            root.putArray("skins").addObject().put("id", SLOT).put("name", "My original slot")
                    .put("skinData", "{\"bodyCharacteristic\":\"Original\",\"cape\":null}");
            json("/player-skins", root.toString());
            json("/player-skins/" + SLOT, "{}");
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static final String SLOT = "a64f44a1-aaf4-455e-a1f2-589989ca2a92";
    private static final String OTHER_SLOT = "b64f44a1-aaf4-455e-a1f2-589989ca2a92";

    private void officialSlots(int max) {
        var root = new ObjectMapper().createObjectNode().put("activeSkin", SLOT).put("maxSkins", max);
        root.putArray("skins").addObject().put("id", SLOT).put("name", "Adventure")
                .put("skinData", "{\"bodyCharacteristic\":\"Default.01\",\"cape\":null}");
        json("/player-skins", root.toString());
        json("/player-skins/" + SLOT, "{}");
        json("/player-skins/active", "{}");
    }

    @Test void officialSlotsPreserveSerializedSnapshotsAndCapacity() {
        officialSlots(5);
        var result = api.slots(settings());
        assertEquals(SLOT, result.activeId());
        assertEquals(5, result.max());
        assertEquals("Adventure", result.slots().getFirst().name());
        assertEquals("{\"bodyCharacteristic\":\"Default.01\",\"cape\":null}", result.slots().getFirst().skinData());
        assertEquals(List.of("/player-skins"), requests);
        assertEquals(List.of("Bearer official-session"), headers);
        assertThrows(UnsupportedOperationException.class, () -> result.slots().clear());
    }

    @Test void malformedSlotResponsesFailClosed() {
        for (String response : List.of("null", "{}", "{\"activeSkin\":null,\"maxSkins\":-1,\"skins\":[]}",
                "{\"activeSkin\":\"missing\",\"maxSkins\":5,\"skins\":[]}",
                "{\"activeSkin\":null,\"maxSkins\":5.5,\"skins\":[]}")) {
            json("/player-skins", response);
            assertThrows(IllegalStateException.class, () -> api.slots(settings()));
        }
        json("/player-skins", "{\"activeSkin\":null,\"maxSkins\":5,\"skins\":[]}");
        assertTrue(api.slots(settings()).slots().isEmpty());
    }

    @Test void permissionsUseOfficialGameSessionAndRejectMalformedOrWrongProfile() {
        json("/my-account/cosmetics", "{\"cape\":[\"Cape_Royal_Emissary\"],\"haircut\":[]}");
        json("/my-account/cosmetics", "{\"cape\":[\"Cape_Royal_Emissary\"],\"haircut\":[],\"cardBackground\":null}");
        var cosmetics = api.unlockedCosmetics(settings());
        assertEquals(Set.of(), cosmetics.get("cardBackground"));
        assertEquals(Set.of("Cape_Royal_Emissary"), cosmetics.get("cape"));
        assertThrows(UnsupportedOperationException.class, () -> cosmetics.get("cape").clear());
        assertTrue(headers.stream().allMatch("Bearer official-session"::equals));
        for (String response : List.of("null", "[]", "{\"cape\":[42]}")) {
            json("/my-account/cosmetics", response);
            assertThrows(IllegalStateException.class, () -> api.unlockedCosmetics(settings()));
        }
    }

    private void archivedSkin() {
        json("/api/skin/" + HASH, "{\"bodyCharacteristic\":\"Muscular.01\",\"cape\":null}");
    }
    @Test void inputAndTransportRejectUntrustedDestinationsAndNonJson() {
        assertThrows(IllegalArgumentException.class, () -> api.lookupSkin("../../secret"));
        assertTrue(requests.isEmpty());
        html("/api/username/KayNeko", "<html>Challenge</html>");
        assertThrows(IllegalStateException.class, () -> api.lookupSkin("KayNeko"));
        assertThrows(IllegalArgumentException.class, () -> new WardrobeApiClient(HttpClient.newHttpClient(), new HytaleAuthService(null, null), base, URI.create("https://evil.test/")));
        assertThrows(IllegalArgumentException.class, () -> new WardrobeApiClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), new HytaleAuthService(null, null)));
    }
    private static String profile(String uuid, String name) {
        return "{\"uuid\":\"" + uuid + "\",\"username\":\"" + name + "\",\"skin\":{\"bodyCharacteristic\":\"Muscular.01\",\"cape\":null}}";
    }
    private void json(String path, String body) { replies.put(path, new Reply(200, "application/json", body)); }
    private void html(String path, String body) { replies.put(path, new Reply(200, "text/html", body)); }
    private record Reply(int status, String type, String body) {}
}
