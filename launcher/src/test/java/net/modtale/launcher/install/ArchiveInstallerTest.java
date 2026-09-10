package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void enablingOneModSeedsOnlyItsDefaultsAndKeepsExistingSettings() throws IOException {
        Path jar = tempDir.resolve("owner.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            add(zip, "manifest.json", "{\"Group\":\"Example\",\"Name\":\"Owner\"}");
        }
        var ids = ArchiveInstaller.readModIds(List.of(jar));
        assertEquals(List.of("Example:Owner"), ids);
        var owned = new net.modtale.launcher.model.worldlist.WorldListConfig("WORLD", "CustomFolder/config.json", "{}", ids);
        var other = new net.modtale.launcher.model.worldlist.WorldListConfig("WORLD", "Other/config.json", "{}", List.of("Example:Other"));
        var selected = List.of(owned, other).stream().filter(config -> config.appliesTo(ids, false)).toList();
        assertEquals(List.of(owned), selected);
        Path universe = tempDir.resolve("universe/mods");
        WorldListConfigInstaller.install(selected, "WORLD", universe, 32 * 1024 * 1024);
        assertEquals("{}", Files.readString(universe.resolve("CustomFolder/config.json")));
        assertTrue(Files.notExists(universe.resolve("Other/config.json")));
        Files.writeString(universe.resolve("CustomFolder/config.json"), "custom");
        WorldListConfigInstaller.install(selected, "WORLD", universe, 32 * 1024 * 1024);
        assertEquals("custom", Files.readString(universe.resolve("CustomFolder/config.json")));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(owned, mapper.readValue(mapper.writeValueAsBytes(owned), net.modtale.launcher.model.worldlist.WorldListConfig.class));
    }

    @Test
    void copiesSingleDownloadedFileToModsDirectory() throws IOException {
        Path download = tempDir.resolve("download.tmp");
        Files.writeString(download, "jar");
        Path mods = tempDir.resolve("mods");

        List<Path> installed = new ArchiveInstaller().installDownloadedFile(download, "Cool Mod!.jar", mods, false);

        assertEquals(1, installed.size());
        assertEquals("Cool-Mod.jar", installed.getFirst().getFileName().toString());
        assertEquals("jar", Files.readString(installed.getFirst()));
    }

    @Test
    void extractsOnlyInstallableEntriesFromArchive() throws IOException {
        Path archive = tempDir.resolve("bundle.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "mod-one.jar", "one");
            add(zip, "nested/mod-two.zip", "two");
            add(zip, "modpack.json", "{}");
            add(zip, "../escape.jar", "safe");
        }
        Path mods = tempDir.resolve("mods");

        List<Path> installed = new ArchiveInstaller().extractInstallableEntries(archive, mods);

        assertEquals(3, installed.size());
        assertTrue(Files.exists(mods.resolve("mod-one.jar")));
        assertTrue(Files.exists(mods.resolve("mod-two.zip")));
        assertTrue(Files.exists(mods.resolve("escape.jar")));
        assertTrue(Files.notExists(tempDir.resolve("escape.jar")));
    }

    @Test
    void installsOnlyHytaleModFilesFromLegacyModpackArchive() throws IOException {
        Path archive = tempDir.resolve("modpack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "Example Pack/modpack.json", "{\"formatVersion\":1,\"game\":\"hytale\",\"files\":[]}");
            add(zip, "Example Pack/mods/first.jar", "first");
            add(zip, "Example Pack/mods/nested/second.hymod", "second");
            add(zip, "Example Pack/readme.txt", "readme");
        }
        Path mods = tempDir.resolve("mods");

        List<Path> installed = new ArchiveInstaller().installModpackArchive(archive, mods);

        assertEquals(2, installed.size());
        assertEquals("first", Files.readString(mods.resolve("first.jar")));
        assertEquals("second", Files.readString(mods.resolve("second.hymod")));
        assertTrue(Files.notExists(mods.resolve("readme.txt")));
        assertTrue(Files.notExists(mods.resolve("Example Pack")));
        assertTrue(Files.notExists(mods.resolve("mods")));
    }

    @Test
    void installsOriginalModtalePackWithoutFormatFields() throws IOException {
        Path archive = tempDir.resolve("original.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "modpack.json", """
                    {"name":"More Weapons, More Armor!","files":[
                      {"id":"df3549aa-3d2f-4d8d-9ccc-1f503915f9d9","version":"0.2.2"}]}
                    """);
            add(zip, "asset-packs/Weapons.zip", "weapons");
        }
        Path mods = tempDir.resolve("original-mods");
        assertEquals(1, new ArchiveInstaller().installModpackArchive(archive, mods).size());
        assertEquals("weapons", Files.readString(mods.resolve("Weapons.zip")));
    }

    @Test
    void rejectsModpackArchiveEntriesThatEscapeTheStagingDirectory() throws IOException {
        Path archive = tempDir.resolve("bad-modpack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "../escape.jar", "escape");
        }
        Path mods = tempDir.resolve("mods");

        assertThrows(IOException.class, () -> new ArchiveInstaller().installModpackArchive(archive, mods));
        assertTrue(Files.notExists(tempDir.resolve("escape.jar")));
        assertTrue(Files.notExists(mods.resolve("escape.jar")));
    }

    @Test
    void rejectsOverrideTraversalBeforeWritingInstanceFiles() throws IOException {
        Path archive = overrideArchive("overrides/Mods/../settings.json");
        Path instance = tempDir.resolve("instance");
        assertThrows(IOException.class, () -> new ArchiveInstaller().installModpackArchive(
                archive, instance.resolve("Mods"), instance));
        assertTrue(Files.notExists(instance.resolve("settings.json")));
    }

    @Test
    void rejectsOverridesThroughExistingSymbolicLinks() throws IOException {
        Path instance = tempDir.resolve("instance");
        Path mods = Files.createDirectories(instance.resolve("Mods"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        try {
            Files.createSymbolicLink(mods.resolve("linked"), outside);
        } catch (UnsupportedOperationException | IOException ex) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable: " + ex.getMessage());
        }
        Path archive = overrideArchive("overrides/Mods/linked/settings.json");
        assertThrows(IOException.class, () -> new ArchiveInstaller().installModpackArchive(archive, mods, instance));
        assertTrue(Files.notExists(outside.resolve("settings.json")));
    }

    @Test
    void rejectsSavedWorldPayloadBeforeWritingFiles() throws IOException {
        Path instance = tempDir.resolve("UserData");
        Path archive = overrideArchive("overrides/Saves/My World/universe/config.json");
        assertThrows(IOException.class, () -> new ArchiveInstaller().installModpackArchive(archive, instance.resolve("Mods"), instance));
        assertTrue(Files.notExists(instance.resolve("Saves")));
    }

    private Path overrideArchive(String entryPath) throws IOException {
        Path archive = tempDir.resolve("override.zip");
        String content = "settings";
        String lock = """
                {"format":"modtale-lock","lockVersion":1,"game":"hytale","entries":[],
                 "overrides":[{"path":"%s","size":%d,"hashes":{"sha256":"%s"}}]}
                """.formatted(entryPath, content.length(), sha256(content));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "modtale.lock.json", lock);
            add(zip, entryPath, content);
        }
        return archive;
    }

    @Test
    void rejectsSharedOverridePackBeforeInstallingMods() throws IOException {
        Path archive = tempDir.resolve("locked-modpack.zip");
        String bundled = "bundled mod";
        String settings = "settings";
        String preferences = "preferences";
        String world = "world settings";
        String lock = """
                {
                  "format":"modtale-lock",
                  "lockVersion":1,
                  "game":"hytale",
                  "entries":[
                    {"distribution":"BUNDLED","path":"mods/bundled.jar","size":%d,"hashes":{"sha256":"%s"}},
                    {"distribution":"REFERENCE_ONLY","url":"https://example.com/provider-file"}
                  ],
                  "overrides":[
                    {"path":"overrides/Mods/example/settings.json","size":%d,"hashes":{"sha256":"%s"}},
                    {"path":"overrides/Mods/example/ui.toml","size":%d,"hashes":{"sha256":"%s"}},
                    {"path":"overrides/Saves/example/config.json","size":%d,"hashes":{"sha256":"%s"}}
                  ]
                }
                """.formatted(
                bundled.length(), sha256(bundled),
                settings.length(), sha256(settings),
                preferences.length(), sha256(preferences),
                world.length(), sha256(world)
        );
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "modtale.lock.json", lock);
            add(zip, "mods/bundled.jar", bundled);
            add(zip, "overrides/Mods/example/settings.json", settings);
            add(zip, "overrides/Mods/example/ui.toml", preferences);
            add(zip, "overrides/Saves/example/config.json", world);
        }
        Path instance = tempDir.resolve("instance");
        Path mods = instance.resolve("mods");

        assertThrows(IOException.class, () -> new ArchiveInstaller().installModpackArchive(archive, mods, instance));
        assertTrue(Files.notExists(mods.resolve("bundled.jar")));
        assertTrue(Files.notExists(instance.resolve("Saves")));
    }

    @Test
    void universeDefaultsAreDeferredAndSeedEachChosenUniverseWithoutOverwriting() throws IOException {
        Path archive = tempDir.resolve("universe.zip");
        String hash = sha256("{}");
        String lock = """
            {"format":"modtale-lock","lockVersion":2,"game":"hytale",
             "entries":[{"id":"mod","source":"MODTALE","distribution":"BUNDLED","path":"mod.jar","size":2,"hashes":{"sha256":"%s"}}],
             "overrides":[{"path":"overrides/Universe/mods/Example/config.json","destination":"Universe/mods/Example/config.json","owner":{"projectId":"mod","source":"MODTALE"},"installPolicy":"SEED_ONLY","size":2,"hashes":{"sha256":"%s"}}]}
            """.formatted(hash, hash);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "modtale.lock.json", lock); add(zip, "mod.jar", "{}");
            add(zip, "overrides/Universe/mods/Example/config.json", "{}");
        }
        ArchiveInstaller installer = new ArchiveInstaller();
        Path instance = tempDir.resolve("instance");
        installer.installModpackArchive(archive, instance.resolve("Mods"), instance);
        assertTrue(Files.notExists(instance.resolve("Universe")));
        assertTrue(installer.readUniverseConfigs(archive, java.util.Set.of()).isEmpty());
        var configs = installer.readUniverseConfigs(archive, java.util.Set.of("MODTALE:mod"));
        for (String universe : List.of("Adventure", "Creative")) {
            Path root = instance.resolve("Saves").resolve(universe).resolve("mods");
            WorldListConfigInstaller.install(configs, "WORLD", root, 32 * 1024 * 1024);
            assertEquals("{}", Files.readString(root.resolve("Example/config.json")));
        }
        Path root = instance.resolve("Saves/Adventure/mods");
        Files.writeString(root.resolve("Example/config.json"), "{\"custom\":true}");
        WorldListConfigInstaller.install(configs, "WORLD", root, 32 * 1024 * 1024);
        assertEquals("{\"custom\":true}", Files.readString(root.resolve("Example/config.json")));
    }

    @Test
    void ownedConfigsFollowOptionalSelectionAndVerifiedExternalInstalls() throws IOException {
        Path archive = tempDir.resolve("owned-modpack.zip");
        String bytes = "{}";
        String hash = sha256(bytes);
        String lock = """
                {"format":"modtale-lock","lockVersion":2,"game":"hytale",
                 "entries":[
                  {"id":"optional","source":"MODTALE","dependencyType":"OPTIONAL","distribution":"BUNDLED","path":"mods/optional.jar","size":2,"hashes":{"sha256":"%s"}},
                  {"id":"cf","source":"CURSEFORGE","distribution":"REFERENCE_ONLY"}],
                 "overrides":[
                  {"path":"overrides/Universe/mods/Optional/config.json","destination":"Universe/mods/Optional/config.json","installPolicy":"SEED_ONLY","owner":{"projectId":"optional","source":"MODTALE"},"size":2,"hashes":{"sha256":"%s"}},
                  {"path":"overrides/Universe/mods/External/config.json","destination":"Universe/mods/External/config.json","installPolicy":"SEED_ONLY","owner":{"projectId":"cf","source":"CURSEFORGE"},"size":2,"hashes":{"sha256":"%s"}}]}
                """.formatted(hash, hash, hash);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "modtale.lock.json", lock);
            add(zip, "mods/optional.jar", bytes);
            add(zip, "overrides/Universe/mods/Optional/config.json", bytes);
            add(zip, "overrides/Universe/mods/External/config.json", bytes);
        }
        var installer = new ArchiveInstaller();
        Path instance = tempDir.resolve("owned-instance");
        Path mods = instance.resolve("Mods");
        assertTrue(installer.installModpackArchive(archive, mods, instance, java.util.Set.of()).isEmpty());
        assertTrue(Files.notExists(mods.resolve("Optional/config.json")));
        installer.installModpackArchive(archive, mods, instance, java.util.Set.of("MODTALE:optional"));
        assertTrue(Files.notExists(mods.resolve("Optional/config.json")));
        var external = installer.readUniverseConfigs(archive, java.util.Set.of("CURSEFORGE:cf"), java.util.Map.of("CURSEFORGE:cf", List.of("Example:External")));
        assertEquals(1, external.size());
        assertEquals(List.of("Example:External"), external.getFirst().modIds());
        assertEquals(bytes, external.getFirst().content());
    }

    @Test
    void rejectsLockedPackWithInvalidChecksumBeforeInstallingAnything() throws IOException {
        Path archive = tempDir.resolve("tampered-modpack.zip");
        String lock = """
                {"format":"modtale-lock","lockVersion":1,"game":"hytale","entries":[
                  {"distribution":"BUNDLED","path":"mods/bundled.jar","size":8,"hashes":{"sha256":"bad"}}
                ],"overrides":[]}
                """;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "modtale.lock.json", lock);
            add(zip, "mods/bundled.jar", "tampered");
        }
        Path mods = tempDir.resolve("instance/mods");

        assertThrows(IOException.class,
                () -> new ArchiveInstaller().installModpackArchive(archive, mods, tempDir.resolve("instance")));
        assertTrue(Files.notExists(mods.resolve("bundled.jar")));
    }

    @Test
    void rejectsNonHytaleLockedAndLegacyArchives() throws IOException {
        Path locked = tempDir.resolve("foreign-locked.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(locked))) {
            add(zip, "modtale.lock.json",
                    "{\"format\":\"modtale-lock\",\"lockVersion\":1,\"game\":\"minecraft\",\"entries\":[]}");
        }
        Path legacy = tempDir.resolve("foreign-legacy.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(legacy))) {
            add(zip, "modpack.json", "{\"formatVersion\":1,\"game\":\"minecraft\",\"files\":[]}");
            add(zip, "mod.jar", "foreign");
        }
        Path mods = tempDir.resolve("instance/mods");

        assertThrows(IOException.class,
                () -> new ArchiveInstaller().installModpackArchive(locked, mods, tempDir.resolve("instance")));
        assertThrows(IOException.class,
                () -> new ArchiveInstaller().installModpackArchive(legacy, mods, tempDir.resolve("instance")));
        assertTrue(Files.notExists(mods.resolve("mod.jar")));
    }

    @Test
    void avoidsOverwritingExistingFile() throws IOException {
        Path download = tempDir.resolve("download.tmp");
        Files.writeString(download, "new");
        Path mods = tempDir.resolve("mods");
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("mod.jar"), "old");

        List<Path> installed = new ArchiveInstaller().installDownloadedFile(download, "mod.jar", mods, false);

        assertEquals("mod-2.jar", installed.getFirst().getFileName().toString());
        assertEquals("old", Files.readString(mods.resolve("mod.jar")));
        assertEquals("new", Files.readString(installed.getFirst()));
    }

    private static void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
