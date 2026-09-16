package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.modtale.launcher.model.worldlist.WorldListConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldListConfigInstallerTest {
    @TempDir Path root;

    @Test void asksOncePerModAndHonorsIndependentDecisions() throws Exception {
        var configs = List.of(config("Alpha/one.json", "alpha"), config("OtherAlpha/two.json", "alpha"),
                config("Beta/config.json", "beta"), config("Beta/new.json", "beta"), config("Gamma/new.json", "gamma"));
        existing("Alpha/one.json");
        existing("OtherAlpha/two.json");
        existing("Beta/config.json");
        existing("Alpha/unrelated.json");
        var prompts = new ArrayList<List<WorldListConfig>>();
        var installed = WorldListConfigInstaller.install(configs, "WORLD", root, WorldListConfig.MAX_TOTAL_BYTES, conflicts -> {
            prompts.add(conflicts);
            return conflicts.getFirst().modIds().contains("alpha");
        });
        assertEquals(List.of(configs.subList(0, 2), List.of(configs.get(2))), prompts);
        assertEquals("included", Files.readString(root.resolve("Alpha/one.json")));
        assertEquals("included", Files.readString(root.resolve("OtherAlpha/two.json")));
        assertEquals("existing", Files.readString(root.resolve("Beta/config.json")));
        assertEquals("existing", Files.readString(root.resolve("Alpha/unrelated.json")));
        assertEquals("included", Files.readString(root.resolve("Beta/new.json")));
        assertEquals("included", Files.readString(root.resolve("Gamma/new.json")));
        assertEquals(4, installed.size());
    }

    @Test void legacyConfigsAreGroupedByPluginFolderAndDefaultToKeepingExisting() throws Exception {
        var configs = List.of(new WorldListConfig("WORLD", "Alpha/one.json", "included"),
                new WorldListConfig("WORLD", "Alpha/sub/two.json", "included"),
                new WorldListConfig("GLOBAL", "Beta/config.json", "included"));
        existing("Alpha/one.json");
        existing("Alpha/sub/two.json");
        assertTrue(WorldListConfigInstaller.install(configs, "WORLD", root).isEmpty());
        var prompts = new ArrayList<List<WorldListConfig>>();
        WorldListConfigInstaller.install(configs, "WORLD", root, WorldListConfig.MAX_TOTAL_BYTES, conflicts -> {
            prompts.add(conflicts);
            return false;
        });
        assertEquals(List.of(configs.subList(0, 2)), prompts);
        assertEquals("existing", Files.readString(root.resolve("Alpha/one.json")));
        assertFalse(Files.exists(root.resolve("Beta/config.json")));
    }

    @Test void rechecksDestinationsAfterPromptBeforeReplacing() throws Exception {
        existing("Alpha/config.json");
        Path outside = Files.writeString(root.resolve("outside.json"), "untouched");
        assertThrows(IOException.class, () -> WorldListConfigInstaller.install(
                List.of(config("Alpha/config.json", "alpha")), "WORLD", root, WorldListConfig.MAX_TOTAL_BYTES, conflicts -> {
                    try {
                        Files.delete(root.resolve("Alpha/config.json"));
                        Files.createSymbolicLink(root.resolve("Alpha/config.json"), outside);
                    } catch (IOException ex) { throw new RuntimeException(ex); }
                    return true;
                }));
        assertEquals("untouched", Files.readString(outside));
    }

    private WorldListConfig config(String path, String mod) {
        return new WorldListConfig("WORLD", path, "included", List.of(mod));
    }

    private void existing(String path) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "existing");
    }
}
