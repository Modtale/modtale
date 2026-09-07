package net.modtale.model.worldlist;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldListConfigTest {
    @Test void acceptsPortableConfigPathsAndPreservesContent() throws Exception {
        var configs = List.of(new WorldListConfig("WORLD", "Example_Plugin/nested/config.json", "{\r\n\"x\":2}"),
                new WorldListConfig("GLOBAL", "Example_Plugin/settings.toml", "value=2"));
        assertEquals(configs, WorldListConfig.validate(configs));
        assertEquals("configs/world/mods/Example_Plugin/nested/config.json", configs.getFirst().archivePath());
        assertEquals(List.of(), WorldListConfig.validate(null));
    }
    @Test void rejectsTraversalExecutablesDeviceNamesAndCollisions() {
        for (String path : List.of("../config.json", "A/../config.json", "/A/config.json", "A\\config.json",
                "A//config.json", "C:/config.json", "A/NUL.json", "A/config.json.", "A/install.js", "A/mod.zip", "A/manifest.json", "A/\n.json")) {
            assertThrows(IOException.class, () -> WorldListConfig.validate(List.of(new WorldListConfig("WORLD", path, "{}"))), path);
        }
        assertThrows(IOException.class, () -> WorldListConfig.validate(List.of(new WorldListConfig("OTHER", "A/config.json", "{}"))));
        assertThrows(IOException.class, () -> WorldListConfig.validate(List.of(
                new WorldListConfig("WORLD", "A/config.json", "{}"), new WorldListConfig("WORLD", "a/CONFIG.json", "{}"))));
    }
    @Test void limitsCountBytesAndBinaryData() {
        var file = new WorldListConfig("WORLD", "A/config.json", "{}");
        assertThrows(IOException.class, () -> WorldListConfig.validate(java.util.Collections.nCopies(101, file)));
        assertThrows(IOException.class, () -> WorldListConfig.validate(List.of(new WorldListConfig("WORLD", "A/config.json", "x".repeat(1024 * 1024 + 1)))));
        assertThrows(IOException.class, () -> WorldListConfig.validate(List.of(new WorldListConfig("WORLD", "A/config.json", "\0"))));
        assertThrows(IOException.class, () -> WorldListConfig.validate(List.of(
                new WorldListConfig("WORLD", "A/1.json", "x".repeat(1024 * 1024)),
                new WorldListConfig("WORLD", "A/2.json", "x".repeat(1024 * 1024)),
                new WorldListConfig("WORLD", "A/3.json", "x"))));
    }
}
