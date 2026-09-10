package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.launcher.model.install.InstalledProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LibraryManifestCompatibilityTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"MODTALE", "LOCAL", "CURSEFORGE"})
    void everyProviderUsesInstalledManifestInsteadOfInstallBuild(String source) throws Exception {
        Path jar = archive("mod.jar", "{\"ServerVersion\":\">=0.5.0 <0.6.0\",\"Version\":\"1.2.3\"}");
        var project = installed(source, jar);
        assertEquals(List.of(">=0.5.0 <0.6.0"), new LibraryManifestCompatibility().forProject(project));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.5.5", ">=0.5.0 <0.6.0", ">=0.6.0-pre.0 <0.7.0", "^0.5.0", "2026.03.26-89796e57b", "*"})
    void preservesExactVersionsBoundsAndPrereleases(String requirement) throws Exception {
        Path jar = archive("mod.jar", "{\"ServerVersion\":\"" + requirement + "\"}");
        assertEquals(requirement, new LibraryManifestCompatibility().read(jar));
        assertEquals("Hytale " + (requirement.equals("*") ? "any version" : requirement),
                LibraryManifestCompatibility.label(requirement));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"Version\":\"1.0\",\"GameVersion\":\"0.5.5\"}",
            "{\"ServerVersion\":null}", "{\"ServerVersion\":[]}", "{\"ServerVersion\":42}",
            "{\"ServerVersion\":\"  \"}", "invalid json", "null"})
    void missingOrMalformedCompatibilityNeverInventsAValue(String manifest) throws Exception {
        Path jar = archive("mod.jar", manifest);
        assertEquals(List.of(), new LibraryManifestCompatibility().forProject(installed("MODTALE", jar)));
    }

    @Test void supportsZipAndUnpackedModsAndRefreshesChangedFiles() throws Exception {
        var reader = new LibraryManifestCompatibility();
        Path zip = archive("mod.zip", "{\"ServerVersion\":\"0.5.0\"}");
        Path directory = Files.createDirectory(temp.resolve("unpacked"));
        Path manifest = directory.resolve("manifest.json");
        Files.writeString(manifest, "{\"ServerVersion\":\"0.5.0\"}");
        for (Path file : List.of(zip, directory)) assertEquals("0.5.0", reader.read(file));
        FileTime changed = FileTime.fromMillis(System.currentTimeMillis() + 5000);
        archive("mod.zip", "{\"ServerVersion\":\"0.6.0\"}");
        Files.setLastModifiedTime(zip, changed);
        Files.writeString(manifest, "{\"ServerVersion\":\"0.6.0\"}");
        Files.setLastModifiedTime(manifest, changed);
        for (Path file : List.of(zip, directory)) assertEquals("0.6.0", reader.read(file));
        Files.delete(zip);
        assertEquals("", reader.read(zip));
        Files.delete(manifest);
        assertEquals("", reader.read(directory));
    }

    @Test void ignoresNestedManifestsAndOversizeOrBrokenArchives() throws Exception {
        Path zip = temp.resolve("nested.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("dependency/manifest.json"));
            out.write("{\"ServerVersion\":\"*\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var reader = new LibraryManifestCompatibility();
        assertEquals("", reader.read(zip));
        assertEquals("", reader.read(archive("large.jar", " ".repeat(1024 * 1024 + 1))));
        Path broken = Files.writeString(temp.resolve("broken.jar"), "not an archive");
        assertEquals("", reader.read(broken));
    }

    @Test void readsLinkedArtifactAndDoesNotInventCombinedPackCompatibility() throws Exception {
        Path jar = archive("mod.jar", "{\"ServerVersion\":\">=0.6.0\"}");
        Path link = temp.resolve("linked.jar");
        Files.createSymbolicLink(link, jar);
        var reader = new LibraryManifestCompatibility();
        assertEquals(List.of(">=0.6.0"), reader.forProject(installed("CURSEFORGE", link)));
        var pack = new InstalledProject("pack", "pack", "Pack", "MODPACK", "1.0", "v1", "0.5.5",
                null, null, List.of(jar.toString()), List.of(), List.of());
        assertEquals(List.of(), reader.forProject(pack));
        assertEquals(">=0.6.0", reader.read(jar));
    }

    static InstalledProject installed(String source, Path file) {
        return new InstalledProject("mod", "mod", "Mod", "PLUGIN", "mod-1.2.3.jar", "v1", "0.5.5",
                null, null, List.of(file.toString()), List.of(), List.of(), source, "", false, List.of());
    }

    private Path archive(String name, String manifest) throws Exception {
        Path path = temp.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("manifest.json"));
            out.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return path;
    }
}
