package net.modtale.service.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModpackOverrideArchiveTest {

    @Test
    void rejectsSavesSharedOverridesAndUnassociatedConfigs() throws Exception {
        for (String path : List.of("overrides/Saves/World/config.json", "overrides/Mods/Example/config.json", "overrides/Universe/mods/Example/config.json")) {
            assertThrows(IOException.class, () -> ModpackOverrideArchive.read(new ByteArrayInputStream(zip(Map.of(path, "{}")))));
        }
    }

    @Test
    void rejectsTraversalWrongRootsCaseCollisionsScriptsAndNestedArchives() throws Exception {
        for (Map<String, String> entries : List.of(
                Map.of("overrides/../secret.txt", "bad"),
                Map.of("config/game.json", "bad"),
                new LinkedHashMap<>(Map.of("overrides/Mods/A.txt", "one", "overrides/mods/a.TXT", "two")),
                Map.of("overrides/Mods/install.ps1", "bad"),
                Map.of("overrides/Mods/mods.zip", "bad"),
                Map.of("overrides/Mods/config/NUL.txt", "bad"),
                Map.of("overrides/config/settings.json", "not a Hytale root")
        )) {
            assertThrows(IOException.class, () -> ModpackOverrideArchive.read(new ByteArrayInputStream(zip(entries))));
        }
    }

    @Test
    void preservesAndValidatesConfigOwnershipAndChecksums() throws Exception {
        String path = "overrides/Universe/mods/Example/config.json";
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("{}".getBytes(StandardCharsets.UTF_8)));
        String manifest = "{\"format\":\"modtale-configs\",\"formatVersion\":1,\"configs\":[{\"projectId\":\"mod-1\",\"source\":\"MODTALE\",\"path\":\"" + path + "\",\"sha256\":\"" + hash + "\"}]}";
        var bundle = ModpackOverrideArchive.readBundle(new ByteArrayInputStream(zip(Map.of(path, "{}", ModpackOverrideArchive.CONFIG_MANIFEST, manifest))));
        assertEquals(1, bundle.files().size());
        assertEquals("MODTALE:mod-1", bundle.configs().getFirst().ownerKey());
        assertThrows(IOException.class, () -> ModpackOverrideArchive.validateOwners(bundle.configs(), List.of()));
        assertThrows(IOException.class, () -> ModpackOverrideArchive.readBundle(new ByteArrayInputStream(zip(Map.of(path, "{\"changed\":true}", ModpackOverrideArchive.CONFIG_MANIFEST, manifest)))));
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
