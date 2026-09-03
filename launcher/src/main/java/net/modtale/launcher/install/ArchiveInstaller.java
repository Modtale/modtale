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
                return installLockedArchive(contentRoot, lockfile, modsDirectory, instanceDirectory);
            }
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
            Path instanceDirectory
    ) throws IOException {
        JsonNode lock = OBJECT_MAPPER.readTree(lockfile.toFile());
        if (!"modtale-lock".equals(lock.path("format").asText()) || lock.path("lockVersion").asInt(-1) != 1
                || !"hytale".equalsIgnoreCase(lock.path("game").asText())) {
            throw new IOException("Unsupported Modtale modpack lockfile format or version.");
        }

        Set<String> archivePaths = new HashSet<>();
        Set<String> destinationPaths = new HashSet<>();
        List<PendingInstall> pending = new ArrayList<>();
        for (JsonNode entry : lock.path("entries")) {
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
            pending.add(new PendingInstall(source, destination));
        }

        List<JsonNode> overrides = new ArrayList<>();
        lock.path("overrides").forEach(overrides::add);
        overrides.sort(Comparator.comparing(entry -> entry.path("path").asText()));
        for (JsonNode entry : overrides) {
            String archivePath = requiredText(entry, "path");
            Path source = resolveLockedSource(contentRoot, archivePath, archivePaths);
            verifyIntegrity(source, entry);
            String prefix = "overrides/";
            if (!archivePath.startsWith(prefix) || archivePath.length() == prefix.length()) {
                throw new IOException("Override path must be inside overrides/: " + archivePath);
            }
            String hytalePath = archivePath.substring(prefix.length());
            if (!(hytalePath.startsWith("Mods/") || hytalePath.startsWith("Saves/"))) {
                throw new IOException("Override destination must be inside Hytale Mods/ or Saves/: " + archivePath);
            }
            Path destination = resolveInstanceDestination(instanceDirectory, hytalePath);
            pending.add(new PendingInstall(source, destination));
        }

        List<Path> installed = new ArrayList<>();
        for (PendingInstall install : pending) {
            Files.createDirectories(install.destination().getParent());
            Files.copy(install.source(), install.destination(), StandardCopyOption.REPLACE_EXISTING);
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

    private static Path resolveInstanceDestination(Path instanceDirectory, String relativePath) throws IOException {
        if (relativePath.contains("\\") || relativePath.matches("^[A-Za-z]:.*") || Path.of(relativePath).isAbsolute()) {
            throw new IOException("Unsafe override destination: " + relativePath);
        }
        Path normalizedRoot = instanceDirectory.toAbsolutePath().normalize();
        Path destination = normalizedRoot.resolve(relativePath).normalize();
        if (!destination.startsWith(normalizedRoot)) {
            throw new IOException("Override escapes the Hytale instance: " + relativePath);
        }
        return destination;
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

    private static boolean isInstallable(String entryName) {
        String filename = Path.of(entryName).getFileName().toString();
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip") || lower.endsWith(".hmasset") || lower.endsWith(".hymod");
    }

    private static Path resolveSafeDestination(Path modsDirectory, String entryName) throws IOException {
        String filename = safeFilename(Path.of(entryName).getFileName().toString());
        Path destination = uniqueDestination(modsDirectory, filename);
        Path normalizedTarget = destination.normalize();
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

    private record PendingInstall(Path source, Path destination) {
    }
}
