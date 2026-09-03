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
    void readsOverridesAndPreservesPortablePaths() throws Exception {
        List<ModpackOverrideArchive.OverrideFile> files = ModpackOverrideArchive.read(new ByteArrayInputStream(zip(Map.of(
                "overrides/Mods/example/game.json", "{}",
                "overrides/Mods/example/ui.toml", "scale=2",
                "overrides/Saves/example/config.json", "{}"
        ))));

        assertEquals(3, files.size());
        assertTrue(files.stream().anyMatch(file -> file.path().equals("overrides/Mods/example/ui.toml")));
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
