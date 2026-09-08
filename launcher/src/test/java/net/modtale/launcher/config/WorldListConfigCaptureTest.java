package net.modtale.launcher.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.install.WorldListConfigInstaller;
import net.modtale.launcher.model.worldlist.WorldListConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldListConfigCaptureTest {
    @TempDir Path directory;

    @Test void capturesSelectedConfigsAndRebasesThemIntoRecipientWorld() throws Exception {
        Path source = directory.resolve("Sender");
        Path global = directory.resolve("Mods");
        Files.createDirectories(source.resolve("mods/Example_Plugin"));
        Files.writeString(source.resolve("mods/Example_Plugin/config.json"), "{\"value\":2}");
        Files.writeString(source.resolve("config.json"), "{\"privateWorldState\":true}");
        Files.createDirectories(global.resolve("Other"));
        Files.writeString(global.resolve("Other/settings.ini"), "value=3");
        WorldListConfigCapture capture = new WorldListConfigCapture();
        var found = capture.discover(global, source);
        assertEquals(2, found.size());
        var configs = capture.capture(found.stream().filter(file -> file.label().startsWith("World mods")).toList(), global, source);
        assertEquals(List.of(new WorldListConfig("WORLD", "Example_Plugin/config.json", "{\"value\":2}")), configs);
        Path target = directory.resolve("Recipient/mods");
        assertEquals(1, WorldListConfigInstaller.install(configs, "WORLD", target).size());
        Path file = target.resolve("Example_Plugin/config.json");
        assertEquals("{\"value\":2}", Files.readString(file));
        Files.writeString(file, "{\"player\":true}");
        assertTrue(WorldListConfigInstaller.install(configs, "WORLD", target).isEmpty());
        assertEquals("{\"player\":true}", Files.readString(file));
        assertFalse(Files.exists(directory.resolve("Recipient/config.json")));
    }

    @Test void rejectsLinkedDestinationsBeforeWritingAnyConfig() throws Exception {
        Path root = directory.resolve("mods");
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Files.createDirectories(root);
        Files.createSymbolicLink(root.resolve("linked"), outside);
        var configs = List.of(new WorldListConfig("WORLD", "Safe/config.json", "{}"),
                new WorldListConfig("WORLD", "linked/config.json", "{}"));
        assertThrows(java.io.IOException.class, () -> WorldListConfigInstaller.install(configs, "WORLD", root));
        assertFalse(Files.exists(root.resolve("Safe/config.json")));
        assertFalse(Files.exists(outside.resolve("config.json")));
    }
}
