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
    void readsLayeredOverridesAndPreservesPortablePaths() throws Exception {
        List<ModpackOverrideArchive.OverrideFile> files = ModpackOverrideArchive.read(new ByteArrayInputStream(zip(Map.of(
                "overrides/common/config/game.json", "{}",
                "overrides/client/config/ui.toml", "scale=2",
                "overrides/server/config/server.properties", "pvp=true"
        ))));

        assertEquals(3, files.size());
        assertTrue(files.stream().anyMatch(file -> file.path().equals("overrides/client/config/ui.toml")));
    }

    @Test
    void rejectsTraversalWrongRootsCaseCollisionsScriptsAndNestedArchives() throws Exception {
        for (Map<String, String> entries : List.of(
                Map.of("overrides/common/../secret.txt", "bad"),
                Map.of("config/game.json", "bad"),
                new LinkedHashMap<>(Map.of("overrides/common/A.txt", "one", "overrides/common/a.TXT", "two")),
                Map.of("overrides/client/install.ps1", "bad"),
                Map.of("overrides/server/mods.zip", "bad"),
                Map.of("overrides/common/config/NUL.txt", "bad")
        )) {
            assertThrows(IOException.class, () -> ModpackOverrideArchive.read(new ByteArrayInputStream(zip(entries))));
        }
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
