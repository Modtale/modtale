package net.modtale.launcher.hytale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.settings.HytalePathDetector;
import net.modtale.launcher.settings.LauncherSettings;

public final class HytaleWorldManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public List<HytaleWorld> loadWorlds(LauncherSettings settings) {
        Path savesDirectory = savesDirectory(settings);
        if (!Files.isDirectory(savesDirectory)) {
            return List.of();
        }
        try (var stream = Files.list(savesDirectory)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(this::worldFromDirectory)
                    .sorted(Comparator.comparing(HytaleWorld::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not read Hytale worlds from " + savesDirectory, ex);
        }
    }

    public HytaleWorldConfig loadConfig(Path configPath) {
        ObjectNode root = readConfigRoot(configPath);
        JsonNode mods = root.get("Mods");
        Map<String, Boolean> enabledByMod = new LinkedHashMap<>();
        if (mods != null && mods.isObject()) {
            mods.fields().forEachRemaining(entry -> {
                JsonNode enabled = entry.getValue().get("Enabled");
                enabledByMod.put(entry.getKey(), enabled != null && enabled.asBoolean(false));
            });
        }
        return new HytaleWorldConfig(configPath, enabledByMod);
    }

    public void setModEnabled(Path configPath, String modId, boolean enabled) {
        setModsEnabled(configPath, List.of(modId), enabled);
    }

    public void setModsEnabled(Path configPath, Collection<String> modIds, boolean enabled) {
        if (configPath == null || modIds == null || modIds.isEmpty()) {
            return;
        }
        ObjectNode root = readConfigRoot(configPath);
        ObjectNode mods = objectNode(root, "Mods");
        for (String modId : modIds) {
            if (modId == null || modId.isBlank()) {
                continue;
            }
            ObjectNode mod = objectNode(mods, modId.trim());
            mod.put("Enabled", enabled);
        }
        try {
            Files.createDirectories(configPath.getParent());
            MAPPER.writeValue(configPath.toFile(), root);
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not update Hytale world config " + configPath, ex);
        }
    }

    public List<HytaleInstalledMod> loadInstalledMods(LauncherSettings settings) {
        Path modsDirectory = modsDirectory(settings);
        if (!Files.isDirectory(modsDirectory)) {
            return List.of();
        }
        try (var stream = Files.list(modsDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(HytaleWorldManager::isJar)
                    .map(this::installedModFromJar)
                    .sorted(Comparator.comparing(HytaleInstalledMod::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not read Hytale mods from " + modsDirectory, ex);
        }
    }

    public Path savesDirectory(LauncherSettings settings) {
        Path configured = settings.hytaleUserDataDirectory().resolve("Saves");
        if (Files.isDirectory(configured)) {
            return configured;
        }
        return HytalePathDetector.detectExistingSavesDirectory().orElse(configured);
    }

    public Path modsDirectory(LauncherSettings settings) {
        Path paired = pairedModsDirectory(settings);
        if (paired != null && Files.isDirectory(paired)) {
            return paired;
        }
        Path configured = settings.hytaleModsDirectory();
        if (Files.isDirectory(configured)) {
            return configured;
        }
        return HytalePathDetector.detectExistingModsDirectory().orElse(configured);
    }

    private Path pairedModsDirectory(LauncherSettings settings) {
        Path savesDirectory = savesDirectory(settings);
        Path userDataDirectory = savesDirectory.getParent();
        return userDataDirectory == null ? null : userDataDirectory.resolve("Mods");
    }

    private HytaleWorld worldFromDirectory(Path directory) {
        Path configPath = directory.resolve("config.json");
        HytaleWorldConfig config = loadConfig(configPath);
        HytaleWorldMetadata metadata = readMetadata(directory);
        long enabledMods = config.enabledByMod().values().stream().filter(Boolean::booleanValue).count();
        return new HytaleWorld(
                directory.toAbsolutePath().normalize(),
                directory.getFileName() == null ? directory.toString() : directory.getFileName().toString(),
                configPath.toAbsolutePath().normalize(),
                metadata.patchline(),
                metadata.previewImage(),
                (int) enabledMods,
                config.enabledByMod().size(),
                lastModified(directory, configPath)
        );
    }

    private HytaleWorldMetadata readMetadata(Path directory) {
        Path metadata = directory.resolve("client_metadata.json");
        JsonNode root = null;
        if (!Files.isRegularFile(metadata)) {
            return new HytaleWorldMetadata("", previewImage(directory, null));
        }
        try {
            root = MAPPER.readTree(metadata.toFile());
            return new HytaleWorldMetadata(
                    root.path("CreatedWithPatchline").asText(""),
                    previewImage(directory, root)
            );
        } catch (IOException ignored) {
            return new HytaleWorldMetadata("", previewImage(directory, root));
        }
    }

    private String previewImage(Path directory, JsonNode metadata) {
        String fromMetadata = previewImageFromMetadata(directory, metadata);
        if (!fromMetadata.isBlank()) {
            return fromMetadata;
        }
        for (String filename : List.of(
                "preview.png",
                "preview.jpg",
                "preview.jpeg",
                "preview.webp",
                "thumbnail.png",
                "thumbnail.jpg",
                "screenshot.png",
                "screenshot.jpg"
        )) {
            Path candidate = directory.resolve(filename);
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize().toUri().toString();
            }
        }
        return "";
    }

    private String previewImageFromMetadata(Path directory, JsonNode metadata) {
        if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
            return "";
        }
        Deque<JsonNode> nodes = new ArrayDeque<>();
        nodes.add(metadata);
        while (!nodes.isEmpty()) {
            JsonNode node = nodes.removeFirst();
            if (node == null || node.isNull() || node.isMissingNode()) {
                continue;
            }
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    if (isPreviewField(field.getKey())) {
                        String resolved = resolveImageValue(directory, field.getValue().asText(""));
                        if (!resolved.isBlank()) {
                            return resolved;
                        }
                    }
                    nodes.addLast(field.getValue());
                }
            } else if (node.isArray()) {
                node.forEach(nodes::addLast);
            }
        }
        return "";
    }

    private boolean isPreviewField(String field) {
        if (field == null || field.isBlank()) {
            return false;
        }
        String normalized = field.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        return List.of(
                "preview",
                "previewimage",
                "previewimagepath",
                "previewpath",
                "thumbnail",
                "thumbnailimage",
                "thumbnailimagepath",
                "thumbnailpath",
                "screenshot",
                "screenshotimage",
                "screenshotpath",
                "image",
                "imagepath"
        ).contains(normalized);
    }

    private String resolveImageValue(Path directory, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("file:")
                || normalized.startsWith("data:")) {
            return normalized;
        }
        try {
            Path path = Path.of(normalized);
            if (!path.isAbsolute()) {
                path = directory.resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path.toUri().toString() : "";
        } catch (IllegalArgumentException ignored) {
            try {
                URI uri = URI.create(normalized);
                return uri.isAbsolute() ? normalized : "";
            } catch (IllegalArgumentException ignoredAgain) {
                return "";
            }
        }
    }

    private Instant lastModified(Path directory, Path configPath) {
        try {
            Instant directoryTime = Files.getLastModifiedTime(directory).toInstant();
            if (!Files.exists(configPath)) {
                return directoryTime;
            }
            Instant configTime = Files.getLastModifiedTime(configPath).toInstant();
            return configTime.isAfter(directoryTime) ? configTime : directoryTime;
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private HytaleInstalledMod installedModFromJar(Path jar) {
        String fallbackName = jar.getFileName() == null ? jar.toString() : jar.getFileName().toString();
        String baseName = fallbackName.replaceFirst("(?i)\\.jar$", "");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry manifest = zip.getEntry("manifest.json");
            if (manifest == null) {
                return new HytaleInstalledMod(baseName, baseName, "", "", jar.toAbsolutePath().normalize());
            }
            try (InputStream input = zip.getInputStream(manifest)) {
                JsonNode root = MAPPER.readTree(input);
                String group = root.path("Group").asText("");
                String name = root.path("Name").asText(baseName);
                String id = group.isBlank() || name.isBlank() ? baseName : group + ":" + name;
                return new HytaleInstalledMod(
                        id,
                        name.isBlank() ? baseName : name,
                        root.path("Version").asText(""),
                        root.path("Description").asText(""),
                        jar.toAbsolutePath().normalize()
                );
            }
        } catch (IOException ex) {
            return new HytaleInstalledMod(baseName, baseName, "", "", jar.toAbsolutePath().normalize());
        }
    }

    private ObjectNode readConfigRoot(Path configPath) {
        if (configPath == null || !Files.exists(configPath)) {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("Version", 4);
            root.set("Mods", MAPPER.createObjectNode());
            return root;
        }
        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            if (root instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not read Hytale world config " + configPath, ex);
        }
        ObjectNode replacement = MAPPER.createObjectNode();
        replacement.put("Version", 4);
        replacement.set("Mods", MAPPER.createObjectNode());
        return replacement;
    }

    private static ObjectNode objectNode(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode replacement = MAPPER.createObjectNode();
        parent.set(field, replacement);
        return replacement;
    }

    private static boolean isJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar");
    }

    public record HytaleWorld(
            Path directory,
            String name,
            Path configPath,
            String patchline,
            String previewImage,
            int enabledMods,
            int totalMods,
            Instant updatedAt
    ) {
        public HytaleWorld {
            previewImage = previewImage == null ? "" : previewImage.trim();
        }
    }

    private record HytaleWorldMetadata(String patchline, String previewImage) {
        private HytaleWorldMetadata {
            patchline = patchline == null ? "" : patchline.trim();
            previewImage = previewImage == null ? "" : previewImage.trim();
        }
    }

    public record HytaleWorldConfig(Path configPath, Map<String, Boolean> enabledByMod) {
        public HytaleWorldConfig {
            enabledByMod = enabledByMod == null ? Map.of() : Map.copyOf(enabledByMod);
        }
    }

    public record HytaleInstalledMod(
            String id,
            String name,
            String version,
            String description,
            Path file
    ) {
    }
}
