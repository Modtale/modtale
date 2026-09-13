package net.modtale.launcher.install;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ArchiveInstaller {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LOCKFILE = "modtale.lock.json";
    private static final String LEGACY_MANIFEST = "modpack.json";

    public List<Path> installDownloadedFile(Path downloadedFile, String filename, Path modsDirectory, boolean unpackArchive)
            throws IOException {
        Files.createDirectories(modsDirectory);
        if (unpackArchive) {
            return extractInstallableEntries(downloadedFile, modsDirectory);
        }
        Path destination = uniqueDestination(modsDirectory, safeFilename(filename));
        Files.copy(downloadedFile, destination, StandardCopyOption.REPLACE_EXISTING);
        return List.of(destination);
    }

    public List<Path> installModpackArchive(Path archive, Path modsDirectory) throws IOException {
        Path instanceDirectory = modsDirectory.toAbsolutePath().normalize().getParent();
        return installModpackArchive(archive, modsDirectory, instanceDirectory);
    }

    public List<Path> installModpackArchive(
            Path archive,
            Path modsDirectory,
            Path instanceDirectory
    ) throws IOException {
        return installModpackArchive(archive, modsDirectory, instanceDirectory, null);
    }

    public List<Path> installModpackArchive(Path archive, Path modsDirectory, Path instanceDirectory, Set<String> selectedOwners) throws IOException {
        return installModpackArchive(archive, modsDirectory, instanceDirectory, selectedOwners, false);
    }

    public List<Path> installModpackConfigs(Path archive, Path modsDirectory, Path instanceDirectory, Set<String> installedOwners) throws IOException {
        return installModpackArchive(archive, modsDirectory, instanceDirectory, installedOwners, true);
    }

    public List<net.modtale.launcher.model.worldlist.WorldListConfig> readUniverseConfigs(Path archive, Set<String> installedOwners) throws IOException {
        return readUniverseConfigs(archive, installedOwners, java.util.Map.of());
    }

    public List<net.modtale.launcher.model.worldlist.WorldListConfig> readUniverseConfigs(Path archive, Set<String> installedOwners, java.util.Map<String, List<String>> externalModIds) throws IOException {
        Path staging = Files.createTempDirectory("modtale-universe-defaults-");
        try {
            extractArchive(archive, staging);
            Path root = modpackContentRoot(staging);
            Path lockPath = root.resolve(LOCKFILE);
            if (!Files.isRegularFile(lockPath)) return List.of();
            JsonNode lock = OBJECT_MAPPER.readTree(lockPath.toFile());
            if (lock.path("lockVersion").asInt() != 2) return List.of();
            List<net.modtale.launcher.model.worldlist.WorldListConfig> configs = new ArrayList<>();
            Set<String> paths = new HashSet<>();
            Set<String> declared = new HashSet<>();
            java.util.Map<String, List<String>> ownerModIds = new java.util.HashMap<>(externalModIds);
            for (JsonNode entry : lock.path("entries")) {
                String ownerKey = entry.path("source").asText() + ":" + entry.path("id").asText();
                declared.add(ownerKey);
                if (installedOwners.contains(ownerKey) && entry.hasNonNull("path")) {
                    Path binary = resolveLockedSource(root, entry.path("path").asText(), new HashSet<>());
                    verifyIntegrity(binary, entry);
                    ownerModIds.put(ownerKey, readModIds(List.of(binary)));
                }
            }
            for (JsonNode entry : lock.path("overrides")) {
                String path = entry.path("path").asText();
                String prefix = "overrides/Universe/mods/";
                if (!path.startsWith(prefix)) continue;
                JsonNode owner = entry.path("owner");
                String key = owner.path("source").asText() + ":" + owner.path("projectId").asText();
                if (!declared.contains(key) || !"SEED_ONLY".equals(entry.path("installPolicy").asText())
                        || !path.equals("overrides/" + entry.path("destination").asText())) throw new IOException("Invalid world config ownership.");
                if (!installedOwners.contains(key)) continue;
                Path file = resolveLockedSource(root, path, paths);
                verifyIntegrity(file, entry);
                if (Files.size(file) > 1024 * 1024) throw new IOException("Universe config exceeds 1 MiB.");
                configs.add(new net.modtale.launcher.model.worldlist.WorldListConfig("WORLD", path.substring(prefix.length()), Files.readString(file), ownerModIds.getOrDefault(key, List.of())));
            }
            return net.modtale.launcher.model.worldlist.WorldListConfig.validate(configs, 32 * 1024 * 1024);
        } finally { deleteRecursively(staging); }
    }

    static List<String> readModIds(List<Path> files) throws IOException {
        List<String> ids = new ArrayList<>();
        for (Path file : files) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
                ZipEntry manifest = zip.getEntry("manifest.json");
                if (manifest == null) continue;
                try (InputStream input = zip.getInputStream(manifest)) {
                    JsonNode json = OBJECT_MAPPER.readTree(input);
                    String group = json.path("Group").asText();
                    String name = json.path("Name").asText();
                    if (!group.isBlank() && !name.isBlank()) ids.add(group + ":" + name);
                }
            } catch (java.util.zip.ZipException ignored) {
                // Files without a readable manifest retain whole-package config application.
            }
        }
        return List.copyOf(ids);
    }

    private List<Path> installModpackArchive(Path archive, Path modsDirectory, Path instanceDirectory, Set<String> selectedOwners, boolean configsOnly) throws IOException {
        Files.createDirectories(modsDirectory);
        Files.createDirectories(instanceDirectory);
        Path stagingDirectory = Files.createTempDirectory("modtale-modpack-");
        try {
            Path extractedDirectory = stagingDirectory.resolve("extracted");
            Files.createDirectories(extractedDirectory);
            extractArchive(archive, extractedDirectory);

            Path contentRoot = modpackContentRoot(extractedDirectory);
            Path lockfile = contentRoot.resolve(LOCKFILE);
            if (Files.isRegularFile(lockfile)) {
                return installLockedArchive(contentRoot, lockfile, modsDirectory, instanceDirectory, selectedOwners, configsOnly);
            }
            if (configsOnly) return List.of();
            validateLegacyHytaleArchiveIfPresent(contentRoot.resolve(LEGACY_MANIFEST));
            List<Path> installed = new ArrayList<>();
            try (Stream<Path> files = Files.walk(contentRoot)) {
                for (Path source : files
                        .filter(Files::isRegularFile)
                        .filter(path -> isInstallable(path.getFileName().toString()))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList()) {
                    Path destination = uniqueDestination(modsDirectory, safeFilename(source.getFileName().toString()));
                    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    installed.add(destination);
                }
            }
            return installed;
        } finally {
            deleteRecursively(stagingDirectory);
        }
    }

    private static List<Path> installLockedArchive(
            Path contentRoot,
            Path lockfile,
            Path modsDirectory,
            Path instanceDirectory,
            Set<String> selectedOwners,
            boolean configsOnly
    ) throws IOException {
        JsonNode lock = OBJECT_MAPPER.readTree(lockfile.toFile());
        if (!"modtale-lock".equals(lock.path("format").asText()) || !Set.of(1, 2).contains(lock.path("lockVersion").asInt(-1))
                || !"hytale".equalsIgnoreCase(lock.path("game").asText())) {
            throw new IOException("Unsupported Modtale modpack lockfile format or version.");
        }

        boolean ownedConfigs = lock.path("lockVersion").asInt() == 2;
        Set<String> declaredOwners = new HashSet<>();
        Set<String> installedOwners = new HashSet<>();
        if (configsOnly && selectedOwners != null) installedOwners.addAll(selectedOwners);
        for (JsonNode entry : lock.path("entries")) declaredOwners.add(entry.path("source").asText() + ":" + entry.path("id").asText());
        Set<String> archivePaths = new HashSet<>();
        Set<String> destinationPaths = new HashSet<>();
        List<PendingInstall> pending = new ArrayList<>();
        for (JsonNode entry : lock.path("entries")) {
            if (configsOnly) continue;
            String ownerKey = entry.path("source").asText() + ":" + entry.path("id").asText();
            if (ownedConfigs && selectedOwners != null && "OPTIONAL".equals(entry.path("dependencyType").asText()) && !selectedOwners.contains(ownerKey)) continue;
            if (!"BUNDLED".equalsIgnoreCase(entry.path("distribution").asText())) {
                continue;
            }
            Path source = resolveLockedSource(contentRoot, requiredText(entry, "path"), archivePaths);
            verifyIntegrity(source, entry);
            Path destination = reservedUniqueDestination(
                    modsDirectory,
                    safeFilename(source.getFileName().toString()),
                    destinationPaths
            );
            pending.add(new PendingInstall(source, destination, false));
            installedOwners.add(ownerKey);
        }

        List<JsonNode> overrides = new ArrayList<>();
        lock.path("overrides").forEach(overrides::add);
        overrides.sort(Comparator.comparing(entry -> entry.path("path").asText()));
        for (JsonNode entry : overrides) {
            String archivePath = requiredText(entry, "path");
            if (!ownedConfigs || !archivePath.startsWith("overrides/Universe/mods/") || entry.path("owner").isNull() || !entry.has("owner")) {
                throw new IOException("Only mod-associated world configs are supported. Saves and shared overrides cannot be installed.");
            }
            if (ownedConfigs) {
                if (!"SEED_ONLY".equals(entry.path("installPolicy").asText()) || !entry.has("owner")
                        || !archivePath.equals("overrides/" + entry.path("destination").asText())) throw new IOException("Invalid config ownership or installation policy.");
                JsonNode owner = entry.path("owner");
                if (!owner.isNull()) {
                    String key = owner.path("source").asText() + ":" + owner.path("projectId").asText();
                    if (!declaredOwners.contains(key)) throw new IOException("Unknown config owner.");
                    if (!installedOwners.contains(key)) continue;
                } else if (configsOnly) continue;
            } else if (configsOnly) continue;
            Path source = resolveLockedSource(contentRoot, archivePath, archivePaths);
            verifyIntegrity(source, entry);
            // Validated defaults are retained separately and applied when enabling their mod.

        }

        List<Path> installed = new ArrayList<>();
        for (PendingInstall install : pending) {
            Files.createDirectories(install.destination().getParent());
            if (install.seedOnly()) {
                Files.copy(install.source(), install.destination());
            } else {
                Files.copy(install.source(), install.destination(), StandardCopyOption.REPLACE_EXISTING);
            }
            installed.add(install.destination());
        }
        return installed;
    }

    private static void validateLegacyHytaleArchiveIfPresent(Path manifestFile) throws IOException {
        if (!Files.isRegularFile(manifestFile)) {
            // Ordinary dependency bundles predate modpack manifests and contain only installable Hytale files.
            return;
        }
        JsonNode manifest = OBJECT_MAPPER.readTree(manifestFile.toFile());
        boolean originalFormat = manifest.isObject() && !manifest.has("formatVersion") && !manifest.has("game")
                && !manifest.path("name").asText("").isBlank() && manifest.path("files").isArray();
        if (originalFormat) {
            for (JsonNode file : manifest.path("files")) {
                if (!file.isObject() || file.path("id").asText("").isBlank()
                        || file.path("version").asText("").isBlank()) {
                    throw new IOException("Invalid original Modtale modpack entry.");
                }
            }
            return;
        }
        if (!manifest.isObject() || manifest.path("formatVersion").asInt(-1) != 1
                || !"hytale".equalsIgnoreCase(manifest.path("game").asText())
                || !manifest.path("files").isArray()) {
            throw new IOException("Unsupported legacy Modtale modpack format or game.");
        }
    }

    private static String requiredText(JsonNode entry, String field) throws IOException {
        String value = entry.path(field).asText("");
        if (value.isBlank()) {
            throw new IOException("Modpack lockfile entry is missing " + field + ".");
        }
        return value;
    }

    private static Path resolveLockedSource(Path contentRoot, String entryName, Set<String> archivePaths) throws IOException {
        if (entryName.contains("\\") || entryName.matches("^[A-Za-z]:.*") || Path.of(entryName).isAbsolute()) {
            throw new IOException("Unsafe modpack path: " + entryName);
        }
        for (String segment : entryName.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Unsafe modpack path: " + entryName);
            }
        }
        String folded = entryName.toLowerCase(Locale.ROOT);
        if (!archivePaths.add(folded)) {
            throw new IOException("Duplicate or case-colliding modpack path: " + entryName);
        }
        Path normalizedRoot = contentRoot.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve(entryName).normalize();
        if (!source.startsWith(normalizedRoot) || !Files.isRegularFile(source)) {
            throw new IOException("Modpack file is missing or outside the archive root: " + entryName);
        }
        return source;
    }

    private static void verifyIntegrity(Path source, JsonNode entry) throws IOException {
        long expectedSize = entry.path("size").asLong(-1);
        if (expectedSize < 0 || Files.size(source) != expectedSize) {
            throw new IOException("Modpack file size mismatch: " + source.getFileName());
        }
        String expectedHash = entry.path("hashes").path("sha256").asText("");
        if (expectedHash.isBlank() || !expectedHash.equalsIgnoreCase(sha256(source))) {
            throw new IOException("Modpack file checksum mismatch: " + source.getFileName());
        }
    }

    private static String sha256(Path source) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(source)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable.", ex);
        }
    }

    public List<Path> extractInstallableEntries(Path archive, Path modsDirectory) throws IOException {
        Files.createDirectories(modsDirectory);
        List<Path> installed = new ArrayList<>();
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !isInstallable(entry.getName())) {
                    continue;
                }
                Path destination = resolveSafeDestination(modsDirectory, entry.getName());
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                installed.add(destination);
            }
        }
        return installed;
    }

    private static void extractArchive(Path archive, Path extractionRoot) throws IOException {
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = resolveExtractionDestination(extractionRoot, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path modpackContentRoot(Path extractedDirectory) throws IOException {
        try (Stream<Path> children = Files.list(extractedDirectory)) {
            List<Path> entries = children
                    .filter(path -> !isArchiveMetadataDirectory(path))
                    .toList();
            if (entries.size() == 1 && Files.isDirectory(entries.getFirst())) {
                return entries.getFirst();
            }
        }
        return extractedDirectory;
    }

    static boolean isInstallable(String entryName) {
        String filename = Path.of(entryName).getFileName().toString();
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip") || lower.endsWith(".hmasset") || lower.endsWith(".hymod");
    }

    private static Path resolveSafeDestination(Path modsDirectory, String entryName) throws IOException {
        String filename = safeFilename(Path.of(entryName).getFileName().toString());
        Path destination = uniqueDestination(modsDirectory, filename);
        Path normalizedTarget = destination.getParent().toRealPath().resolve(destination.getFileName()).normalize();
        Path normalizedRoot = modsDirectory.toRealPath().normalize();
        if (!normalizedTarget.toAbsolutePath().normalize().startsWith(normalizedRoot.toAbsolutePath())) {
            throw new IOException("Archive entry escapes the target mods directory: " + entryName);
        }
        return destination;
    }

    private static Path resolveExtractionDestination(Path extractionRoot, String entryName) throws IOException {
        Path destination = extractionRoot.resolve(entryName).normalize();
        Path normalizedRoot = extractionRoot.toAbsolutePath().normalize();
        if (!destination.toAbsolutePath().normalize().startsWith(normalizedRoot)) {
            throw new IOException("Archive entry escapes the extraction directory: " + entryName);
        }
        return destination;
    }

    private static Path uniqueDestination(Path modsDirectory, String filename) {
        Path candidate = modsDirectory.resolve(filename);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int extensionStart = filename.lastIndexOf('.');
        String base = extensionStart > 0 ? filename.substring(0, extensionStart) : filename;
        String extension = extensionStart > 0 ? filename.substring(extensionStart) : "";
        int counter = 2;
        while (true) {
            Path next = modsDirectory.resolve(base + "-" + counter + extension);
            if (!Files.exists(next)) {
                return next;
            }
            counter++;
        }
    }

    private static Path reservedUniqueDestination(Path directory, String filename, Set<String> reserved) {
        int extensionStart = filename.lastIndexOf('.');
        String base = extensionStart > 0 ? filename.substring(0, extensionStart) : filename;
        String extension = extensionStart > 0 ? filename.substring(extensionStart) : "";
        int counter = 1;
        while (true) {
            String candidateName = counter == 1 ? filename : base + "-" + counter + extension;
            Path candidate = directory.resolve(candidateName);
            String folded = candidate.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (!Files.exists(candidate) && reserved.add(folded)) {
                return candidate;
            }
            counter++;
        }
    }

    private static boolean isArchiveMetadataDirectory(Path path) {
        return Files.isDirectory(path) && "__MACOSX".equals(path.getFileName().toString());
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> cleanup = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path path : cleanup) {
                Files.deleteIfExists(path);
            }
        }
    }

    static String safeFilename(String filename) {
        String base = filename == null || filename.isBlank() ? "modtale-download.jar" : Path.of(filename).getFileName().toString();
        String sanitized = base.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("-+\\.", ".")
                .replaceAll("(^-|-$)", "");
        return sanitized.isBlank() ? "modtale-download.jar" : sanitized;
    }

    private record PendingInstall(Path source, Path destination, boolean seedOnly) {
    }
}
