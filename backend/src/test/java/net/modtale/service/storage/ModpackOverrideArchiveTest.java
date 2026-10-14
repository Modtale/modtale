package net.modtale.service.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    @Test
    void rejectsImpossibleBundlesAtTheActualConfigLimitsBeforeRetainingTheirContents() throws Exception {
        String path = "overrides/Universe/mods/Example/config.json";
        var oversized = assertThrows(IOException.class, () -> ModpackOverrideArchive.readBundle(
                new ByteArrayInputStream(zip(Map.of(path, " ".repeat(1024 * 1024 + 1))))));
        assertTrue(oversized.getMessage().contains("1 MiB"));

        var entries = new LinkedHashMap<String, String>();
        for (int i = 0; i < 101; i++) entries.put("overrides/Universe/mods/Example/config-" + i + ".json", "{}");
        var tooMany = assertThrows(IOException.class, () -> ModpackOverrideArchive.readBundle(new ByteArrayInputStream(zip(entries))));
        assertTrue(tooMany.getMessage().contains("too many files"));
    }

    @Test
    void rejectsCentralDirectoryNameDisagreementAndTrailingPayload() throws Exception {
        byte[] valid = validZip();
        byte[] renamed = valid.clone();
        boolean changed = false;
        for (int i = 0; i <= renamed.length - 46; i++) {
            if (renamed[i] != 'P' || renamed[i + 1] != 'K' || renamed[i + 2] != 1 || renamed[i + 3] != 2) continue;
            int nameOffset = i + 46;
            String name = "overrides/Universe/mods/Example/config.json";
            if (nameOffset + name.length() <= renamed.length &&
                    Arrays.equals(Arrays.copyOfRange(renamed, nameOffset, nameOffset + name.length()), name.getBytes(StandardCharsets.UTF_8))) {
                renamed[nameOffset] = 'x';changed = true;break;
            }
        }
        assertTrue(changed);
        assertThrows(IOException.class, () -> ModpackOverrideArchive.readBundle(new ByteArrayInputStream(renamed)));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 7);
        System.arraycopy("payload".getBytes(StandardCharsets.UTF_8), 0, trailing, valid.length, 7);
        assertThrows(IOException.class, () -> ModpackOverrideArchive.readBundle(new ByteArrayInputStream(trailing)));
    }

    @Test
    void rejectsCentralDirectorySymlinksAndUndeclaredRecords() throws Exception {
        byte[] valid = validZip();
        byte[] symlink = valid.clone();
        int central = centralHeader(symlink);
        assertTrue(central >= 0);
        symlink[central + 41] = (byte) 0xa0;
        assertThrows(IOException.class, () -> ModpackOverrideArchive.readBundle(new ByteArrayInputStream(symlink)));

        byte[] omitted = valid.clone();
        int end = endHeader(omitted);
        assertTrue(end >= 0);
        omitted[end + 8] = 1; omitted[end + 10] = 1;
        assertThrows(IOException.class, () -> ModpackOverrideArchive.readBundle(new ByteArrayInputStream(omitted)));
    }

    private static int centralHeader(byte[] bytes) {
        for (int i = 0; i <= bytes.length - 46; i++)
            if (bytes[i] == 'P' && bytes[i + 1] == 'K' && bytes[i + 2] == 1 && bytes[i + 3] == 2) return i;
        return -1;
    }
    private static int endHeader(byte[] bytes) {
        for (int i = bytes.length - 22; i >= 0; i--)
            if (bytes[i] == 'P' && bytes[i + 1] == 'K' && bytes[i + 2] == 5 && bytes[i + 3] == 6) return i;
        return -1;
    }

    @Test
    void acceptsMatchingLocalAndCentralViewsWithAStandardZipComment() throws Exception {
        String path="overrides/Universe/mods/Example/config.json";
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("{}".getBytes(StandardCharsets.UTF_8)));
        String manifest="{\"format\":\"modtale-configs\",\"formatVersion\":1,\"configs\":[{\"projectId\":\"mod-1\",\"source\":\"MODTALE\",\"path\":\""
                +path+"\",\"sha256\":\""+hash+"\"}]}";
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)) {
            zip.setComment("ordinary archive comment");
            for(String directory:List.of("overrides/","overrides/Universe/","overrides/Universe/mods/","overrides/Universe/mods/Example/")) {
                zip.putNextEntry(new ZipEntry(directory));zip.closeEntry();
            }
            for(var item:Map.of(path,"{}",ModpackOverrideArchive.CONFIG_MANIFEST,manifest).entrySet()) {
                zip.putNextEntry(new ZipEntry(item.getKey()));
                zip.write(item.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
        }
        assertEquals(1,ModpackOverrideArchive.readBundle(new ByteArrayInputStream(output.toByteArray())).files().size());
    }

    private static byte[] validZip() throws Exception {
        String path="overrides/Universe/mods/Example/config.json";
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("{}".getBytes(StandardCharsets.UTF_8)));
        String manifest="{\"format\":\"modtale-configs\",\"formatVersion\":1,\"configs\":[{\"projectId\":\"mod-1\",\"source\":\"MODTALE\",\"path\":\""
                +path+"\",\"sha256\":\""+hash+"\"}]}";
        return zip(Map.of(path,"{}",ModpackOverrideArchive.CONFIG_MANIFEST,manifest));
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
