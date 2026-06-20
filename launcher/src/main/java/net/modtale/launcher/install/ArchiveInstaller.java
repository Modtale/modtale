package net.modtale.launcher.install;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ArchiveInstaller {

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
        Files.createDirectories(modsDirectory);
        Path stagingDirectory = Files.createTempDirectory("modtale-modpack-");
        try {
            Path extractedDirectory = stagingDirectory.resolve("extracted");
            Files.createDirectories(extractedDirectory);
            extractArchive(archive, extractedDirectory);

            Path contentRoot = modpackContentRoot(extractedDirectory);
            List<Path> installed = new ArrayList<>();
            try (Stream<Path> files = Files.walk(contentRoot)) {
                for (Path source : files
                        .filter(Files::isRegularFile)
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
}
