package net.modtale.launcher.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.model.sync.LauncherConfigSnapshot;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherConfigStoreTest {
    @TempDir Path directory;
    private final LauncherConfigStore configs = new LauncherConfigStore();

    @Test void capturesPortableContentsAndPersistsThemInLocalSettings() throws Exception {
        LauncherSettings settings = settings("device-one");
        write(settings.hytaleModsDirectory().resolve("Example/settings.toml"), "value=2\r\n");
        write(settings.hytaleUserDataDirectory().resolve("Saves/My World/mods/Example/config.json"), "{\"x\":2}");
        write(settings.hytaleUserDataDirectory().resolve("Saves/My World/config.json"), "{}");
        write(settings.hytaleUserDataDirectory().resolve("auth.enc"), "not a config");
        write(settings.hytaleUserDataDirectory().resolve("Saves/My World/universe/players/player.json"), "{}");
        settings.setConfigs(configs.capture(settings));
        assertEquals(3, settings.getConfigs().size());
        assertTrue(settings.getConfigs().stream().allMatch(config -> !config.path().contains(directory.toString())));
        SettingsStore store = new SettingsStore(directory.resolve("launcher/settings.json"));
        store.save(settings);
        assertEquals(settings.getConfigs(), store.load().getConfigs());
        write(settings.hytaleModsDirectory().resolve("Example/settings.toml"), "value=3");
        assertTrue(configs.capture(settings).contains(new LauncherConfigSnapshot("Mods/Example/settings.toml", "value=3")));
    }

    @Test void restoresUnderRecipientPathsAndBacksUpChangedFiles() throws Exception {
        LauncherSettings target = settings("device-two");
        Path existing = target.hytaleModsDirectory().resolve("Example/config.json");
        write(existing, "{\"local\":true}");
        var snapshot = List.of(new LauncherConfigSnapshot("Mods/Example/config.json", "{\"profile\":true}"),
                new LauncherConfigSnapshot("Saves/My World/mods/Example/settings.ini", "value=2"));
        assertEquals(2, configs.restore(snapshot, target));
        assertEquals("{\"profile\":true}", Files.readString(existing));
        assertEquals("value=2", Files.readString(target.hytaleUserDataDirectory().resolve("Saves/My World/mods/Example/settings.ini")));
        try (var siblings = Files.list(existing.getParent())) {
            Path backup = siblings.filter(file -> file.toString().endsWith(".bak")).findFirst().orElseThrow();
            assertEquals("{\"local\":true}", Files.readString(backup));
        }
        assertEquals(0, configs.restore(snapshot, target));
    }

    @Test void rejectsUnsafePathsAndLinksBeforeRestoringFiles() throws Exception {
        LauncherSettings target = settings("device");
        assertThrows(IOException.class, () -> configs.restore(List.of(new LauncherConfigSnapshot("Mods/../config.json", "{}")), target));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Files.createDirectories(target.hytaleModsDirectory());
        Files.createSymbolicLink(target.hytaleModsDirectory().resolve("Linked"), outside);
        assertThrows(IOException.class, () -> configs.restore(List.of(
                new LauncherConfigSnapshot("Mods/Safe/config.json", "{}"),
                new LauncherConfigSnapshot("Mods/Linked/config.json", "{}")), target));
        assertFalse(Files.exists(target.hytaleModsDirectory().resolve("Safe/config.json")));
        assertFalse(Files.exists(outside.resolve("config.json")));
    }

    @Test void keepsSavedConfigsWhenWorldIsNotAvailableOnThisDevice() throws Exception {
        LauncherSettings settings = settings("new-device");
        var saved = List.of(new LauncherConfigSnapshot("Saves/Other World/mods/Example/config.json", "{}"));
        settings.setConfigs(saved);
        assertEquals(saved, configs.capture(settings));
    }

    private LauncherSettings settings(String name) {
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleModsPath(directory.resolve(name + "/custom-mods").toString());
        settings.setHytaleUserDataPath(directory.resolve(name + "/UserData").toString());
        return settings;
    }
    private void write(Path file, String value) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, value);
    }
}
