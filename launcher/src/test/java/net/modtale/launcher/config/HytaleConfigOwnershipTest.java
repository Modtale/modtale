package net.modtale.launcher.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.launcher.install.WorldListConfigInstaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleConfigOwnershipTest {
    @TempDir Path directory;

    @Test void matchesManifestIdentityRatherThanFilenameNameOrCase() throws Exception {
        jar("Mods/unrelated-download-name.jar", "{\"Group\":\"com.azuredoom\",\"Name\":\"levelingcore\"}");
        jar("Mods/other.jar", "{\"Group\":\"Other\",\"Name\":\"levelingcore\"}");
        config("world/mods/com.azuredoom_levelingcore/config.json");
        config("world/mods/Other_levelingcore/settings.toml");
        config("world/mods/com.azuredoom_Levelingcore/config.json");
        config("world/mods/unrelated-download-name/config.json");
        config("world/mods/levelingcore/config.json");
        var found = new HytaleConfigFiles().discover(directory.resolve("Mods"), directory.resolve("world"));
        assertEquals(5, found.size());
        assertEquals(2, found.stream().filter(file -> !file.pluginId().isEmpty()).count());
        assertTrue(found.stream().anyMatch(file -> file.pluginId().equals("com.azuredoom:levelingcore")
                && file.label().contains("World mods / com.azuredoom:levelingcore /")));
        assertEquals(3, found.stream().filter(file -> file.label().contains("Unattributed")).count());
    }

    @Test void handlesLocalWorldModsInheritedSubPluginsAndAmbiguousUnderscores() throws Exception {
        jar("world/mods/bundle.jar", "{\"Group\":\"Author\",\"Name\":\"Parent\",\"SubPlugins\":[{\"Name\":\"Child\"}]}");
        jar("Mods/one.jar", "{\"Group\":\"A_B\",\"Name\":\"C\"}");
        jar("Mods/two.jar", "{\"Group\":\"A\",\"Name\":\"B_C\"}");
        config("world/mods/Author_Child/config.json");
        config("world/mods/A_B_C/config.json");
        var found = new HytaleConfigFiles().discover(directory.resolve("Mods"), directory.resolve("world"));
        assertTrue(found.stream().anyMatch(file -> file.pluginId().equals("Author:Child")));
        assertTrue(found.stream().anyMatch(file -> file.pluginId().isEmpty() && file.label().contains("Ambiguous mod")));
    }

    @Test void excludesLooseAssetJsonAndDoesNotGuessCustomFolders() throws Exception {
        Path manifest = config("Mods/loose-pack/manifest.json");
        Files.writeString(manifest, "{\"Group\":\"Author\",\"Name\":\"Assets\"}");
        config("Mods/loose-pack/Server/Item/Items/example.json");
        jar("Mods/corrupt.jar", "{broken");
        config("world/mods/custom-author-folder/nested/settings.json");
        var found = new HytaleConfigFiles().discover(directory.resolve("Mods"), directory.resolve("world"));
        assertEquals(1, found.size());
        assertEquals("", found.getFirst().pluginId());
        assertTrue(found.getFirst().label().contains("Unattributed"));
    }

    @Test void sharedConfigsPreserveExactManifestFolderAndOwnerAfterRebasing() throws Exception {
        jar("Mods/modtale-project-slug-v2.jar", "{\"Group\":\"org.example_mods\",\"Name\":\"Fancy_Mod\"}");
        config("world/mods/org.example_mods_Fancy_Mod/nested/Gameplay.json");
        var capture = new WorldListConfigCapture();
        var found = capture.discover(directory.resolve("Mods"), directory.resolve("world"));
        assertEquals("org.example_mods:Fancy_Mod", found.getFirst().pluginId());
        var configs = capture.capture(found, directory.resolve("Mods"), directory.resolve("world"));
        assertEquals("org.example_mods_Fancy_Mod/nested/Gameplay.json", configs.getFirst().path());
        WorldListConfigInstaller.install(configs, "WORLD", directory.resolve("recipient/mods"));
        var restored = new HytaleConfigFiles().discover(directory.resolve("Mods"), directory.resolve("recipient"));
        assertEquals(List.of("org.example_mods:Fancy_Mod"), restored.stream().map(HytaleConfigFiles.ConfigFile::pluginId).toList());
    }

    private Path config(String relative) throws Exception {
        Path path = directory.resolve(relative); Files.createDirectories(path.getParent()); Files.writeString(path, "{}"); return path;
    }
    private void jar(String relative, String manifest) throws Exception {
        Path path = directory.resolve(relative); Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
    }
}
