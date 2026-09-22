package net.modtale.launcher.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigSettingsDocumentTest {
    private ConfigSettingsDocument doc(String extension, String text) throws Exception {
        Path root = Path.of("/tmp/settings-test");
        return new ConfigSettingsDocument(new HytaleConfigFiles.Snapshot(new HytaleConfigFiles.ConfigFile(root, root.resolve("config." + extension), ""), text));
    }
    @Test void preservesTypesPrecisionUnknownValuesAndNestedStructure() throws Exception {
        String text = "{\"XP\":9007199254740993,\"Stats\":{\"Power\":2.200000047683716},\"Names\":[\"one\",\"two\"],\"Unknown\":null,\"Enabled\":true}";
        var doc = doc("json", text);
        assertEquals(text, doc.serialize());
        doc.settings().stream().filter(s -> s.name().equals("Enabled")).findFirst().orElseThrow().set("false");
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(doc.serialize());
        assertEquals("9007199254740993", tree.path("XP").asText());
        assertTrue(tree.path("Unknown").isNull());
        assertEquals("two", tree.path("Names").get(1).asText());
        assertFalse(tree.path("Enabled").asBoolean());
        doc.reset(); assertFalse(doc.dirty()); assertEquals(text, doc.serialize());
    }
    @Test void rejectsInvalidNumbersAndHandlesEscapedKeys() throws Exception {
        var doc = doc("json", "{\"a/b~c\":2,\"Value\":0.5}");
        var integer = doc.settings().getFirst();
        assertThrows(IllegalArgumentException.class, () -> integer.set("2.5"));
        assertThrows(IllegalArgumentException.class, () -> doc.settings().get(1).set("NaN"));
        integer.set("3"); assertEquals("3", integer.value());
    }
    @Test void yamlAndTomlStayTypedAndEditable() throws Exception {
        for (String format : new String[]{"yaml", "toml"}) {
            var doc = doc(format, format.equals("yaml") ? "Enabled: true\nCount: 3\nName: '007'\n" : "Enabled = true\nCount = 3\nName = \"007\"\n");
            doc.settings().getFirst().set("false");
            var reloaded = doc(format, doc.serialize());
            assertFalse(Boolean.parseBoolean(reloaded.settings().getFirst().value()));
            assertEquals("007", reloaded.settings().get(2).value());
            assertFalse(reloaded.settings().get(2).number());
        }
    }
    @Test void rejectsDuplicatesAndUnreadableFormatsWithoutRawFallback() {
        assertThrows(java.io.IOException.class, () -> doc("json", "{\"x\":1,\"x\":2}"));
        assertThrows(java.io.IOException.class, () -> doc("yaml", "x: 1\nx: 2\n"));
        assertThrows(java.io.IOException.class, () -> doc("ini", "[plugin]\nvalue=1"));
    }
}
