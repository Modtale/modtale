package net.modtale.launcher.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.modtale.launcher.model.sync.LauncherConfigSnapshot;
import net.modtale.launcher.settings.LauncherSettings;

public final class LauncherConfigStore {
    private final HytaleConfigFiles files = new HytaleConfigFiles();

    public List<LauncherConfigSnapshot> capture(LauncherSettings settings) throws IOException {
        var captured = new LinkedHashMap<String, LauncherConfigSnapshot>();
        // Keep saved snapshots for temporarily unavailable worlds or mod folders.
        for (var config : LauncherConfigSnapshot.validate(settings.getConfigs())) {
            if (!HytaleConfigFiles.runtimeSnapshot(config.path())) captured.put(config.path(), config);
        }
        Path mods = settings.hytaleModsDirectory().toAbsolutePath().normalize();
        Path userData = settings.hytaleUserDataDirectory().toAbsolutePath().normalize();
        for (var file : files.discoverMods(mods)) {
            if (mods.relativize(file.path()).getNameCount() < 2) continue;
            capture(captured, "Mods/" + portable(mods.relativize(file.path())), file);
        }
        Path saves = userData.resolve("Saves");
        if (Files.isDirectory(saves, LinkOption.NOFOLLOW_LINKS)) {
            try (var worlds = Files.list(saves)) {
                var candidates = worlds.limit(1001).toList();
                if (candidates.size() > 1000) throw new IOException("Too many worlds to scan for config sync.");
                for (Path world : candidates) {
                    if (!Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)) continue;
                    for (var file : files.discoverWorld(world)) {
                        capture(captured, portable(userData.relativize(file.path())), file);
                    }
                }
            }
        }
        return LauncherConfigSnapshot.validate(new ArrayList<>(captured.values()));
    }

    private void capture(LinkedHashMap<String, LauncherConfigSnapshot> captured, String path,
            HytaleConfigFiles.ConfigFile file) throws IOException {
        captured.put(path, new LauncherConfigSnapshot(path, files.read(file).text()));
        LauncherConfigSnapshot.validate(new ArrayList<>(captured.values()));
    }

    /** Called only after the user chooses Load from Modtale. Existing files get backups. */
    public int restore(List<LauncherConfigSnapshot> configs, LauncherSettings settings) throws IOException {
        List<LauncherConfigSnapshot> valid = LauncherConfigSnapshot.validate(configs).stream()
                .filter(config -> !HytaleConfigFiles.runtimeSnapshot(config.path())).toList();
        for (var config : valid) destination(config, settings);
        int changed = 0;
        for (var config : valid) {
            var target = destination(config, settings);
            if (Files.exists(target.path(), LinkOption.NOFOLLOW_LINKS)) {
                var original = files.read(target);
                if (original.text().equals(config.content())) continue;
                files.save(original, config.content());
            } else {
                Files.createDirectories(target.path().getParent());
                destination(config, settings);
                Files.copy(new ByteArrayInputStream(config.content().getBytes(StandardCharsets.UTF_8)), target.path());
            }
            changed++;
        }
        return changed;
    }

    private HytaleConfigFiles.ConfigFile destination(LauncherConfigSnapshot config, LauncherSettings settings) throws IOException {
        boolean global = config.path().startsWith("Mods/");
        Path root = (global ? settings.hytaleModsDirectory() : settings.hytaleUserDataDirectory()).toAbsolutePath().normalize();
        Path path = root.resolve(global ? config.path().substring(5) : config.path()).normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(root)) throw new IOException("Unsafe synced config destination.");
        Path current = root;
        for (Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IOException("Synced config destination contains a symbolic link.");
        }
        if (Files.exists(path) && !Files.isRegularFile(path)) throw new IOException("Synced config destination is not a file.");
        return new HytaleConfigFiles.ConfigFile(root, path, config.path());
    }

    private static String portable(Path path) { return path.toString().replace('\\', '/'); }
}
