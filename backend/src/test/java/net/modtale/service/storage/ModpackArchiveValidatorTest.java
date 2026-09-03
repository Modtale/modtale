package net.modtale.service.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModpackArchiveValidatorTest {

    @Test
    void acceptsAnArchiveWhoseBundledBytesMatchTheLockfile() throws Exception {
        byte[] content = bytes("trusted");
        byte[] archive = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", lockEntry("MODTALE", "BUNDLED", "plugin.jar", content, false)),
                new ArchiveEntry("plugin.jar", content)
        ));

        assertDoesNotThrow(() -> ModpackArchiveValidator.validate(archive));
    }

    @Test
    void rejectsHashMismatch() throws Exception {
        byte[] content = bytes("tampered");
        String lock = lockEntry("MODTALE", "BUNDLED", "plugin.jar", content, false)
                .replace(sha256(content), "0".repeat(64));
        byte[] archive = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", lock),
                new ArchiveEntry("plugin.jar", content)
        ));

        IOException error = assertThrows(IOException.class, () -> ModpackArchiveValidator.validate(archive));
        assertTrue(error.getMessage().contains("hash does not match"));
    }

    @Test
    void rejectsTraversalAndCaseFoldedDuplicatePaths() throws Exception {
        byte[] traversal = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", emptyLock()),
                entry("../escape.jar", "bad")
        ));
        byte[] collision = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", emptyLock()),
                entry("Plugin.jar", "one"),
                entry("plugin.JAR", "two")
        ));

        assertTrue(assertThrows(IOException.class,
                () -> ModpackArchiveValidator.validate(traversal)).getMessage().contains("unsafe path"));
        assertTrue(assertThrows(IOException.class,
                () -> ModpackArchiveValidator.validate(collision)).getMessage().contains("case-colliding"));
    }

    @Test
    void rejectsUndeclaredFilesAndBundledCurseForgeArtifacts() throws Exception {
        byte[] content = bytes("provider-file");
        byte[] undeclared = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", emptyLock()),
                new ArchiveEntry("surprise.jar", content)
        ));
        byte[] curseForge = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", lockEntry("CURSEFORGE", "BUNDLED", "curseforge.jar", content, false)),
                new ArchiveEntry("curseforge.jar", content)
        ));

        assertTrue(assertThrows(IOException.class,
                () -> ModpackArchiveValidator.validate(undeclared)).getMessage().contains("not declared"));
        assertTrue(assertThrows(IOException.class,
                () -> ModpackArchiveValidator.validate(curseForge)).getMessage().contains("reference-only"));
    }

    @Test
    void rejectsReferenceEntriesThatClaimBundledIntegrityFields() throws Exception {
        byte[] archive = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", lockEntry(
                        "CURSEFORGE",
                        "REFERENCE_ONLY",
                        "should-not-exist.jar",
                        bytes("not bundled"),
                        true
                ))
        ));

        assertTrue(assertThrows(IOException.class,
                () -> ModpackArchiveValidator.validate(archive)).getMessage().contains("must not claim bundled bytes"));
    }

    @Test
    void acceptsCanonicalCurseForgeReferencesAndRejectsLookalikeHosts() throws Exception {
        String canonical = curseForgeLock("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810");
        String lookalike = curseForgeLock("https://www.curseforge.com.evil.example/hytale/mods/simple-compost/files/8227810");

        byte[] valid = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", canonical)
        ));
        byte[] invalid = archive(List.of(
                entry("modpack.json", legacyManifest()),
                entry("manifest.json", manifest()),
                entry("modtale.lock.json", lookalike)
        ));

        assertDoesNotThrow(() -> ModpackArchiveValidator.validate(valid));
        assertTrue(assertThrows(IOException.class,
                () -> ModpackArchiveValidator.validate(invalid)).getMessage().contains("invalid file-page URL"));
    }

    private static String emptyLock() {
        return """
                {
                  "format": "modtale-lock",
                  "lockVersion": 1,
                  "pack": {},
                  "gameVersions": [],
                  "entries": []
                }
                """;
    }

    private static String curseForgeLock(String fileUrl) {
        return """
                {
                  "format": "modtale-lock",
                  "lockVersion": 1,
                  "pack": {},
                  "gameVersions": [],
                  "entries": [{
                    "id": "",
                    "title": "Simple Compost",
                    "version": "1.0.0",
                    "source": "CURSEFORGE",
                    "dependencyType": "REQUIRED",
                    "distribution": "REFERENCE_ONLY",
                    "url": "%s",
                    "fileUrl": "%s",
                    "provider": {"projectId": "1450386", "fileId": "8227810"}
                  }]
                }
                """.formatted(fileUrl, fileUrl);
    }

    private static String lockEntry(
            String source,
            String distribution,
            String path,
            byte[] content,
            boolean includeIntegrityForReference
    ) throws Exception {
        String integrity = "BUNDLED".equals(distribution) || includeIntegrityForReference
                ? """
                    ,"path":"%s","size":%d,"hashes":{"sha256":"%s"}
                  """.formatted(path, content.length, sha256(content))
                : "";
        return """
                {
                  "format": "modtale-lock",
                  "lockVersion": 1,
                  "pack": {},
                  "gameVersions": [],
                  "entries": [
                    {"source":"%s","distribution":"%s"%s}
                  ]
                }
                """.formatted(source, distribution, integrity);
    }

    private static String legacyManifest() {
        return """
                {"formatVersion":1,"game":"hytale","files":[]}
                """;
    }

    private static String manifest() {
        return """
                {
                  "format": "modtale-pack",
                  "schemaVersion": 1,
                  "pack": {},
                  "game": {},
                  "dependencies": []
                }
                """;
    }

    private static ArchiveEntry entry(String name, String content) {
        return new ArchiveEntry(name, bytes(content));
    }

    private static byte[] archive(List<ArchiveEntry> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (ArchiveEntry value : entries) {
                ZipEntry entry = new ZipEntry(value.name());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(value.content());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record ArchiveEntry(String name, byte[] content) {}
}
