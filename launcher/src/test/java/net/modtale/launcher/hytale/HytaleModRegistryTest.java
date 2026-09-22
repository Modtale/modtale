package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleModRegistryTest {
    @TempDir Path root;
    static final ObjectMapper JSON = new ObjectMapper();

    @Test void importsPerfectParriesWithProviderMetadataAndReplacesLocalRecord() throws Exception {
        Path file = jar("Perfect Parries.jar", "0.9.4");
        ObjectNode entry = entry(file, "8852275");
        writeRegistry("curseforge:1429041", entry);
        var local = project(file, "local:parry", "", InstalledProject.SOURCE_LOCAL);
        var registry = new HytaleModRegistry(root);
        var projects = registry.importProjects(List.of(local));
        assertEquals(1, projects.size());
        var imported = projects.getFirst();
        assertEquals("curseforge:1429041", imported.projectId());
        assertEquals("8852275", imported.installedVersionId());
        assertEquals("CURSEFORGE", imported.source());
        assertEquals(file.toRealPath().toString(), imported.files().getFirst());
        assertEquals("narwhals", registry.metadata().get(imported.projectId()).author());
        assertEquals(projects, registry.importProjects(projects));
    }

    @Test void exportsExactBinaryAndKeepsModtaleIdentityAcrossNativeUpdatesAndRemoval() throws Exception {
        Path file = jar("LevelingCore-1.jar", "1.0");
        var project = project(file, "modtale-leveling", "modtale-version-1", "MODTALE");
        var registry = new HytaleModRegistry(root);
        registry.exportProjects(List.of(project), api(file));
        var original = JSON.readTree(root.resolve(".installed.json").toFile());
        assertEquals("curseforge:123", original.path("mods").path("curseforge:42").path("fileId").asText());
        assertEquals(List.of(project), registry.importProjects(List.of(project)));
        // Hytale replaces the entry entirely and renames the binary on update.
        Path updated = jar("LevelingCore-2.jar", "2.0");
        Files.delete(file);
        writeRegistry("curseforge:42", entry(updated, "124"));
        var refreshed = registry.importProjects(List.of(project));
        assertEquals(1, refreshed.size());
        assertEquals("modtale-leveling", refreshed.getFirst().projectId());
        assertEquals("MODTALE", refreshed.getFirst().source());
        assertEquals("2.0", refreshed.getFirst().installedVersion());
        assertEquals("", refreshed.getFirst().installedVersionId());
        assertEquals(updated.toRealPath().toString(), refreshed.getFirst().files().getFirst());
        Files.delete(updated);
        writeRegistry(null, null);
        assertTrue(registry.importProjects(refreshed).isEmpty());
    }

    @Test void rejectsTraversalTamperingAndWhitespaceFingerprintCollisions() throws Exception {
        Path file = jar("mod.jar", "1");
        var registry = new HytaleModRegistry(root);
        ObjectNode entry = entry(file, "123");
        entry.put("relativePath", "../outside.jar");
        writeRegistry("curseforge:42", entry);
        assertTrue(registry.importProjects(List.of()).isEmpty());
        entry = entry(file, "123");
        entry.put("sha1", "0000000000000000000000000000000000000000");
        writeRegistry("curseforge:42", entry);
        assertTrue(registry.importProjects(List.of()).isEmpty());
        ModtaleApiClient mismatch = new ModtaleApiClient("http://localhost") {
            @Override public JsonNode matchCurseForgeFiles(List<Long> fingerprints) {
                var response = response(file);
                ((ObjectNode) response.path("data").path("exactMatches").path(0).path("file").path("hashes").path(0))
                        .put("value", "0000000000000000000000000000000000000000");
                return response;
            }
        };
        registry.exportProjects(List.of(project(file, "mt", "1", "MODTALE")), mismatch);
        assertEquals(JSON.readTree(entry.toString()), JSON.readTree(root.resolve(".installed.json").toFile()).path("mods").path("curseforge:42"));
    }

    @Test void preservesUnrelatedEntriesUnknownFieldsAndMalformedRegistry() throws Exception {
        Path file = jar("mod.jar", "1");
        Path other = jar("other.jar", "1");
        ObjectNode otherEntry = entry(other, "999");
        otherEntry.put("futureField", "keep");
        writeRegistry("curseforge:999", otherEntry);
        var registry = new HytaleModRegistry(root);
        registry.exportProjects(List.of(project(file, "mt", "1", "MODTALE")), api(file));
        assertEquals(JSON.readTree(otherEntry.toString()), JSON.readTree(root.resolve(".installed.json").toFile()).path("mods").path("curseforge:999"));
        Files.delete(file);
        registry.removeFiles(List.of(file.toString()));
        var mods = JSON.readTree(root.resolve(".installed.json").toFile()).path("mods");
        assertFalse(mods.has("curseforge:42"));
        assertEquals(JSON.readTree(otherEntry.toString()), mods.path("curseforge:999"));
        Files.writeString(root.resolve(".installed.json"), "broken");
        assertThrows(java.io.IOException.class, () -> registry.exportProjects(List.of(), api(other)));
        assertEquals("broken", Files.readString(root.resolve(".installed.json")));
    }

    @Test void importsNativeUpdateOfAnExistingCurseForgeProjectWithoutDuplicates() throws Exception {
        Path old = jar("old.jar", "1");
        var previous = project(old, "curseforge:42", "122", "CURSEFORGE");
        Path current = jar("current.jar", "2");
        Files.delete(old);
        writeRegistry("curseforge:42", entry(current, "123"));
        var result = new HytaleModRegistry(root).importProjects(List.of(previous));
        assertEquals(1, result.size());
        assertEquals("123", result.getFirst().installedVersionId());
        assertEquals(List.of(current.toRealPath().toString()), result.getFirst().files());
    }

    @Test void removesDeletedFilesRecordedThroughADirectoryAlias() throws Exception {
        Path file = jar("mod.jar", "1");
        Path alias = root.resolve("mods-alias");
        try {
            Files.createSymbolicLink(alias, root.toRealPath());
        } catch (UnsupportedOperationException | java.io.IOException ex) {
            org.junit.jupiter.api.Assumptions.abort("Directory symlinks unavailable: " + ex.getMessage());
        }
        Path recorded = alias.resolve(file.getFileName());
        var registry = new HytaleModRegistry(alias);
        registry.exportProjects(List.of(project(recorded, "mt", "1", "MODTALE")), api(file));
        assertTrue(JSON.readTree(root.resolve(".installed.json").toFile()).path("mods").has("curseforge:42"));

        Files.delete(file);
        registry.removeFiles(List.of(recorded.toString()));

        assertFalse(JSON.readTree(root.resolve(".installed.json").toFile()).path("mods").has("curseforge:42"));
    }

    private ModtaleApiClient api(Path file) {
        return new ModtaleApiClient("http://localhost") {
            @Override public JsonNode matchCurseForgeFiles(List<Long> fingerprints) { return response(file); }
            @Override public ProjectMeta getProjectMeta(String id) {
                return new ProjectMeta("LevelingCore", "", "https://example.com/icon.png", "AzureDoom", "MOD", 0, "", id);
            }
        };
    }

    private JsonNode response(Path file) {
        try {
            var response = JSON.createObjectNode();
            var match = response.putObject("data").putArray("exactMatches").addObject().put("id", 42);
            var remote = match.putObject("file").put("id", 123).put("modId", 42).put("gameId", 70216)
                    .put("fileFingerprint", ArtifactFingerprint.calculate(file).curseForgeFingerprint())
                    .put("fileLength", Files.size(file)).put("displayName", "1.0");
            remote.putArray("gameVersions").add("0.6");
            remote.putArray("hashes").addObject().put("algo", 1).put("value", sha1(file));
            return response;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private Path jar(String name, String version) throws Exception {
        Path file = root.resolve(name);
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(("{\"Version\":\"" + version + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    private ObjectNode entry(Path file, String fileId) throws Exception {
        var entry = JSON.createObjectNode().put("fileId", "curseforge:" + fileId)
                .put("relativePath", file.getFileName().toString()).put("fileName", file.getFileName().toString())
                .put("isWorld", false).put("name", "Perfect Parries").put("author", "narwhals")
                .put("logoUrl", "https://example.com/icon.png").put("displayVersion", "0.9.4")
                .put("sha1", sha1(file)).put("fileLength", Files.size(file)).put("installedAt", 1789603498);
        entry.putArray("gameVersions").add("0.6");
        return entry;
    }

    private static String sha1(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    }

    private void writeRegistry(String id, JsonNode entry) throws Exception {
        var document = JSON.createObjectNode().put("version", 1);
        var mods = document.putObject("mods");
        if (id != null) mods.set(id, entry);
        JSON.writeValue(root.resolve(".installed.json").toFile(), document);
    }

    private InstalledProject project(Path file, String id, String versionId, String source) {
        return new InstalledProject(id, id, "LevelingCore", "PLUGIN", "1.0", versionId, "0.6", Instant.EPOCH,
                Instant.EPOCH, List.of(file.toString()), List.of(), List.of(), source, "DIRECT", false, List.of());
    }
}
