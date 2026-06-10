package net.modtale.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileValidationServiceTest {

    private final FileValidationService fileValidationService = new FileValidationService(
            new ProjectArchiveValidationService(new PluginManifestValidationService(new ObjectMapper())),
            new ProjectImageValidationService()
    );

    @Test
    void resolveUploadClassificationInfersArchiveTypeFromFilename() throws IOException {
        MockMultipartFile jar = archive("plugin.jar", Map.of("manifest.json", validManifest().getBytes(StandardCharsets.UTF_8)));
        MockMultipartFile zip = archive("bundle.zip", Map.of("data/file.txt", "ok".getBytes(StandardCharsets.UTF_8)));

        assertEquals("PLUGIN", fileValidationService.resolveUploadClassification("DATA", jar));
        assertEquals("DATA", fileValidationService.resolveUploadClassification("PLUGIN", zip));
        assertEquals("MODPACK", fileValidationService.resolveUploadClassification("MODPACK", jar));
        assertEquals("CUSTOM", fileValidationService.resolveUploadClassification("CUSTOM", jar));
    }

    @Test
    void validateProjectFileReturnsManifestInspectionForValidPluginArchives() throws IOException {
        MockMultipartFile plugin = archive(
                "sky-tools.jar",
                orderedEntries(
                        "manifest.json", validManifest().getBytes(StandardCharsets.UTF_8),
                        "net/modtale/Main.class", new byte[]{0x01, 0x02, 0x03}
                )
        );

        FileValidationService.ManifestInspection inspection = fileValidationService.validateProjectFile(plugin, "PLUGIN");

        assertEquals("net.modtale", inspection.getGroup());
        assertEquals("SkyTools", inspection.getName());
        assertEquals("1.2.3", inspection.getVersion());
        assertEquals("0.9.0", inspection.getServerVersion());
        assertEquals(2, inspection.getDependencies().size());

        FileValidationService.ManifestDependency required = inspection.getDependencies().stream()
                .filter(dep -> dep.getKey().equals("modtale:core"))
                .findFirst()
                .orElseThrow();
        FileValidationService.ManifestDependency optional = inspection.getDependencies().stream()
                .filter(dep -> dep.getKey().equals("modtale:ui"))
                .findFirst()
                .orElseThrow();

        assertEquals("^1.0.0", required.getVersion());
        assertFalse(required.isOptional());
        assertEquals("ui", optional.getNamePart());
        assertTrue(optional.isOptional());
    }

    @Test
    void validateProjectFileRejectsPluginArchivesMissingManifest() throws IOException {
        MockMultipartFile plugin = archive(
                "sky-tools.jar",
                Map.of("net/modtale/Main.class", new byte[]{0x01, 0x02, 0x03})
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fileValidationService.validateProjectFile(plugin, "PLUGIN")
        );

        assertEquals("Plugin archive must include manifest.json at the archive root.", error.getMessage());
    }

    @Test
    void validateProjectFileRejectsPluginManifestsMissingRequiredFields() throws IOException {
        String invalidManifest = """
                {
                  "Group": "net.modtale",
                  "Name": "SkyTools",
                  "Version": "1.2.3",
                  "ServerVersion": "0.9.0",
                  "Authors": [{ "Name": "Ada" }]
                }
                """;
        MockMultipartFile plugin = archive("sky-tools.jar", Map.of("manifest.json", invalidManifest.getBytes(StandardCharsets.UTF_8)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fileValidationService.validateProjectFile(plugin, "PLUGIN")
        );

        assertEquals("Plugin manifest.json is missing required field: Main", error.getMessage());
    }

    @Test
    void validateProjectFileRejectsPathTraversalInArchives() throws IOException {
        MockMultipartFile archive = archive(
                "textures.zip",
                Map.of("../evil.txt", "bad".getBytes(StandardCharsets.UTF_8))
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fileValidationService.validateProjectFile(archive, "DATA")
        );

        assertEquals("Archive contains malicious path traversal: ../evil.txt", error.getMessage());
    }

    @Test
    void validateProjectFileRejectsBlockedFilesForDataProjects() throws IOException {
        MockMultipartFile archive = archive(
                "textures.zip",
                Map.of("scripts/install.sh", "echo nope".getBytes(StandardCharsets.UTF_8))
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fileValidationService.validateProjectFile(archive, "DATA")
        );

        assertTrue(error.getMessage().contains("DATA projects cannot contain .sh files"));
    }

    @Test
    void validateProjectFileRejectsNestedArchivesForArtProjects() throws IOException {
        MockMultipartFile archive = archive(
                "gallery.zip",
                Map.of("assets/backup.zip", new byte[]{0x50, 0x4B, 0x03, 0x04})
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fileValidationService.validateProjectFile(archive, "ART")
        );

        assertEquals("Security Violation: Nested archives (.zip) are not allowed in ART", error.getMessage());
    }

    private static String validManifest() {
        return """
                {
                  "Group": "net.modtale",
                  "Name": "SkyTools",
                  "Version": "1.2.3",
                  "ServerVersion": "0.9.0",
                  "Main": "net.modtale.SkyTools",
                  "Authors": [{ "Name": "Ada" }],
                  "Dependencies": {
                    "modtale:core": "^1.0.0",
                    "Hytale:Server": "^0.9.0"
                  },
                  "OptionalDependencies": {
                    "modtale:ui": "^2.0.0"
                  }
                }
                """;
    }

    private static Map<String, byte[]> orderedEntries(String firstName, byte[] firstValue, String secondName, byte[] secondValue) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(firstName, firstValue);
        entries.put(secondName, secondValue);
        return entries;
    }

    private static MockMultipartFile archive(String filename, Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", filename, "application/zip", out.toByteArray());
    }
}
