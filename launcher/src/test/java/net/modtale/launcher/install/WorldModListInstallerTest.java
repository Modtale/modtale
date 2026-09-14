package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.worldlist.WorldListConfig;
import net.modtale.launcher.model.worldlist.WorldModList;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldModListInstallerTest {
    @TempDir Path directory;

    @Test void downloadsPinnedCurseForgeFilesDirectlyForCurseForgeOnlyList() throws Exception {
        Path archive = directory.resolve("list.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("modtale-list.json"));
            zip.write("{}".getBytes()); zip.closeEntry();
        }
        Path mod = Files.writeString(directory.resolve("download.jar"), "mod bytes");
        var item = new net.modtale.launcher.model.worldlist.WorldModListItem("", "Author:Example", "", "", "Example", "1.0", "PLUGIN",
                "CURSEFORGE", "curseforge:123", "https://www.curseforge.com/hytale/mods/example/files/456", "", false, "");
        var list = new WorldModList("list", "List", "World", "", "", null, null, null, 0, 0, 1, 0, "", "", "", List.of(item));
        var client = new ModtaleApiClient("https://example.invalid") {
            @Override public DownloadedFile download(String path) { return new DownloadedFile(archive, "list.zip", "application/zip"); }
            @Override public net.modtale.launcher.model.project.DownloadUrlResponse getCurseForgeDownloadUrl(long projectId, long fileId) {
                assertEquals(123, projectId); assertEquals(456, fileId);
                return null;
            }
            @Override public DownloadedFile download(net.modtale.launcher.model.project.DownloadUrlResponse response) {
                return new DownloadedFile(mod, "example-1.0.jar", "application/java-archive");
            }
        };
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleModsPath(directory.resolve("Mods").toString());
        var result = new WorldModListInstaller(client).install(list, settings);
        assertEquals(1, result.installedFiles().size());
        assertEquals("mod bytes", Files.readString(result.installedFiles().getFirst()));
        assertFalse(Files.exists(mod));
    }

    @Test void installsGlobalConfigsAndDefersWorldConfigsToWorldSelection() throws Exception {
        Path archive = directory.resolve("list.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("plugin.jar"));
            zip.write("binary".getBytes());
            zip.closeEntry();
        }
        var configs = List.of(new WorldListConfig("GLOBAL", "Example/config.json", "{}"),
                new WorldListConfig("WORLD", "Example/config.json", "{\"world\":true}"));
        var list = new WorldModList("list", "List", "Sender", "", "", null, null, null, 0, 0, 1, 1,
                "", "", "", List.of(), configs);
        var client = new ModtaleApiClient("https://example.invalid") {
            @Override public DownloadedFile download(String path) {
                assertEquals("/lists/list/download", path);
                return new DownloadedFile(archive, "list.zip", "application/zip");
            }
        };
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(directory.resolve("UserData").toString());
        settings.setHytaleModsPath(directory.resolve("UserData/Mods").toString());
        var result = new WorldModListInstaller(client).install(list, settings);
        assertEquals(2, result.installedFiles().size());
        assertEquals("{}", Files.readString(directory.resolve("UserData/Mods/Example/config.json")));
        assertFalse(Files.exists(directory.resolve("UserData/Saves/Sender")));
        assertEquals(configs, result.list().configs());
        assertFalse(Files.exists(archive));
    }
}
