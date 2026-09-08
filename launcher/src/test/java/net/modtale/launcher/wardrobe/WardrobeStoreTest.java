package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WardrobeStoreTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();

    private WardrobeItem item(String name, WardrobeItem.Kind kind, boolean favorite, String collection) {
        return new WardrobeItem(UUID.randomUUID(), kind, name, favorite, collection, "{\"skin\":{\"hair\":\"blue\"}}");
    }

    @Test void persistsBothKindsAndEditsAndSearchesWithoutExposingMutableLists() throws Exception {
        var store = new WardrobeStore(directory);
        var skin = item("Winter Explorer", WardrobeItem.Kind.SKIN, true, "Snow");
        var cape = item("Banner", WardrobeItem.Kind.CAPE, false, "Snow");
        store.saveItem(skin);
        store.saveItem(cape);
        store = new WardrobeStore(directory);
        assertEquals(List.of(skin, cape), store.items());
        assertEquals(List.of(skin), store.search("EXPLORER", null, false, null));
        assertEquals(List.of(skin, cape), store.search("snow", null, false, null));
        assertEquals(List.of(skin), store.search(null, null, true, "sNoW"));
        assertEquals(List.of(cape), store.search("", WardrobeItem.Kind.CAPE, false, null));
        assertTrue(store.search("", null, false, "").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> new WardrobeStore(directory).items().clear());
        var edited = new WardrobeItem(skin.id(), skin.kind(), "Renamed", false, "", skin.payload());
        store.saveItem(edited);
        assertEquals(List.of(edited, cape), new WardrobeStore(directory).items());
        store.removeItem(cape.id());
        assertEquals(List.of(edited), new WardrobeStore(directory).items());
    }

    @Test void enforcesItemAndByteLimitsIncludingUtf8AndDeepPayloads() throws Exception {
        var list = new ArrayList<WardrobeItem>();
        for (int i = 0; i < WardrobeStore.MAX_ITEMS; i++) list.add(item("Item", WardrobeItem.Kind.SKIN, false, ""));
        Files.writeString(directory.resolve("wardrobe.json"), json.writeValueAsString(java.util.Map.of("version", 1, "items", list)));
        var store = new WardrobeStore(directory);
        assertEquals(WardrobeStore.MAX_ITEMS, store.items().size());
        assertThrows(IllegalArgumentException.class, () -> store.saveItem(item("Overflow", WardrobeItem.Kind.CAPE, false, "")));
        list.add(item("Extra", WardrobeItem.Kind.SKIN, false, ""));
        Files.writeString(directory.resolve("wardrobe.json"), json.writeValueAsString(java.util.Map.of("version", 1, "items", list)));
        assertThrows(IOException.class, () -> new WardrobeStore(directory));
        for (String payload : List.of("[]", "null", "{} {}", "{\"x\":1,\"x\":2}",
                "{\"x\":\"" + "é".repeat(WardrobeStore.MAX_PAYLOAD_BYTES / 2) + "\"}",
                "{\"x\":".repeat(34) + "{}" + "}".repeat(34))) {
            assertThrows(IllegalArgumentException.class, () -> new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN,
                    "Skin", false, "", payload));
        }
    }

    @Test void invalidExistingFilesAreNotSilentlyReset() throws Exception {
        Path path = directory.resolve("wardrobe.json");
        for (String invalid : List.of("", "null", "{}", "{broken", "{\"version\":99,\"items\":[],\"watches\":[],\"alerts\":[]}")) {
            Files.writeString(path, invalid);
            assertThrows(IOException.class, () -> new WardrobeStore(directory));
            assertEquals(invalid, Files.readString(path));
        }
        Files.writeString(path, " ".repeat(WardrobeStore.MAX_DOCUMENT_BYTES + 1));
        assertThrows(IOException.class, () -> new WardrobeStore(directory));
    }

    @Test void failedDiskWriteDoesNotPublishMemoryStateAndCleansTemporaryFile() throws Exception {
        var store = new WardrobeStore(directory);
        var saved = item("Saved", WardrobeItem.Kind.SKIN, false, "");
        store.saveItem(saved);
        Path path = directory.resolve("wardrobe.json");
        Files.delete(path);
        Files.createDirectory(path);
        Files.writeString(path.resolve("blocker"), "keep");
        assertThrows(IOException.class, () -> store.saveItem(item("Unsaved", WardrobeItem.Kind.CAPE, false, "")));
        assertEquals(List.of(saved), store.items());
        try (var files = Files.list(directory)) { assertEquals(List.of(path), files.toList()); }
    }

    @Test void validatesRecordFieldsAndNormalizesLabels() {
        var item = item("  Trimmed  ", WardrobeItem.Kind.SKIN, false, "  Group  ");
        assertEquals("Trimmed", item.name());
        assertEquals("Group", item.collection());
        for (String name : List.of(" ", "x".repeat(121), "line\nfeed")) {
            assertThrows(IllegalArgumentException.class, () -> item(name, WardrobeItem.Kind.SKIN, false, ""));
        }
        assertThrows(IllegalArgumentException.class, () -> item("Name", WardrobeItem.Kind.CAPE, false, "x".repeat(121)));
    }

    @Test void aggregateDocumentLimitRejectsCandidateWithoutPublishingIt() throws Exception {
        var store = new WardrobeStore(directory);
        var saved = item("Saved", WardrobeItem.Kind.SKIN, false, "");
        store.saveItem(saved);
        String payload = "{\"data\":\"" + "a".repeat(WardrobeStore.MAX_PAYLOAD_BYTES - 20) + "\"}";
        var items = new ArrayList<WardrobeItem>();
        for (int i = 0; i < 128; i++) {
            items.add(new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.CAPE, "Cape", false, "", payload));
        }
        String oversized = json.writeValueAsString(java.util.Map.of("version", 1, "items", items));
        assertTrue(oversized.length() > WardrobeStore.MAX_DOCUMENT_BYTES);
        for (int i = 0; i < items.size() - 1; i++) store.saveItem(items.get(i));
        var before = store.items();
        String beforeDisk = Files.readString(directory.resolve("wardrobe.json"));
        assertThrows(IOException.class, () -> store.saveItem(items.getLast()));
        assertEquals(before, store.items());
        assertEquals(beforeDisk, Files.readString(directory.resolve("wardrobe.json")));
    }

    @Test void readsLegacyLocalItemsAndDropsRetiredMetadataOnNextSave() throws Exception {
        var store = new WardrobeStore(directory);
        var saved = item("Saved", WardrobeItem.Kind.SKIN, true, "Collection");
        store.saveItem(saved);
        Path path = directory.resolve("wardrobe.json");
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(Files.readString(path));
        legacy.putArray("watches").addObject().put("username", "Retired example");
        legacy.putArray("alerts").addObject().put("message", "Retired history");
        Files.writeString(path, legacy.toString());
        var reopened = new WardrobeStore(directory);
        assertEquals(List.of(saved), reopened.items());
        reopened.saveItem(saved);
        assertFalse(json.readTree(Files.readString(path)).has("watches"));
        assertFalse(json.readTree(Files.readString(path)).has("alerts"));
        assertEquals(List.of(saved), new WardrobeStore(directory).items());
        legacy.put("unexpected", true);
        Files.writeString(path, legacy.toString());
        assertThrows(IOException.class, () -> new WardrobeStore(directory));
    }
}
