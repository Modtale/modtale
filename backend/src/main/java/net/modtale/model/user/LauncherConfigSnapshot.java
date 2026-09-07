package net.modtale.model.user;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import net.modtale.model.worldlist.WorldListConfig;

/** Paths are relative to this device's Hytale UserData, never to the sender's filesystem. */
public record LauncherConfigSnapshot(String path, String content) implements Serializable {
    public static List<LauncherConfigSnapshot> validate(List<LauncherConfigSnapshot> configs) throws IOException {
        if (configs == null) return List.of();
        for (LauncherConfigSnapshot config : configs) {
            if (config == null || config.path == null) throw new IOException("Invalid launcher config snapshot.");
            String[] parts = config.path.split("/", -1);
            boolean mod = parts.length >= 3 && parts[0].equals("Mods");
            boolean world = parts.length >= 3 && parts[0].equals("Saves");
            boolean worldMod = world && parts.length >= 5 && parts[2].equals("mods");
            boolean worldConfig = world && parts.length == 3 && parts[2].equals("config.json");
            boolean universeConfig = world && parts.length == 6 && parts[2].equals("universe")
                    && parts[3].equals("worlds") && parts[5].equals("config.json");
            if (!(mod || worldMod || worldConfig || universeConfig)) throw new IOException("Unsupported launcher config location.");
        }
        WorldListConfig.validate(configs.stream().map(config -> new WorldListConfig("GLOBAL", config.path, config.content)).toList());
        return List.copyOf(configs);
    }
}
