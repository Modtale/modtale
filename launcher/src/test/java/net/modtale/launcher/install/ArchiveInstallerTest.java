package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void installsModpackArchiveByFlatteningExtractedFolderIntoModsDirectory() throws IOException {
        Path archive = tempDir.resolve("modpack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "Example Pack/mods/first.jar", "first");
            add(zip, "Example Pack/mods/nested/second.hymod", "second");
            add(zip, "Example Pack/readme.txt", "readme");
        }
        Path mods = tempDir.resolve("mods");

        List<Path> installed = new ArchiveInstaller().installModpackArchive(archive, mods);

        assertEquals(3, installed.size());
        assertEquals("first", Files.readString(mods.resolve("first.jar")));
        assertEquals("second", Files.readString(mods.resolve("second.hymod")));
        assertEquals("readme", Files.readString(mods.resolve("readme.txt")));
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
}
