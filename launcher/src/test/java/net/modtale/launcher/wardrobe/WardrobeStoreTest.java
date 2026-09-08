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

    @Test void exchangeMergesIdempotently() throws Exception {
        var source = new WardrobeStore(directory.resolve("source"));
        var skin = item("Skin", WardrobeItem.Kind.SKIN, true, "Saved");
        var cape = item("Cape", WardrobeItem.Kind.CAPE, false, "");
        source.saveItem(skin);
        source.saveItem(cape);
        String exported = source.exportItems();
        var target = new WardrobeStore(directory.resolve("target"));
        var existing = item("Existing", WardrobeItem.Kind.SKIN, false, "");
        target.saveItem(existing);
        target.importItems(exported);
        target.importItems(exported);
        assertEquals(List.of(existing, skin, cape), new WardrobeStore(directory.resolve("target")).items());
    }

    @Test void malformedImportsNeverPartiallyReplaceExistingItemsOrDisk() throws Exception {
        var store = new WardrobeStore(directory);
        var saved = item("Saved", WardrobeItem.Kind.SKIN, false, "");
        store.saveItem(saved);
        String disk = Files.readString(directory.resolve("wardrobe.json"));
        String valid = store.exportItems();
        var invalid = new ArrayList<>(List.of("", "null", "[]", "{}", "{\"version\":2,\"items\":[]}",
                "{\"version\":1,\"items\":null}", "{\"version\":1,\"items\":[null]}",
                "{\"version\":1,\"version\":1,\"items\":[]}", valid + " {}",
                valid.replace("\"version\":1", "\"version\":1.5"),
                valid.replace("\"favorite\":false", "\"favorite\":\"false\""),
                valid.replace("\"favorite\":false,", ""),
                valid.replace("\"kind\":\"SKIN\"", "\"kind\":0"),
                valid.replace(saved.id().toString(), "not-a-uuid"),
                valid.replace("Saved", " "), valid.replace("\"version\":1", "\"unknown\":1,\"version\":1")));
        var duplicate = json.readTree(valid);
        ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.get("items")).add(duplicate.get("items").get(0).deepCopy());
        invalid.add(duplicate.toString());
        for (String input : invalid) {
            assertThrows(IOException.class, () -> store.importItems(input), input);
            assertEquals(List.of(saved), store.items());
            assertEquals(disk, Files.readString(directory.resolve("wardrobe.json")));
        }
    }

    @Test void rejectsConflictsAfterValidNewItemsWithoutPartialMerge() throws Exception {
        var store = new WardrobeStore(directory);
        var saved = item("Saved", WardrobeItem.Kind.SKIN, false, "");
        store.saveItem(saved);
        var other = new WardrobeStore(directory.resolve("other"));
        other.saveItem(item("New", WardrobeItem.Kind.CAPE, false, ""));
        other.saveItem(new WardrobeItem(saved.id(), saved.kind(), "Conflict", true, "", "{}"));
        assertThrows(IOException.class, () -> store.importItems(other.exportItems()));
        assertEquals(List.of(saved), new WardrobeStore(directory).items());
    }

    @Test void enforcesItemAndByteLimitsIncludingUtf8AndDeepPayloads() throws Exception {
        var store = new WardrobeStore(directory);
        var list = new ArrayList<WardrobeItem>();
        for (int i = 0; i < WardrobeStore.MAX_ITEMS; i++) list.add(item("Item", WardrobeItem.Kind.SKIN, false, ""));
        store.importItems(json.writeValueAsString(java.util.Map.of("version", 1, "items", list)));
        assertEquals(WardrobeStore.MAX_ITEMS, store.items().size());
        assertThrows(IllegalArgumentException.class, () -> store.saveItem(item("Overflow", WardrobeItem.Kind.CAPE, false, "")));
        list.add(item("Extra", WardrobeItem.Kind.SKIN, false, ""));
        assertThrows(IOException.class, () -> store.importItems(json.writeValueAsString(java.util.Map.of("version", 1, "items", list))));
        assertThrows(IOException.class, () -> store.importItems(" ".repeat(WardrobeStore.MAX_DOCUMENT_BYTES + 1)));
        assertThrows(IOException.class, () -> store.importItems("é".repeat(WardrobeStore.MAX_DOCUMENT_BYTES / 2 + 1)));
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

    @Test void pathExchangeIsAtomicAndRejectsOversizedOrMalformedUtf8Input() throws Exception {
        var store = new WardrobeStore(directory);
        var saved = item("Saved", WardrobeItem.Kind.SKIN, true, "");
        store.saveItem(saved);
        Path export = directory.resolve("exports/items.json");
        store.exportItems(export);
        var target = new WardrobeStore(directory.resolve("target"));
        target.importItems(export);
        assertEquals(List.of(saved), target.items());
        store.removeItem(saved.id());
        store.exportItems(export);
        assertEquals("{\"version\":1,\"items\":[]}", Files.readString(export));
        try (var paths = Files.list(export.getParent())) { assertEquals(List.of(export), paths.toList()); }
        String disk = Files.readString(directory.resolve("wardrobe.json"));
        assertThrows(IllegalArgumentException.class, () -> store.exportItems(directory.resolve("wardrobe.json")));
        assertEquals(disk, Files.readString(directory.resolve("wardrobe.json")));
        Files.write(export, new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});
        assertThrows(IOException.class, () -> target.importItems(export));
        Files.writeString(export, " ".repeat(WardrobeStore.MAX_DOCUMENT_BYTES + 1));
        assertThrows(IOException.class, () -> target.importItems(export));
        assertEquals(List.of(saved), target.items());
    }

    @Test void aggregateDocumentLimitRejectsCandidateWithoutPublishingIt() throws Exception {
        var store = new WardrobeStore(directory);
        var saved = item("Saved", WardrobeItem.Kind.SKIN, false, "");
        store.saveItem(saved);
        String original = Files.readString(directory.resolve("wardrobe.json"));
        String payload = "{\"data\":\"" + "a".repeat(WardrobeStore.MAX_PAYLOAD_BYTES - 20) + "\"}";
        var items = new ArrayList<WardrobeItem>();
        for (int i = 0; i < 129; i++) {
            items.add(new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.CAPE, "Cape", false, "", payload));
        }
        String oversized = json.writeValueAsString(java.util.Map.of("version", 1, "items", items));
        assertTrue(oversized.length() > WardrobeStore.MAX_DOCUMENT_BYTES);
        assertThrows(IOException.class, () -> store.importItems(oversized));
        assertEquals(List.of(saved), store.items());
        assertEquals(original, Files.readString(directory.resolve("wardrobe.json")));
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
        assertThrows(IOException.class, () -> reopened.importItems(legacy.toString()), "Imports remain strictly item-only");
        reopened.saveItem(saved);
        assertEquals(json.readTree(reopened.exportItems()), json.readTree(Files.readString(path)));
        legacy.put("unexpected", true);
        Files.writeString(path, legacy.toString());
        assertThrows(IOException.class, () -> new WardrobeStore(directory));
    }
}
