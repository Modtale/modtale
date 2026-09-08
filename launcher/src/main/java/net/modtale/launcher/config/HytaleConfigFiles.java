package net.modtale.launcher.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Bounded discovery and conflict-aware writes for existing Hytale config files. */
public final class HytaleConfigFiles {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("json", "toml", "yaml", "yml", "properties", "cfg", "conf", "ini");
    private static final Set<String> RUNTIME_FOLDERS = Set.of("data", "cache", "logs", "backups", "players", "playerdata");

    static boolean runtimeModData(Path relative) {
        // The first component is the plugin's folder, which may itself be named Data.
        for (int i = 1; i < relative.getNameCount() - 1; i++) {
            if (RUNTIME_FOLDERS.contains(relative.getName(i).toString().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    static boolean runtimeSnapshot(String path) {
        Path relative = Path.of(path);
        if (path.startsWith("Mods/") && relative.getNameCount() > 2)
            return runtimeModData(relative.subpath(1, relative.getNameCount()));
        if (relative.getNameCount() > 4 && relative.getName(0).toString().equals("Saves")
                && relative.getName(2).toString().equals("mods"))
            return runtimeModData(relative.subpath(3, relative.getNameCount()));
        return false;
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public List<ConfigFile> discover(Path globalMods, Path world) throws IOException {
        List<ConfigFile> files = new ArrayList<>(discoverMods(globalMods));
        files.addAll(attribute(discoverWorldFiles(world), HytaleConfigOwnership.read(globalMods, world.resolve("mods"))));
        return List.copyOf(files);
    }

    public List<ConfigFile> discoverMods(Path mods) throws IOException {
        List<ConfigFile> files = new ArrayList<>();
        scan(mods, "Global mods", files);
        return attribute(files, HytaleConfigOwnership.read(mods));
    }

    public List<ConfigFile> discoverWorld(Path world) throws IOException {
        return attribute(discoverWorldFiles(world), HytaleConfigOwnership.read(world.resolve("mods")));
    }

    private List<ConfigFile> discoverWorldFiles(Path world) throws IOException {
        List<ConfigFile> files = new ArrayList<>();
        scan(world.resolve("mods"), "World mods", files);
        add(world, world.resolve("config.json"), "World", files);
        Path worlds = world.resolve("universe/worlds");
        if (Files.isDirectory(worlds, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(worlds)) {
            try (Stream<Path> children = Files.list(worlds)) {
                for (Path child : children.limit(1000).toList()) {
                    add(world, child.resolve("config.json"), "World", files);
                }
            }
        }
        files.sort(Comparator.comparing(ConfigFile::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(files);
    }

    private List<ConfigFile> attribute(List<ConfigFile> files, HytaleConfigOwnership ownership) {
        return files.stream().map(file -> {
            if (!(file.label().startsWith("Global mods / ") || file.label().startsWith("World mods / "))) return file;
            Path relative = file.root().relativize(file.path());
            Set<String> owners = relative.getNameCount() < 2 ? Set.of() : ownership.owners(relative.getName(0).toString());
            String id = owners.size() == 1 ? owners.iterator().next() : "";
            String owner = id.isEmpty() ? (owners.isEmpty() ? "Unattributed" : "Ambiguous mod") : id;
            String scope = file.label().startsWith("Global") ? "Global mods" : "World mods";
            return new ConfigFile(file.root(), file.path(), scope + " / " + owner + " / "
                    + relative.toString().replace('\\', '/'), id);
        }).toList();
    }

    private void scan(Path root, String scope, List<ConfigFile> files) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(root, 8)) {
            List<Path> candidates = paths.limit(20_001).toList();
            if (candidates.size() > 20_000) throw new IOException("Too many files to scan for configs.");
            for (Path path : candidates) {
                // Loose asset packs contain game JSON, not editable plugin settings.
                Path relative = root.relativize(path);
                if (relative.getNameCount() > 1 && Files.isRegularFile(root.resolve(relative.getName(0)).resolve("manifest.json"), LinkOption.NOFOLLOW_LINKS)) continue;
                if (runtimeModData(relative)) continue;
                add(root, path, scope, files);
                if (files.size() > 2000) throw new IOException("Too many config files to display.");
            }
        }
    }

    private void add(Path root, Path path, String scope, List<ConfigFile> files) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || !EXTENSIONS.contains(name.substring(dot + 1)) || name.equals("manifest.json")
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) return;
        ConfigFile file = new ConfigFile(root.toAbsolutePath().normalize(), path.toAbsolutePath().normalize(),
                scope + " / " + root.relativize(path).toString().replace('\\', '/'));
        try {
            checked(file);
            files.add(file);
        } catch (IOException ignored) {
            // Linked or inaccessible files are never offered for editing.
        }
    }

    public Snapshot read(ConfigFile file) throws IOException {
        Path path = checked(file);
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Config exceeds the 1 MiB editor limit.");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if (text.indexOf('\0') >= 0) throw new IOException("This file contains binary data.");
        return new Snapshot(file, text);
    }

    public Snapshot save(Snapshot original, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Config exceeds the 1 MiB editor limit.");
        if (text.indexOf('\0') >= 0) throw new IOException("Config contains binary data.");
        if (original.file().path().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            String json = text.startsWith("\uFEFF") ? text.substring(1) : text;
            if (json.isBlank() || JSON.readTree(json) == null) throw new IOException("Config must contain valid JSON.");
        }
        if (!read(original.file()).text().equals(original.text())) {
            throw new IOException("This file changed outside the editor. Reload it before saving.");
        }
        Path path = checked(original.file());
        Path backup = Files.createTempFile(path.getParent(), path.getFileName() + ".modtale-", ".bak");
        Files.writeString(backup, original.text(), StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(path.getParent(), ".modtale-config-", ".tmp");
        try {
            Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Files.write(temporary, bytes);
            checked(original.file());
            if (!read(original.file()).text().equals(original.text())) {
                throw new IOException("This file changed outside the editor. Reload it before saving.");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Snapshot(original.file(), text);
    }

    private Path checked(ConfigFile file) throws IOException {
        Path root = file.root().toAbsolutePath().normalize();
        Path path = file.path().toAbsolutePath().normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(root)) throw new IOException("Unsafe config path.");
        Path current = root;
        for (Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IOException("Linked config files cannot be edited.");
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Config file no longer exists.");
        return path;
    }

    public record ConfigFile(Path root, Path path, String label, String pluginId) {
        public ConfigFile(Path root, Path path, String label) { this(root, path, label, ""); }
        @Override public String toString() { return label; }
    }
    public record Snapshot(ConfigFile file, String text) {}
}
