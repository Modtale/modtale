package net.modtale.launcher.config;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldSettingsTest {
    @TempDir Path directory;
    private final HytaleConfigFiles files = new HytaleConfigFiles();
    private final ObjectMapper json = new ObjectMapper();

    private ConfigSettingsDocument document(String text) throws Exception {
        Path path = directory.resolve("universe/worlds/default/config.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        return new ConfigSettingsDocument(files.read(new HytaleConfigFiles.ConfigFile(directory, path, "World")), true);
    }
    private ConfigSettingsDocument.Setting setting(ConfigSettingsDocument doc, String name) {
        return doc.settings().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test void addsOverridesWithoutTouchingInheritedOrUnknownSettingsAndCanReset() throws Exception {
        String original = "{\"UUID\":\"keep\",\"Seed\":9007199254740993,\"GameplayConfig\":\"Custom\",\"Mods\":{\"Author:Mod\":{\"Enabled\":true}},\"Custom\":[1,null]}";
        var doc = document(original);
        assertFalse(doc.dirty());
        assertEquals("", setting(doc, "Day duration (seconds)").value());
        setting(doc, "PvP").set("true");
        setting(doc, "Inventory penalty on death").set("Configured");
        setting(doc, "Resource loss on death (%)").set("25.5");
        var saved = files.save(doc.snapshot(), doc.serialize());
        var root = json.readTree(saved.text());
        assertTrue(root.path("IsPvpEnabled").asBoolean());
        assertEquals(25.5, root.path("Death").path("ItemsAmountLossPercentage").asDouble());
        assertFalse(root.has("DaytimeDurationSeconds"));
        assertFalse(root.path("Death").has("ItemsDurabilityLossPercentage"));
        for (String field : new String[]{"UUID", "Seed", "GameplayConfig", "Mods", "Custom"})
            assertEquals(json.readTree(original).get(field), root.get(field));
        try (var backups = Files.list(saved.file().path().getParent())) {
            assertTrue(backups.anyMatch(p -> p.toString().endsWith(".bak")));
        }
        doc.reset();
        assertFalse(doc.dirty());
        assertEquals(original, doc.serialize());
    }

    @Test void revertingVirtualDefaultsDoesNotLeaveOverridesOrEmptySections() throws Exception {
        for (String text : new String[]{"{}", "{\"Death\":null}"}) {
            var doc = document(text);
            setting(doc, "PvP").set("true");
            setting(doc, "PvP").set("false");
            setting(doc, "Inventory penalty on death").set("All");
            setting(doc, "Inventory penalty on death").set("");
            assertFalse(doc.dirty());
            assertEquals(text, doc.serialize());
        }
    }

    @Test void validatesRangesTypesAndKeepsStorageReadOnly() throws Exception {
        var doc = document("{}");
        for (String value : new String[]{"-1", "101", "NaN", "Infinity", "oops"})
            assertThrows(IllegalArgumentException.class, () -> setting(doc, "Resource loss on death (%)").set(value));
        for (String value : new String[]{"0", "1.5", "2147483648"})
            assertThrows(IllegalArgumentException.class, () -> setting(doc, "Day duration (seconds)").set(value));
        assertThrows(IllegalArgumentException.class, () -> setting(doc, "Inventory penalty on death").set("invalid"));
        assertThrows(IllegalArgumentException.class, () -> setting(doc, "Storage type").set("Empty"));
        assertThrows(java.io.IOException.class, () -> document("{\"Death\":[]}"));
        assertThrows(java.io.IOException.class, () -> document("{\"IsPvpEnabled\":\"yes\"}"));
        assertFalse(doc.dirty());
    }

    @Test void discoveryExcludesServerModsOtherSavesAndLinkedWorlds() throws Exception {
        document("{}");
        Files.writeString(directory.resolve("config.json"), "{\"Mods\":{}}");
        Path mod = directory.resolve("mods/Example/config.json");
        Files.createDirectories(mod.getParent()); Files.writeString(mod, "{}");
        Path other = directory.resolve("other/config.json");
        Files.createDirectories(other.getParent()); Files.writeString(other, "{}");
        Files.createSymbolicLink(directory.resolve("universe/worlds/linked"), other.getParent());
        var found = files.discoverWorldSettings(directory);
        assertEquals(1, found.size());
        assertEquals(directory.resolve("universe/worlds/default/config.json"), found.getFirst().path());
    }

    @Test void rejectsSavingOverExternalChanges() throws Exception {
        var doc = document("{}");
        setting(doc, "Fall damage").set("false");
        Files.writeString(doc.snapshot().file().path(), "{\"IsPvpEnabled\":true}");
        assertThrows(java.io.IOException.class, () -> files.save(doc.snapshot(), doc.serialize()));
        assertEquals("{\"IsPvpEnabled\":true}", Files.readString(doc.snapshot().file().path()));
    }
}
