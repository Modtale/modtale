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
    void installsVerifiedLockedPackAndAppliesAllOverrides() throws IOException {
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

        List<Path> installed = new ArchiveInstaller().installModpackArchive(archive, mods, instance);

        assertEquals(4, installed.size());
        assertEquals(bundled, Files.readString(mods.resolve("bundled.jar")));
        assertEquals(settings, Files.readString(instance.resolve("Mods/example/settings.json")));
        assertEquals(preferences, Files.readString(instance.resolve("Mods/example/ui.toml")));
        assertEquals(world, Files.readString(instance.resolve("Saves/example/config.json")));
        assertTrue(Files.notExists(mods.resolve("modtale.lock.json")));
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
