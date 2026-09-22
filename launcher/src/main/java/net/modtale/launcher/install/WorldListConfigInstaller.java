package net.modtale.launcher.install;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.modtale.launcher.model.worldlist.WorldListConfig;

public final class WorldListConfigInstaller {
    private WorldListConfigInstaller() {}

    public static List<Path> install(List<WorldListConfig> configs, String scope, Path modsRoot) throws IOException {
        return install(configs, scope, modsRoot, WorldListConfig.MAX_TOTAL_BYTES);
    }

    public static List<Path> install(List<WorldListConfig> configs, String scope, Path modsRoot, int maxTotalBytes) throws IOException {
        return install(configs, scope, modsRoot, maxTotalBytes, conflicts -> false);
    }

    /** Requests one decision per mod; only explicitly approved conflicting files are replaced. */
    public static List<Path> install(List<WorldListConfig> configs, String scope, Path modsRoot, int maxTotalBytes,
            Predicate<List<WorldListConfig>> replaceExisting) throws IOException {
        List<WorldListConfig> selected = WorldListConfig.validate(configs, maxTotalBytes).stream()
                .filter(config -> config.scope().equals(scope)).toList();
        if (selected.isEmpty()) return List.of();
        if (Files.isSymbolicLink(modsRoot)) throw new IOException("The mods folder is a symbolic link.");
        Files.createDirectories(modsRoot);
        Path root = modsRoot.toRealPath();
        // Check every destination before writing any config.
        for (WorldListConfig config : selected) checked(root, config.path());
        var conflictsByMod = new LinkedHashMap<List<String>, List<WorldListConfig>>();
        for (WorldListConfig config : selected) {
            if (!Files.isRegularFile(checked(root, config.path()), LinkOption.NOFOLLOW_LINKS)) continue;
            List<String> key = config.modIds().isEmpty()
                    ? List.of("folder", config.path().split("/", 2)[0])
                    : java.util.stream.Stream.concat(java.util.stream.Stream.of("mod"),
                            config.modIds().stream().distinct().sorted()).toList();
            conflictsByMod.computeIfAbsent(key, ignored -> new ArrayList<>()).add(config);
        }
        Set<String> replacements = new HashSet<>();
        for (var conflicts : conflictsByMod.values()) {
            if (replaceExisting.test(List.copyOf(conflicts))) {
                conflicts.forEach(config -> replacements.add(config.path()));
            }
        }
        List<Path> installed = new ArrayList<>();
        for (WorldListConfig config : selected) {
            Path target = checked(root, config.path());
            boolean replace = replacements.contains(config.path());
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && !replace) continue;
            Files.createDirectories(target.getParent());
            checked(root, config.path());
            Files.copy(new ByteArrayInputStream(config.content().getBytes(StandardCharsets.UTF_8)), target,
                    replace ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new StandardCopyOption[0]);
            installed.add(target);
        }
        return List.copyOf(installed);
    }

    private static Path checked(Path root, String relative) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IOException("Config escapes its mods folder.");
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IOException("Config destination contains a symbolic link.");
        }
        if (Files.exists(target) && !Files.isRegularFile(target)) throw new IOException("Config destination is not a file.");
        return target;
    }
}
