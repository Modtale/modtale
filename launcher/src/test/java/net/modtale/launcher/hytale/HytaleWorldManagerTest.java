package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleWorldManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void updatesWorldModConfigUsingHytaleShape() throws Exception {
        Path config = tempDir.resolve(Path.of("Saves", "New World", "config.json"));
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
                {
                  "Version": 4,
                  "Backup": {
                    "Enabled": true
                  },
                  "Mods": {
                    "net.modtale:SimView": {
                      "Enabled": false
                    }
                  }
                }
                """);

        HytaleWorldManager manager = new HytaleWorldManager();
        manager.setModEnabled(config, "net.modtale:SimView", true);
        manager.setModEnabled(config, "club.championscombat:championscombat", false);

        JsonNode root = MAPPER.readTree(config.toFile());
        assertTrue(root.path("Backup").path("Enabled").asBoolean());
        assertTrue(root.path("Mods").path("net.modtale:SimView").path("Enabled").asBoolean());
        assertFalse(root.path("Mods").path("club.championscombat:championscombat").path("Enabled").asBoolean());
    }

    @Test
    void readsInstalledModIdsFromHytaleManifest() throws Exception {
        Path userData = tempDir.resolve("UserData");
        Files.createDirectories(userData.resolve("Saves"));
        Path mods = userData.resolve("Mods");
        Files.createDirectories(mods);
        writeJarManifest(mods.resolve("SimView-0.1.0.jar"), """
                {
                  "Group": "net.modtale",
                  "Name": "SimView",
                  "Version": "0.1.0",
                  "Description": "Separates visible chunks from simulated chunks."
                }
                """);

        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(userData.toString());
        settings.setHytaleModsPath(tempDir.resolve("unused-mods").toString());

        List<HytaleWorldManager.HytaleInstalledMod> installed = new HytaleWorldManager().loadInstalledMods(settings);

        assertEquals(1, installed.size());
        assertEquals("net.modtale:SimView", installed.getFirst().id());
        assertEquals("SimView", installed.getFirst().name());
    }

    @Test
    void readsWorldPreviewFromSaveFolder() throws Exception {
        Path userData = tempDir.resolve("UserData");
        Path world = userData.resolve(Path.of("Saves", "New World"));
        Files.createDirectories(world);
        Files.writeString(world.resolve("config.json"), """
                {
                  "Version": 4,
                  "Mods": {}
                }
                """);
        Files.writeString(world.resolve("client_metadata.json"), """
                {
                  "CreatedWithPatchline": "release"
                }
                """);
        Path preview = world.resolve("preview.png");
        Files.write(preview, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(userData.toString());

        List<HytaleWorldManager.HytaleWorld> worlds = new HytaleWorldManager().loadWorlds(settings);

        assertEquals(1, worlds.size());
        assertEquals(preview.toAbsolutePath().normalize().toUri().toString(), worlds.getFirst().previewImage());
    }

    @Test
    void readsWorldPreviewFromNestedMetadataPath() throws Exception {
        Path userData = tempDir.resolve("UserData");
        Path world = userData.resolve(Path.of("Saves", "Metadata Preview"));
        Path images = world.resolve("images");
        Files.createDirectories(images);
        Files.writeString(world.resolve("config.json"), """
                {
                  "Version": 4,
                  "Mods": {}
                }
                """);
        Path preview = images.resolve("world.webp");
        Files.write(preview, new byte[]{'R', 'I', 'F', 'F'});
        Files.writeString(world.resolve("client_metadata.json"), """
                {
                  "CreatedWithPatchline": "release",
                  "Client": {
                    "PreviewImagePath": "images/world.webp"
                  }
                }
                """);

        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(userData.toString());

        List<HytaleWorldManager.HytaleWorld> worlds = new HytaleWorldManager().loadWorlds(settings);

        assertEquals(1, worlds.size());
        assertEquals(preview.toAbsolutePath().normalize().toUri().toString(), worlds.getFirst().previewImage());
    }

    private void writeJarManifest(Path jar, String manifest) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry("manifest.json"));
            output.write(manifest.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
