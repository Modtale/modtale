package net.modtale.launcher.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/** Matches the exact manifest Group + "_" + Name used by PendingLoadJavaPlugin.load(). */
final class HytaleConfigOwnership {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private final Map<String, Set<String>> idsByFolder = new HashMap<>();

    static HytaleConfigOwnership read(Path... roots) throws IOException {
        var ownership = new HytaleConfigOwnership();
        for (Path root : new LinkedHashSet<>(Arrays.asList(roots))) {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue;
            try (var paths = Files.list(root)) {
                var candidates = paths.limit(20_001).toList();
                if (candidates.size() > 20_000) throw new IOException("Too many mods to identify configs.");
                for (Path path : candidates) ownership.readManifest(path);
            }
        }
        return ownership;
    }

    private void readManifest(Path path) {
        if (Files.isSymbolicLink(path)) return;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                Path manifest = path.resolve("manifest.json");
                if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) return;
                try (var input = Files.newInputStream(manifest)) { add(read(input), null, 0); }
            } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && (name.endsWith(".jar") || name.endsWith(".zip"))) {
                try (var zip = new ZipFile(path.toFile())) {
                    var entry = zip.getEntry("manifest.json");
                    if (entry != null) try (var input = zip.getInputStream(entry)) { add(read(input), null, 0); }
                }
            }
        } catch (IOException ignored) {
            // An unreadable manifest provides no evidence of config ownership.
        }
    }

    private JsonNode read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
        if (bytes.length > MAX_MANIFEST_BYTES) throw new IOException("Manifest is too large.");
        return JSON.readTree(bytes);
    }

    private void add(JsonNode manifest, String inheritedGroup, int depth) {
        if (manifest == null || !manifest.isObject() || depth > 16) return;
        String group = manifest.path("Group").asText(inheritedGroup);
        String name = manifest.path("Name").asText("");
        if (component(group) && component(name)) {
            idsByFolder.computeIfAbsent(group + "_" + name, ignored -> new TreeSet<>()).add(group + ":" + name);
        }
        for (JsonNode child : manifest.path("SubPlugins")) add(child, group, depth + 1);
    }

    private static boolean component(String value) {
        return value != null && !value.isBlank() && value.chars().noneMatch(c -> c < 32 || c == ':' || c == '/' || c == '\\');
    }

    Set<String> owners(String folder) { return idsByFolder.getOrDefault(folder, Set.of()); }
}
