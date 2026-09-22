package net.modtale.model.worldlist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Config paths are relative to a plugin data folder inside global or per-world mods. */
public record WorldListConfig(String scope, String path, String content) {
    public static final int MAX_FILES = 100;
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("json", "toml", "yaml", "yml", "properties", "cfg", "conf", "ini");

    public static List<WorldListConfig> validate(List<WorldListConfig> configs) throws IOException {
        if (configs == null) return List.of();
        if (configs.size() > MAX_FILES) throw new IOException("A mod list can hold at most 100 config files.");
        int total = 0;
        Set<String> paths = new HashSet<>();
        for (WorldListConfig config : configs) {
            if (config == null || !("GLOBAL".equals(config.scope) || "WORLD".equals(config.scope))
                    || config.path == null || config.path.length() > 500 || config.content == null) {
                throw new IOException("Invalid mod list config.");
            }
            String[] parts = config.path.split("/", -1);
            if (parts.length < 2) throw new IOException("Configs must be inside a plugin folder.");
            for (String part : parts) {
                if (part.isBlank() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                        || part.chars().anyMatch(c -> c < 32 || c == 127 || "<>:\"|?*\\".indexOf(c) >= 0)
                        || part.toLowerCase(Locale.ROOT).split("\\.", 2)[0].matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) {
                    throw new IOException("Unsafe mod list config path.");
                }
            }
            String name = parts[parts.length - 1].toLowerCase(Locale.ROOT);
            int dot = name.lastIndexOf('.');
            if (name.equals("manifest.json") || dot < 0 || !EXTENSIONS.contains(name.substring(dot + 1))) {
                throw new IOException("Unsupported mod list config file type.");
            }
            if (!paths.add(config.scope + "/" + config.path.toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate or case-colliding mod list config paths.");
            }
            int size = config.content.getBytes(StandardCharsets.UTF_8).length;
            total += size;
            if (config.content.indexOf('\0') >= 0 || size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                throw new IOException("Configs must be text, at most 1 MiB each and 2 MiB in total.");
            }
        }
        return List.copyOf(configs);
    }

    public String archivePath() {
        return "GLOBAL".equals(scope) ? "configs/global/Mods/" + path : "configs/world/mods/" + path;
    }
}
