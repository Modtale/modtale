package net.modtale.launcher.hytale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.io.AtomicJsonFile;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectMeta;


/** Interoperates with Hytale's provider-backed installation registry. */
public final class HytaleModRegistry {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;
    private final Path registry;
    private final Path linksPath;

    public HytaleModRegistry(Path modsDirectory) {
        root = canonical(modsDirectory);
        registry = root.resolve(".installed.json");
        linksPath = root.resolve(".modtale-links.json");
    }

    public List<InstalledProject> importProjects(List<InstalledProject> projects) throws IOException {
        synchronized (HytaleModRegistry.class) { return importLocked(projects); }
    }

    private List<InstalledProject> importLocked(List<InstalledProject> projects) throws IOException {
        ObjectNode document = read();
        ObjectNode links = readLinks();
        ObjectNode originalLinks = links.deepCopy();
        List<InstalledProject> result = new ArrayList<>(projects);
        Set<String> tracked = new HashSet<>();
        links.properties().forEach(e -> tracked.add(e.getValue().path("projectId").asText()));
        result.removeIf(p -> tracked.contains(p.projectId()) && !p.files().isEmpty()
                && p.files().stream().noneMatch(f -> Files.exists(Path.of(f))));
        for (var entry : document.path("mods").properties()) {
            String id = entry.getKey();
            JsonNode value = entry.getValue();
            if (ModtaleApiClient.curseForgeId(id) == null || value.path("isWorld").asBoolean()) continue;
            Path file = installedFile(value);
            String fileId = value.path("fileId").asText("");
            if (file == null || !fileId.matches("curseforge:[1-9][0-9]*") || !matches(file, value)) continue;
            JsonNode link = links.path(id);
            InstalledProject previous = projects.stream().filter(p -> p.projectId().equals(link.path("projectId").asText())
                    || p.projectId().equals(id) || p.files().stream()
                    .anyMatch(f -> canonical(Path.of(f)).equals(file))).findFirst().orElse(null);
            if (previous != null && InstalledProject.SOURCE_MODTALE.equals(previous.source())) {
                InstalledProject retained = previous;
                if (!previous.files().stream().anyMatch(f -> canonical(Path.of(f)).equals(file))
                        || (!link.path("fileId").asText().isBlank() && !fileId.equals(link.path("fileId").asText()))) {
                    retained = new InstalledProject(previous.projectId(), previous.slug(), previous.title(), previous.classification(),
                            manifestVersion(file), "", value.path("gameVersions").path(0).asText(""), previous.installedAt(),
                            Instant.ofEpochSecond(Math.max(0, value.path("installedAt").asLong())), List.of(file.toString()),
                            previous.dependencyProjectIds(), previous.externalDependencies(), previous.source(), previous.installType(),
                            previous.modpackUnlocked(), previous.bundledProjects(), previous.universeConfigs());
                }
                result.removeIf(p -> p.projectId().equals(previous.projectId()) || p.projectId().equals(id));
                result.add(retained);
                remember(links, id, retained.projectId(), value);
                continue;
            }
            Instant installedAt = Instant.ofEpochSecond(Math.max(0, value.path("installedAt").asLong()));
            InstalledProject imported = new InstalledProject(id, id, value.path("name").asText(file.getFileName().toString()),
                    "MOD", value.path("displayVersion").asText(file.getFileName().toString()),
                    fileId.substring("curseforge:".length()), value.path("gameVersions").path(0).asText(""),
                    previous == null ? installedAt : previous.installedAt(), installedAt, List.of(file.toString()),
                    List.of(), List.of(), InstalledProject.SOURCE_CURSEFORGE, InstalledProject.INSTALL_DIRECT, false, List.of());
            remember(links, id, id, value);
            if (previous != null && previous.projectId().equals(id)
                    && previous.installedVersionId().equals(imported.installedVersionId())
                    && previous.files().stream().anyMatch(f -> canonical(Path.of(f)).equals(file))) continue;
            result.removeIf(p -> p.projectId().equals(id) || (InstalledProject.INSTALL_DIRECT.equals(p.installType())
                    && p.files().stream().anyMatch(f -> canonical(Path.of(f)).equals(file))));
            result.add(imported);
        }
        if (!links.equals(originalLinks)) AtomicJsonFile.write(linksPath, JSON.writerWithDefaultPrettyPrinter(), links);
        // Preserve list order when only metadata links changed.
        Map<String, InstalledProject> byId = new LinkedHashMap<>();
        result.forEach(p -> byId.put(p.projectId(), p));
        List<InstalledProject> ordered = new ArrayList<>();
        projects.forEach(p -> { if (byId.containsKey(p.projectId())) ordered.add(byId.remove(p.projectId())); });
        ordered.addAll(byId.values());
        return List.copyOf(ordered);
    }

    public Map<String, ProjectMeta> metadata() throws IOException {
        Map<String, ProjectMeta> result = new LinkedHashMap<>();
        for (var entry : read().path("mods").properties()) {
            JsonNode value = entry.getValue();
            if (installedFile(value) != null) result.put(entry.getKey(), new ProjectMeta(
                    value.path("name").asText(), "", value.path("logoUrl").asText(),
                    value.path("author").asText(), "MOD", 0, "", entry.getKey()));
        }
        return result;
    }

    public void exportProjects(List<InstalledProject> projects, ModtaleApiClient api) throws IOException {
        Map<Long, List<Path>> fingerprints = new LinkedHashMap<>();
        Map<Path, InstalledProject> owners = new LinkedHashMap<>();
        ObjectNode initial = read();
        for (InstalledProject project : projects) {
            if (InstalledProject.SOURCE_LOCAL.equals(project.source()) || project.isModpack()) continue;
            for (String recorded : project.files()) {
                Path file = canonical(Path.of(recorded));
                if (!root.equals(file.getParent()) || !Files.isRegularFile(file)
                        || !file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(jar|zip)")) continue;
                boolean registered = initial.path("mods").properties().stream().anyMatch(e -> {
                    try { return file.equals(installedFile(e.getValue())) && matches(file, e.getValue()); }
                    catch (IOException ignored) { return false; }
                });
                if (registered) continue;
                owners.put(file, project);
                fingerprints.computeIfAbsent(ArtifactFingerprint.calculate(file).curseForgeFingerprint(), ignored -> new ArrayList<>()).add(file);
            }
        }
        if (fingerprints.isEmpty()) return;
        JsonNode response = api.matchCurseForgeFiles(List.copyOf(fingerprints.keySet()));
        Map<String, ObjectNode> additions = new LinkedHashMap<>();
        for (JsonNode match : response.path("data").path("exactMatches")) {
            JsonNode remote = match.path("file");
            long projectId = remote.path("modId").asLong();
            long fileId = remote.path("id").asLong();
            if (projectId <= 0 || fileId <= 0 || remote.path("gameId").asLong() != 70216) continue;
            for (Path file : fingerprints.getOrDefault(remote.path("fileFingerprint").asLong(), List.of())) {
                ObjectNode hashes = JSON.createObjectNode();
                hashes.put("fileLength", remote.path("fileLength").asLong(-1));
                for (JsonNode hash : remote.path("hashes")) {
                    if (hash.path("algo").asInt() == 1) hashes.put("sha1", hash.path("value").asText());
                    if (hash.path("algo").asInt() == 2) hashes.put("md5", hash.path("value").asText());
                }
                // Fingerprints exclude whitespace; require a cryptographic whole-file match too.
                if (!matches(file, hashes)) continue;
                InstalledProject owner = owners.get(file);
                String id = "curseforge:" + projectId;
                ProjectMeta meta = api.getProjectMeta(id);
                ObjectNode value = hashes.deepCopy();
                value.put("fileId", "curseforge:" + fileId);
                value.put("fileName", file.getFileName().toString());
                value.put("relativePath", root.relativize(file).toString());
                value.put("isWorld", false);
                value.put("name", meta.title());
                value.put("author", meta.author());
                value.put("logoUrl", meta.icon());
                value.put("displayVersion", remote.path("displayName").asText(owner.installedVersion()));
                value.set("gameVersions", remote.path("gameVersions").deepCopy());
                value.put("installedAt", owner.installedAt().getEpochSecond());
                additions.put(id, value);
            }
        }
        // Re-read after network requests so other installations are preserved.
        synchronized (HytaleModRegistry.class) {
            ObjectNode document = read();
            ObjectNode mods = (ObjectNode) document.get("mods");
            ObjectNode links = readLinks();
            boolean changed = false;
            for (var addition : additions.entrySet()) {
                Path file = installedFile(addition.getValue());
                if (file == null || !matches(file, addition.getValue())) continue;
                JsonNode existing = mods.get(addition.getKey());
                if (existing != null && installedFile(existing) != null && matches(installedFile(existing), existing)) continue;
                mods.set(addition.getKey(), addition.getValue());
                remember(links, addition.getKey(), owners.get(file).projectId(), addition.getValue());
                changed = true;
            }
            if (changed) {
                AtomicJsonFile.write(linksPath, JSON.writerWithDefaultPrettyPrinter(), links);
                AtomicJsonFile.write(registry, JSON.writerWithDefaultPrettyPrinter(), document);
            }
        }
    }

    public void removeFiles(List<String> files) throws IOException {
        Set<Path> paths = new HashSet<>();
        files.forEach(f -> paths.add(canonical(Path.of(f))));
        synchronized (HytaleModRegistry.class) {
            if (!Files.exists(registry)) return;
            ObjectNode document = read();
            ObjectNode mods = (ObjectNode) document.get("mods");
            List<String> removed = new ArrayList<>();
            for (var entry : mods.properties()) {
                Path file = resolve(entry.getValue().path("relativePath").asText(""));
                if (file != null && paths.contains(file) && !Files.exists(file)) removed.add(entry.getKey());
            }
            if (!removed.isEmpty()) {
                mods.remove(removed);
                AtomicJsonFile.write(registry, JSON.writerWithDefaultPrettyPrinter(), document);
            }
        }
    }

    private ObjectNode readLinks() throws IOException {
        if (!Files.exists(linksPath)) return JSON.createObjectNode();
        JsonNode node = JSON.readTree(linksPath.toFile());
        if (!(node instanceof ObjectNode object)) throw new IOException("Invalid Modtale provider links");
        return object;
    }

    private static void remember(ObjectNode links, String nativeId, String projectId, JsonNode entry) {
        ObjectNode link = links.putObject(nativeId);
        link.put("projectId", projectId);
        link.put("fileId", entry.path("fileId").asText());
    }

    private static String manifestVersion(Path file) throws IOException {
        try (var zip = new java.util.zip.ZipFile(file.toFile())) {
            var entry = zip.getEntry("manifest.json");
            if (entry == null) return "";
            try (var stream = zip.getInputStream(entry)) { return JSON.readTree(stream).path("Version").asText(""); }
        }
    }

    private ObjectNode read() throws IOException {
        if (!Files.exists(registry)) {
            ObjectNode document = JSON.createObjectNode().put("version", 1);
            document.putObject("mods");
            return document;
        }
        JsonNode value = JSON.readTree(registry.toFile());
        if (!(value instanceof ObjectNode document) || document.path("version").asInt() != 1
                || !document.path("mods").isObject()) throw new IOException("Unsupported Hytale mod registry: " + registry);
        return document;
    }

    private Path installedFile(JsonNode value) {
        Path file = resolve(value.path("relativePath").asText(""));
        return file != null && Files.isRegularFile(file) ? file : null;
    }

    private Path resolve(String relative) {
        if (relative.isBlank()) return null;
        try {
            Path path = Path.of(relative);
            if (path.isAbsolute()) return null;
            Path file = canonical(root.resolve(path));
            return root.equals(file.getParent()) ? file : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private static boolean matches(Path file, JsonNode value) throws IOException {
        if (!Files.isRegularFile(file) || value.path("fileLength").asLong(-1) != Files.size(file)) return false;
        String sha1 = value.path("sha1").asText("");
        String md5 = value.path("md5").asText("");
        String expected = sha1.isBlank() ? md5 : sha1;
        if (expected.isBlank()) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance(sha1.isBlank() ? "MD5" : "SHA-1");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Path canonical(Path path) {
        try { return path.toRealPath(); }
        catch (IOException ignored) { return path.toAbsolutePath().normalize(); }
    }
}
