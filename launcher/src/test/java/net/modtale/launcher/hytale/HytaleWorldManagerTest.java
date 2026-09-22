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
    void readsAssetPackManifestInsideZipFolder() throws Exception {
        Path userData = tempDir.resolve("UserData");
        Files.createDirectories(userData.resolve("Saves"));
        Path mods = Files.createDirectories(userData.resolve("Mods"));
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(mods.resolve("More_Armor.zip")))) {
            output.putNextEntry(new ZipEntry("More Armor/manifest.json"));
            output.write("{\"Group\":\"Charlock Castle\",\"Name\":\"More Armor\",\"Version\":\"0.1.2\"}".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(mods.resolve("download-bundle.zip")))) {
            output.putNextEntry(new ZipEntry("README.txt"));
            output.write("Extract the bundled mods".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(userData.toString());
        var installed = new HytaleWorldManager().loadInstalledMods(settings);
        assertEquals(1, installed.size());
        assertEquals("Charlock Castle:More Armor", installed.getFirst().id());
        assertEquals("0.1.2", installed.getFirst().version());
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

    @Test
    void resolvesHytaleDefaultsAndExplicitOverrides() throws Exception {
        Path userData = tempDir.resolve("UserData");
        Path mods = Files.createDirectories(userData.resolve("Mods"));
        writeJarManifest(mods.resolve("ordinary.jar"), """
                {"Group":"Author", "Name":"Ordinary"}
                """);
        writeJarManifest(mods.resolve("opt-in.jar"), """
                {"Group":"Author", "Name":"OptIn", "DisabledByDefault":true}
                """);
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(userData.toString());
        var manager = new HytaleWorldManager();
        var installed = manager.loadInstalledMods(settings);
        Path config = userData.resolve("config.json");
        for (String inherited : List.of("{}", "{\"Enabled\":null}", "{\"RequiredVersion\":\">=1.0.0\"}")) {
            Files.writeString(config, "{\"DefaultModsEnabled\":true,\"Mods\":{\"Author:Ordinary\":" + inherited + "}}");
            var enabled = manager.loadConfig(config, installed).enabledByMod();
            assertTrue(enabled.get("Author:Ordinary"));
            assertFalse(enabled.get("Author:OptIn"));
        }
        manager.setModEnabled(config, "Author:Ordinary", false);
        manager.setModEnabled(config, "Author:OptIn", true);
        var enabled = manager.loadConfig(config, installed).enabledByMod();
        assertFalse(enabled.get("Author:Ordinary"));
        assertTrue(enabled.get("Author:OptIn"));
        assertEquals(">=1.0.0", MAPPER.readTree(config.toFile()).path("Mods")
                .path("Author:Ordinary").path("RequiredVersion").asText());
        Files.writeString(config, "{}");
        assertTrue(manager.loadConfig(config, installed).enabledByMod().values().stream().noneMatch(Boolean::booleanValue));
    }

    @Test
    void togglesOnlySelectedSaveAndReadsSubsequentGameEdits() throws Exception {
        Path first = tempDir.resolve("Saves/First/config.json");
        Path second = tempDir.resolve("Saves/Second/config.json");
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        String original = """
                {"Version":4,"ModLoadOrder":["Author:Mod"],"Mods":{
                  "Author:Mod":{"Enabled":false,"RequiredVersion":"*"},
                  "Other:Mod":{"Enabled":true}},"Unknown":{"Keep":42}}
                """;
        Files.writeString(first, original);
        Files.writeString(second, original);
        var manager = new HytaleWorldManager();
        manager.setModEnabled(first, "Author:Mod", true);
        assertTrue(manager.loadConfig(first).enabledByMod().get("Author:Mod"));
        assertEquals(original, Files.readString(second));
        var root = MAPPER.readTree(first.toFile());
        assertEquals(42, root.path("Unknown").path("Keep").asInt());
        assertEquals("Author:Mod", root.path("ModLoadOrder").get(0).asText());
        assertTrue(root.path("Mods").path("Other:Mod").path("Enabled").asBoolean());
        Files.writeString(first, original);
        assertFalse(manager.loadConfig(first).enabledByMod().get("Author:Mod"));
    }

    @Test
    void doesNotDiscoverAnotherInstallWhenConfiguredUserDirectoryHasNoSaves() {
        LauncherSettings settings = new LauncherSettings();
        Path userData = tempDir.resolve("NewUserData");
        settings.setHytaleUserDataPath(userData.toString());
        var manager = new HytaleWorldManager();
        assertEquals(userData.resolve("Saves"), manager.savesDirectory(settings));
        assertTrue(manager.loadWorlds(settings).isEmpty());
    }

    @Test
    void ignoresArtifactsWithoutUsableHytaleIdentifiers() throws Exception {
        Path userData = tempDir.resolve("UserData");
        Path mods = Files.createDirectories(userData.resolve("Mods"));
        writeJarManifest(mods.resolve("missing-group.jar"), "{\"Name\":\"Example\"}");
        writeJarManifest(mods.resolve("missing-name.jar"), "{\"Group\":\"Author\"}");
        Files.writeString(mods.resolve("broken.jar"), "not an archive");
        writeJarManifest(mods.resolve("empty.jar"), "");
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(userData.toString());
        assertTrue(new HytaleWorldManager().loadInstalledMods(settings).isEmpty());
    }

    @Test
    void worldCountsIncludeInheritedModsAndIgnoreRemovedArtifacts() throws Exception {
        Path userData = tempDir.resolve("UserData");
        Path mods = Files.createDirectories(userData.resolve("Mods"));
        writeJarManifest(mods.resolve("mod.jar"), "{\"Group\":\"Author\",\"Name\":\"Mod\"}");
        Path world = Files.createDirectories(userData.resolve("Saves/World"));
        Files.writeString(world.resolve("config.json"), """
                {"DefaultModsEnabled":true,"Mods":{"Removed:Mod":{"Enabled":true}}}
                """);
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(userData.toString());
        var loaded = new HytaleWorldManager().loadWorlds(settings).getFirst();
        assertEquals(1, loaded.enabledMods());
        assertEquals(1, loaded.totalMods());
    }

    @Test
    void refusesToOverwriteMalformedConfig() throws Exception {
        var manager = new HytaleWorldManager();
        Path config = tempDir.resolve("config.json");
        for (String invalid : List.of("[]", "null", "", "{broken")) {
            Files.writeString(config, invalid);
            org.junit.jupiter.api.Assertions.assertThrows(net.modtale.launcher.api.ModtaleApiException.class,
                    () -> manager.setModEnabled(config, "Author:Mod", true));
            assertEquals(invalid, Files.readString(config));
        }
    }

    private void writeJarManifest(Path jar, String manifest) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry("manifest.json"));
            output.write(manifest.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
