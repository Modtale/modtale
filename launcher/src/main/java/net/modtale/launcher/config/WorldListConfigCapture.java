package net.modtale.launcher.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;
import net.modtale.launcher.model.worldlist.WorldListConfig;

public final class WorldListConfigCapture {
    private final HytaleConfigFiles files = new HytaleConfigFiles();

    public List<ConfigFile> discover(Path globalMods, Path world) throws IOException {
        Path worldMods = world.resolve("mods").toAbsolutePath().normalize();
        Path global = globalMods.toAbsolutePath().normalize();
        return files.discover(globalMods, world).stream()
                .filter(file -> file.root().equals(worldMods) || file.root().equals(global))
                .filter(file -> file.root().relativize(file.path()).getNameCount() >= 2).toList();
    }

    public List<WorldListConfig> capture(List<ConfigFile> selected, Path globalMods, Path world) throws IOException {
        Path global = globalMods.toAbsolutePath().normalize();
        Path worldMods = world.resolve("mods").toAbsolutePath().normalize();
        if (selected.size() > WorldListConfig.MAX_FILES) throw new IOException("Choose at most 100 config files.");
        int total = 0;
        List<WorldListConfig> configs = new ArrayList<>();
        for (ConfigFile file : selected) {
            String scope;
            if (file.root().equals(global)) scope = "GLOBAL";
            else if (file.root().equals(worldMods)) scope = "WORLD";
            else throw new IOException("Only mod config folders can be shared.");
            String text = files.read(file).text();
            total += text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (total > WorldListConfig.MAX_TOTAL_BYTES) throw new IOException("Selected configs exceed 2 MiB.");
            configs.add(new WorldListConfig(scope, file.root().relativize(file.path()).toString().replace('\\', '/'), text));
        }
        return WorldListConfig.validate(configs);
    }
}
