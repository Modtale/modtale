package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalSkinLibraryTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();

    @Test void portableFilesOnlyContainCosmeticSelectionsAndRoundTripOffline() throws Exception {
        var skin = json.createObjectNode().put("bodyCharacteristic", "Default.01").put("haircut", "Short.Black").putNull("cape");
        var payload = json.createObjectNode().put("username", "PrivateName").put("playerUuid", UUID.randomUUID().toString())
                .put("thumbnailUrl", "https://example.invalid/tracker").put("skinId", "legacy-hash");
        payload.set("skin", skin);
        var item = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN, "Private name", true, "Private collection", payload.toString());
        Path file = directory.resolve("outfit.json");
        LocalSkinLibrary.exportFile(file, item);
        var exported = json.readTree(Files.readString(file));
        assertEquals(Set.of("version", "skin"), exported.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        var imported = LocalSkinLibrary.importFile(file);
        assertEquals(skin, json.readTree(imported.payload()).path("skin"));
        assertFalse(imported.payload().contains("Private"));
        assertFalse(imported.payload().contains("http"));
        assertFalse(imported.favorite()); assertTrue(imported.collection().isEmpty());
        LocalSkinLibrary.exportFile(file, imported);
        assertEquals(imported, LocalSkinLibrary.importFile(file));
    }

    @Test void rejectsMalformedOversizedAndIdentityDisguisedAsCosmetics() throws Exception {
        Path file = directory.resolve("outfit.json");
        for (String body : List.of("null", "{}", "[]", "{\"version\":2,\"skin\":{}}",
                "{\"version\":1,\"skin\":{\"bodyCharacteristic\":\"Default.01\",\"username\":\"Secret\"}}",
                "{\"version\":1,\"skin\":{\"bodyCharacteristic\":\"../../secret\"}}")) {
            Files.writeString(file, body);
            assertThrows(IOException.class, () -> LocalSkinLibrary.importFile(file), body);
        }
        Files.writeString(file, " ".repeat(WardrobeStore.MAX_PAYLOAD_BYTES + 1));
        assertThrows(IOException.class, () -> LocalSkinLibrary.importFile(file));
    }

    @Test void discoveryWorksOfflineAndFiltersSavedLooksWithoutAssets() throws Exception {
        var look = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN, "Green explorer", true, "Adventure", "{\"skin\":{\"bodyCharacteristic\":\"Default.01\"}}");
        assertEquals(List.of(look), LocalSkinLibrary.discover(directory.resolve("missing.zip"), List.of(look), "ADVENTURE"));
        assertTrue(LocalSkinLibrary.discover(directory.resolve("missing.zip"), List.of(look), "red").isEmpty());
    }

    @Test void installedDiscoveryIsStableAndExcludesEntitlementOnlySuggestions() throws Exception {
        Path assets = directory.resolve("Assets.zip");
        Map<String, String> entries = new LinkedHashMap<>();
        for (var category : CosmeticCatalogClient.categories()) entries.put(CosmeticCatalogClient.assetFile(category.key()), "[]");
        entries.put("Cosmetics/CharacterCreator/GradientSets.json", "[]");
        entries.put(CosmeticCatalogClient.assetFile("bodyCharacteristic"), "[{\"Id\":\"Default\",\"IsDefaultAsset\":true}]");
        entries.put(CosmeticCatalogClient.assetFile("haircut"), "[{\"Id\":\"Short\"},{\"Id\":\"Exclusive\",\"Entitlements\":[\"deluxe\"]}]");
        try (var zip = new ZipOutputStream(Files.newOutputStream(assets))) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
        var looks = LocalSkinLibrary.discover(assets, List.of(), "");
        assertEquals(2, looks.size());
        assertEquals(looks, LocalSkinLibrary.discover(assets, List.of(), ""));
        assertEquals(1, LocalSkinLibrary.discover(assets, looks, "short").size());
        assertTrue(looks.stream().noneMatch(look -> look.payload().contains("Exclusive")));
    }
}
