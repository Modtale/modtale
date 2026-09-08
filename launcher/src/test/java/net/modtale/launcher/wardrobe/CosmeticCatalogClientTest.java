package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticCatalogClientTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROOT = "Cosmetics/CharacterCreator/";

    @Test void enumeratesOnlyActualVariantColorCombinationsAndPreservesLabelsLocksAndSwatches() throws Exception {
        var client = new CosmeticCatalogClient(archive(definitions()));
        assertEquals(20, client.categories().size());
        assertEquals("Body", client.categories().getFirst().label());
        assertEquals("HairLength.Short", client.tags().getFirst().id());
        assertEquals(1, client.tags().getFirst().displayOrder());
        assertEquals(List.of("Cape.Green.Neck", "Cape.Blue.NoNeck", "Cape.White.NoNeck"),
                client.options("cape", "Cape").stream().map(CosmeticOption::id).toList());
        var option = client.options("cape", "Cape").getFirst();
        assertEquals("Emerald Cape", option.label()); assertEquals(List.of("#00ff00"), option.swatches());
        assertEquals(List.of("game.deluxe"), option.entitlements());
        assertEquals("Green", option.colorId()); assertEquals("Neck", option.variantId());
        assertThrows(IllegalArgumentException.class, () -> client.resolve("cape", "Cape.Blue.Neck"));
        assertEquals(List.of("Short.Black", "Short.Blond", "Long.Black", "Long.Blond"),
                client.browse("haircut", "", 1, 100).options().stream().map(CosmeticOption::id).toList());
        assertEquals(List.of("Neutral"), client.options("face", "Neutral").stream().map(CosmeticOption::id).toList());
    }

    @Test void paginatesUniqueAssetsAndSearchesLabelsIdsAndColors() throws Exception {
        var client = new CosmeticCatalogClient(archive(definitions()));
        var page = client.browseAssets("haircut", "", 1, 1);
        assertEquals(2, page.total()); assertTrue(page.hasNext()); assertTrue(page.complete());
        assertEquals("Short", page.options().getFirst().assetId());
        assertEquals("Long", client.browseAssets("haircut", "", 2, 1).options().getFirst().assetId());
        assertFalse(client.browseAssets("haircut", "", 2, 1).hasNext());
        assertEquals(2, client.browse("haircut", "BLACK", 1, 10).options().size());
        assertEquals(1, client.browseAssets("cape", "emerald", 1, 10).total());
        assertTrue(client.browse("haircut", "", Integer.MAX_VALUE, 100).options().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> client.browse("haircut", "", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> client.browseAssets("haircut", "", 1, 101));
        assertThrows(IllegalArgumentException.class, () -> client.browse("../unknown", "", 1, 10));
        assertThrows(UnsupportedOperationException.class, () -> page.options().clear());
    }

    @Test void resolvesVariantModelTextureAndInheritedBodyColorWithDefensiveCopies() throws Exception {
        var client = new CosmeticCatalogClient(archive(definitions()));
        ObjectNode skin = client.defaultSkin();
        assertEquals("Body.01", skin.path("bodyCharacteristic").asText()); assertEquals("Neutral", skin.path("face").asText());
        skin.put("haircut", "Long.Blond"); skin.put("cape", "Cape.Green.Neck");
        var parts = client.resolveComposition(skin);
        var face = parts.stream().filter(part -> part.category().equals("face")).findFirst().orElseThrow();
        assertEquals("Tint/Skin01.png", face.definition().path("GradientTexture").asText());
        assertEquals("Face.png", face.definition().path("Texture").asText());
        var hair = parts.stream().filter(part -> part.category().equals("haircut")).findFirst().orElseThrow();
        assertEquals("HairLong.blockymodel", hair.definition().path("Model").asText());
        assertEquals("Tint/Blond.png", hair.definition().path("GradientTexture").asText());
        var cape = client.resolve("cape", "Cape.Green.Neck");
        assertEquals("CapeNeck.blockymodel", cape.path("Model").asText()); assertEquals("Green.png", cape.path("Texture").asText());
        assertEquals("Haircut", cape.path("DisableCharacterPartCategory").asText());
        assertTrue(cape.path("GradientTexture").isMissingNode());
        ((ObjectNode) hair.definition()).put("Model", "corrupt");
        assertEquals("HairLong.blockymodel", hair.definition().path("Model").asText());
        ((ObjectNode) client.definition("haircut", "Long")).put("Model", "corrupt");
        assertEquals("HairLong.blockymodel", client.resolve("haircut", "Long.Blond").path("Model").asText());
        skin.put("bodyCharacteristic", "Body.02"); assertEquals("Body.01", client.defaultSkin().path("bodyCharacteristic").asText());
    }

    @Test void requiresExplicitBodyAndKeepsAnimationsSeparateFromCosmeticSlots() throws Exception {
        var client = new CosmeticCatalogClient(archive(definitions()));
        assertThrows(IllegalArgumentException.class, () -> client.resolveComposition(JSON.createObjectNode()));
        ObjectNode skin = client.defaultSkin(); skin.put("bodyCharacteristic", "Body.NoSuchColor");
        assertThrows(IllegalArgumentException.class, () -> client.resolveComposition(skin));
        skin.put("bodyCharacteristic", "Body.01"); skin.put("emote", "Wave");
        assertThrows(IllegalArgumentException.class, () -> client.resolveComposition(skin));
        skin.remove("emote"); skin.put("cape", 1);
        assertThrows(IllegalArgumentException.class, () -> client.resolveComposition(skin));
        assertThrows(IllegalArgumentException.class, () -> client.resolve("cape", "Cape.Neck.Green"));
        var animation = client.animations("EmotesInGame").getFirst();
        assertEquals("Wave", animation.id()); assertEquals("Wave.blockyanim", animation.animation());
        assertTrue(animation.looping()); assertTrue(animation.hideItemInHand());
    }

    @Test void malformedMissingDuplicateAndOversizedCatalogsFailClosed() throws Exception {
        var missing = definitions(); missing.remove(ROOT + "Faces.json");
        assertThrows(IOException.class, () -> new CosmeticCatalogClient(archive(missing)));
        for (String invalid : List.of("null", "{}", "[{\"Id\":\"X\",\"Id\":\"Y\"}]", "[{\"Id\":\"X\"},{\"Id\":\"X\"}]",
                "[{\"Id\":\"X\",\"GradientSet\":\"Unknown\"}]", "[{\"Id\":\"../bad\"}]", "[{\"Id\":\"X\",\"Variants\":[]}]", "[{\"Id\":\"X\",\"Textures\":{}}]")) {
            var data = definitions(); data.put(ROOT + "Haircuts.json", invalid);
            assertThrows(IOException.class, () -> new CosmeticCatalogClient(archive(data)), invalid);
        }
        var large = definitions(); large.put(ROOT + "Haircuts.json", " ".repeat(4 * 1024 * 1024 + 1));
        assertThrows(IOException.class, () -> new CosmeticCatalogClient(archive(large)));
    }

    @Test void remoteUsesPublishedSearchAndNeverClaimsCompletenessOrInventsColors() throws Exception {
        AtomicInteger count = new AtomicInteger(); AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> response = new AtomicReference<>("[\"Short.Black\",\"Short.Black\",\"Short.Blond\",\"Long.Black\"]");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            count.incrementAndGet(); request.set(exchange.getRequestURI().toString());
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = new CosmeticCatalogClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            var page = client.browse("haircut", "Short", 1, 1);
            assertEquals("/api/cosmetic-values/haircut?search=Short", request.get());
            assertEquals("Short.Black", page.options().getFirst().id()); assertFalse(page.complete()); assertEquals(-1, page.total()); assertTrue(page.hasNext());
            assertTrue(page.options().getFirst().entitlements().isEmpty());
            assertEquals("Short.Blond", client.browse("haircut", "Short", 2, 1).options().getFirst().id());
            assertEquals(1, count.get()); assertThrows(IllegalStateException.class, client::defaultSkin);
            assertThrows(IllegalStateException.class, () -> client.resolve("haircut", "Short.Black"));
            assertThrows(IllegalArgumentException.class, () -> client.browse("haircut", "", 0, 10)); assertEquals(1, count.get());
            response.set("[\"../bad\"]"); assertThrows(IOException.class, () -> client.browse("haircut", "Other", 1, 10));
            response.set("{}"); assertThrows(IOException.class, () -> client.browse("haircut", "Other", 1, 10));
        } finally { server.stop(0); }
    }

    @Test void refusesRedirectFollowingAndForeignOrigins() {
        assertThrows(IllegalArgumentException.class, () -> new CosmeticCatalogClient(HttpClient.newHttpClient(), URI.create("https://example.com/")));
        assertThrows(IllegalArgumentException.class, () -> new CosmeticCatalogClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), URI.create("https://hytags.com/")));
    }

    @Test void preservesPublishedMulticolorPaletteTokensWithPlusSigns() throws Exception {
        var data = definitions();
        data.put(ROOT + "EarAccessory.json", """
                [{"Id":"Earring","Model":"Earring.blockymodel","Textures":{
                  "Black+Grey":{"Texture":"BlackGrey.png","BaseColor":["#111111","#999999"]},
                  "PinkPastel+PurplePastel+TurquoisePastel":{"Texture":"Pastels.png"}}}]
                """);
        var client = new CosmeticCatalogClient(archive(data));
        var options = client.options("earAccessory", "Earring");
        assertEquals("Earring.Black+Grey", options.getFirst().id());
        assertEquals(2, options.getFirst().swatches().size());
        assertEquals("Pastels.png", client.resolve("earAccessory", "Earring.PinkPastel+PurplePastel+TurquoisePastel").path("Texture").asText());
    }

    @Test @EnabledIfEnvironmentVariable(named = "MODTALE_COSMETIC_ASSETS", matches = ".+")
    void validatesInstalledCatalogAndEveryResolvedOption() throws Exception {
        var client = new CosmeticCatalogClient(Path.of(System.getenv("MODTALE_COSMETIC_ASSETS")));
        int assets = 0, selections = 0;
        for (var category : client.categories()) {
            var first = client.browseAssets(category.key(), "", 1, 100); assets += first.total();
            for (int page = 1; page <= Math.max(1, (first.total() + 99) / 100); page++) {
                for (var asset : client.browseAssets(category.key(), "", page, 100).options()) {
                    for (var option : client.options(category.key(), asset.assetId())) {
                        var resolved = client.resolve(category.key(), option.id());
                        assertFalse(resolved.path("Model").asText().isBlank(), option.id());
                        assertFalse(resolved.path("Texture").asText().isBlank(), option.id()); selections++;
                    }
                }
            }
        }
        assertFalse(client.resolveComposition(client.defaultSkin()).isEmpty()); assertTrue(assets > 100); assertTrue(selections > 1000);
        System.out.println("Installed cosmetic catalog: " + assets + " assets, " + selections + " selections; explicit default=" + client.defaultSkin());
    }

    private Path archive(Map<String, String> contents) throws IOException {
        Path path = directory.resolve(java.util.UUID.randomUUID() + ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : contents.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
        return path;
    }

    private static Map<String, String> definitions() {
        var result = new LinkedHashMap<String, String>();
        for (var category : new CosmeticCatalogClient().categories()) result.put(CosmeticCatalogClient.assetFile(category.key()), "[]");
        result.put(ROOT + "GradientSets.json", """
                [{"Id":"Skin","Gradients":{"01":{"Texture":"Tint/Skin01.png","BaseColor":["#aa8866"]},"02":{"Texture":"Tint/Skin02.png","BaseColor":["#ddbb88"]}}},
                 {"Id":"Hair","Gradients":{"Black":{"Texture":"Tint/Black.png","BaseColor":["#111111"]},"Blond":{"Texture":"Tint/Blond.png","BaseColor":["#ffdd99"]}}}]
                """);
        result.put(ROOT + "BodyCharacteristics.json", "[{\"Id\":\"Body\",\"Model\":\"Body.blockymodel\",\"GreyscaleTexture\":\"Body.png\",\"GradientSet\":\"Skin\",\"IsDefaultAsset\":true}]");
        result.put(ROOT + "Faces.json", "[{\"Id\":\"Neutral\",\"Model\":\"Face.blockymodel\",\"GreyscaleTexture\":\"Face.png\",\"GradientSet\":\"Skin\",\"IsDefaultAsset\":true}]");
        result.put(ROOT + "Haircuts.json", """
                [{"Id":"Short","Model":"HairShort.blockymodel","GreyscaleTexture":"HairShort.png","GradientSet":"Hair"},
                 {"Id":"Long","Model":"HairLong.blockymodel","GreyscaleTexture":"HairLong.png","GradientSet":"Hair"}]
                """);
        result.put(ROOT + "Capes.json", """
                [{"Id":"Cape","Name":"avatarCustomization.capes.Cape.name","Entitlements":["game.deluxe"],"DisableCharacterPartCategory":"Haircut",
                  "Variants":{"Neck":{"Model":"CapeNeck.blockymodel","Textures":{"Green":{"Texture":"Green.png","BaseColor":["#00ff00"]}}},
                              "NoNeck":{"Model":"CapeNoNeck.blockymodel","Textures":{"Blue":{"Texture":"Blue.png"},"White":{"Texture":"White.png"}}}}}]
                """);
        result.put("Common/Languages/en-US/avatarCustomization/capes.lang", "Cape.name = Emerald Cape\n");
        result.put(ROOT + "EmotesInGame.json", "[{\"Id\":\"Wave\",\"Animation\":\"Wave.blockyanim\",\"IsLooping\":true,\"HideItemInHand\":true}]");
        result.put(ROOT + "Tags.json", "[{\"Id\":\"HairLength.Short\",\"NameKey\":\"avatarCustomization.tags.haircut.short\",\"DisplayOrder\":1}]");
        return result;
    }
}
