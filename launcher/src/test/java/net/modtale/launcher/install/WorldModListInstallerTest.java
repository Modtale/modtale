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
