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

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
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
        };
        api = new WardrobeApiClient(HttpClient.newHttpClient(), auth, base, base);
    }
    @AfterEach void stop() { server.stop(0); }

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
    @Test void capesFollowPublishedNextLinkAndUseCosmeticIdentifierRatherThanRecordHash() throws Exception {
        html("/capes", "<h2>Cape archive</h2><a href='/cape/" + HASH + "'>Forest Guardian</a><a href='/capes?page=2'>Next ›</a>");
        html("/capes?page=2", "<h2>Cape archive</h2><a href='/cape/" + HASH + "'>Forest Guardian</a>");
        html("/cape/" + HASH, "<img src='https://hyvatar.io/render/cape/NPC?cape=Cape_Forest_Guardian.Green.Neck_Piece&amp;size=640'>");
        List<WardrobeItem> items = api.capes();
        assertEquals(1, items.size());
        assertEquals("Cape_Forest_Guardian.Green.Neck_Piece", new ObjectMapper().readTree(items.getFirst().payload()).path("cape").asText());
        assertEquals(3, requests.size());
        assertEquals(items, api.capes());
        assertEquals(3, requests.size(), "Cape catalog should be cached");
    }
    @Test void capeCatalogUsesInlineIdentifiersWithoutFetchingEveryDetailPage() throws Exception {
        html("/capes", "<h2>Cape archive</h2><a href='/cape/" + HASH + "'><img src='https://hyvatar.io/render/cape/NPC?cape=Cape_Forest.Green.Neck&amp;size=256' alt='Cape Forest Green Neck Hytale cape'></a>");
        var items = api.capes();
        assertEquals(List.of("/capes"), requests);
        assertEquals("Forest Green Neck", items.getFirst().name());
        assertEquals("Cape_Forest.Green.Neck", new ObjectMapper().readTree(items.getFirst().payload()).path("cape").asText());
    }
    @Test void untrustedCapePreviewAndChangedHtmlAreErrors() {
        html("/capes", "<h2>Cape archive</h2><a href='/cape/" + HASH + "'>Cape</a>");
        html("/cape/" + HASH, "<img src='https://hyvatar.io.evil.test/render/cape/NPC?cape=bad'>");
        assertThrows(IllegalStateException.class, () -> api.capes());
        html("/skins?page=1", "<h1>Sign in</h1>");
        assertThrows(IllegalStateException.class, () -> api.browseSkins(1, "default"));
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
        assertEquals("/player-skins/slot-1", requests.getLast());
        assertEquals("Bearer official-oauth", headers.getLast());
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
        json("/player-skins?profileId=" + ID, "{\"activeSkin\":\"missing\",\"skins\":[]}");
        assertThrows(IllegalStateException.class, () -> api.apply(cape, settings()));
        assertFalse(methods.contains("PUT"));
    }
    @Test void rejectedWriteIsNotReportedAsSuccess() {
        slots();
        replies.put("/player-skins/slot-1", new Reply(403, "application/json", "{}"));
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
            var root = mapper.createObjectNode().put("activeSkin", "slot-1");
            root.putArray("skins").addObject().put("id", "slot-1").put("name", "My original slot")
                    .put("skinData", "{\"bodyCharacteristic\":\"Original\",\"cape\":null}");
            json("/player-skins?profileId=" + ID, root.toString());
            json("/player-skins/slot-1", "{}");
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

    @Test void officialOutfitCrudUsesVerifiedMethodsBodiesAndProfileSession() throws Exception {
        officialSlots(5);
        LauncherSettings settings = settings();
        UUID profile = UUID.fromString(ID);
        var definition = new ObjectMapper().readTree("{\"bodyCharacteristic\":\"Default.01\",\"haircut\":\"Fringe.Black\",\"cape\":null}");
        api.createSkin(settings, "New outfit", definition, profile);
        assertEquals(List.of("GET", "POST"), methods);
        assertEquals("/player-skins", requests.getLast());
        var body = new ObjectMapper().readTree(bodies.getLast());
        assertEquals(2, body.size());
        assertEquals("New outfit", body.path("name").asText());
        assertTrue(body.path("skinData").isTextual());
        assertEquals(definition, new ObjectMapper().readTree(body.path("skinData").asText()));
        api.updateSkin(settings, SLOT, "Renamed", definition, profile);
        assertEquals("PUT", methods.getLast());
        assertEquals("/player-skins/" + SLOT, requests.getLast());
        assertEquals("Renamed", new ObjectMapper().readTree(bodies.getLast()).path("name").asText());
        api.activateSkin(settings, SLOT, profile);
        assertEquals("PUT", methods.getLast());
        assertEquals("/player-skins/active", requests.getLast());
        assertEquals("{\"skinId\":\"" + SLOT + "\"}", bodies.getLast());
        deletableSlots();
        api.deleteSkin(settings, SLOT, profile);
        assertEquals("DELETE", methods.getLast());
        assertEquals("/player-skins/" + SLOT, requests.getLast());
        assertEquals("", bodies.getLast());
        assertTrue(headers.stream().allMatch("Bearer official-session"::equals));
    }

    private void deletableSlots() {
        var root = new ObjectMapper().createObjectNode().put("activeSkin", OTHER_SLOT).put("maxSkins", 5);
        var skins = root.putArray("skins");
        for (String id : List.of(SLOT, OTHER_SLOT)) skins.addObject().put("id", id).put("name", "Outfit")
                .put("skinData", "{\"bodyCharacteristic\":\"Default.01\"}");
        json("/player-skins", root.toString());
    }

    @Test void deleteRefreshesSlotsAndRejectsNewlyActiveOrLastOutfit() {
        for (boolean nowActive : List.of(true, false)) {
            deletableSlots();
            var settings = settings();
            var displayed = api.slots(settings);
            assertEquals(2, displayed.slots().size());
            assertNotEquals(SLOT, displayed.activeId());
            var changed = new ObjectMapper().createObjectNode().put("maxSkins", 5);
            if (nowActive) changed.put("activeSkin", SLOT);
            else changed.putNull("activeSkin");
            var skins = changed.putArray("skins");
            skins.addObject().put("id", SLOT).put("name", "Adventure")
                    .put("skinData", "{\"bodyCharacteristic\":\"Default.01\"}");
            if (nowActive) skins.addObject().put("id", OTHER_SLOT).put("name", "Other")
                    .put("skinData", "{\"bodyCharacteristic\":\"Default.01\"}");
            json("/player-skins", changed.toString());
            requests.clear(); methods.clear();
            var error = assertThrows(IllegalStateException.class,
                    () -> api.deleteSkin(settings, SLOT, UUID.fromString(ID)));
            assertTrue(error.getMessage().contains(nowActive ? "active" : "last"));
            assertEquals(List.of("/player-skins"), requests, "Deletion must fetch the changed upstream slots");
            assertEquals(List.of("GET"), methods, "Unsafe deletion must never reach DELETE");
        }
    }

    @Test void officialOutfitWritesRejectFullSlotsMissingOwnershipAndInvalidDefinitions() {
        officialSlots(1);
        var definition = new ObjectMapper().createObjectNode().put("bodyCharacteristic", "Default.01");
        var settings = settings();
        var profile = UUID.fromString(ID);
        assertThrows(IllegalStateException.class, () -> api.createSkin(settings, "New", definition, profile));
        assertThrows(IllegalStateException.class, () -> api.deleteSkin(settings, OTHER_SLOT, profile));
        assertThrows(IllegalStateException.class, () -> api.activateSkin(settings, OTHER_SLOT, profile));
        assertThrows(IllegalStateException.class, () -> api.updateSkin(settings, OTHER_SLOT, "New", definition, profile));
        assertTrue(methods.stream().allMatch("GET"::equals));
        requests.clear();
        assertThrows(IllegalArgumentException.class, () -> api.deleteSkin(settings, "../active", profile));
        assertThrows(IllegalArgumentException.class, () -> api.createSkin(settings, " ", definition, profile));
        assertThrows(IllegalStateException.class, () -> api.createSkin(settings, "New", new ObjectMapper().createObjectNode(), profile));
        assertTrue(requests.isEmpty());
    }

    @Test void allOutfitWritesAbortWhenAccountChangesDuringSlotRead() {
        officialSlots(5);
        var definition = new ObjectMapper().createObjectNode().put("bodyCharacteristic", "Default.01");
        for (int operation = 0; operation < 4; operation++) {
            var settings = settings();
            var profile = UUID.fromString(ID);
            afterSlots = () -> settings.getHytaleAuthSession().setUuid(OTHER_SLOT);
            final int action = operation;
            assertThrows(IllegalStateException.class, () -> {
                switch (action) {
                    case 0 -> api.createSkin(settings, "New", definition, profile);
                    case 1 -> api.updateSkin(settings, SLOT, "New", definition, profile);
                    case 2 -> api.deleteSkin(settings, SLOT, profile);
                    case 3 -> api.activateSkin(settings, SLOT, profile);
                }
            });
        }
        assertEquals(4, requests.size());
        assertTrue(methods.stream().allMatch("GET"::equals));
    }

    @Test void outfitWritesAbortBeforeReadWhenAccountChangesDuringTokenRefresh() {
        var settings = settings();
        duringSessionRefresh = () -> settings.getHytaleAuthSession().setUuid(OTHER_SLOT);
        assertThrows(IllegalStateException.class, () -> api.deleteSkin(settings, SLOT, UUID.fromString(ID)));
        assertTrue(requests.isEmpty());
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
        var cosmetics = api.unlockedCosmetics(settings());
        assertEquals(Set.of("Cape_Royal_Emissary"), cosmetics.get("cape"));
        assertThrows(UnsupportedOperationException.class, () -> cosmetics.get("cape").clear());
        json("/my-account/game-profile", "{\"uuid\":\"" + ID + "\",\"entitlements\":[\"game.founder\"]}");
        assertEquals(Set.of("game.founder"), api.entitlements(settings()));
        assertTrue(headers.stream().allMatch("Bearer official-session"::equals));
        json("/my-account/game-profile", "{\"uuid\":\"" + OTHER_SLOT + "\",\"entitlements\":[]}");
        assertThrows(IllegalStateException.class, () -> api.entitlements(settings()));
        for (String response : List.of("null", "[]", "{\"cape\":null}", "{\"cape\":[42]}")) {
            json("/my-account/cosmetics", response);
            assertThrows(IllegalStateException.class, () -> api.unlockedCosmetics(settings()));
        }
    }

    @Test void rejectedOfficialWriteDoesNotRetryOrExposeResponseBody() {
        deletableSlots();
        replies.put("/player-skins/" + SLOT, new Reply(403, "application/json", "sensitive upstream details"));
        var failure = assertThrows(IllegalStateException.class,
                () -> api.deleteSkin(settings(), SLOT, UUID.fromString(ID)));
        assertTrue(failure.getMessage().contains("403"));
        assertFalse(failure.getMessage().contains("sensitive"));
        assertEquals(List.of("GET", "DELETE"), methods);
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
