package net.modtale.launcher.ui.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.modtale.launcher.model.install.InstalledProject;

final class LibraryManifestCompatibility {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private final Map<Path, Cached> cache = new HashMap<>();

    private record Cached(FileTime modified, long size, Object fileKey, String requirement) { }

    List<String> forProject(InstalledProject installed) {
        // A pack's children have separate requirements, not a union of supported versions.
        if (installed.isModpack()) return List.of();
        return installed.files().stream().map(this::read).filter(value -> !value.isBlank()).distinct().toList();
    }

    String read(String file) {
        if (file == null || file.isBlank()) return "";
        try {
            return read(Path.of(file));
        } catch (java.nio.file.InvalidPathException ignored) {
            return "";
        }
    }

    String read(Path file) {
        if (file == null) return "";
        Path source = file.toAbsolutePath().normalize();
        boolean directory = Files.isDirectory(source);
        if (directory) source = source.resolve("manifest.json");
        try {
            BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
            Cached previous = cache.get(source);
            if (previous != null && previous.modified().equals(attributes.lastModifiedTime())
                    && previous.size() == attributes.size() && Objects.equals(previous.fileKey(), attributes.fileKey())) {
                return previous.requirement();
            }
            String requirement;
            if (directory) {
                try (InputStream input = Files.newInputStream(source)) {
                    requirement = requirement(input);
                }
            } else {
                try (ZipFile zip = new ZipFile(source.toFile())) {
                    ZipEntry manifest = zip.getEntry("manifest.json");
                    if (manifest == null || manifest.isDirectory() || manifest.getSize() > MAX_MANIFEST_BYTES) {
                        requirement = "";
                    } else {
                        try (InputStream input = zip.getInputStream(manifest)) {
                            requirement = requirement(input);
                        }
                    }
                }
            }
            cache.put(source, new Cached(attributes.lastModifiedTime(), attributes.size(), attributes.fileKey(), requirement));
            return requirement;
        } catch (IOException ignored) {
            cache.remove(source);
            return "";
        }
    }

    private static String requirement(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
        if (bytes.length > MAX_MANIFEST_BYTES) return "";
        JsonNode root = MAPPER.readTree(bytes);
        JsonNode version = root == null ? null : root.get("ServerVersion");
        // Match the site's manifest field; never substitute Version, dependencies, or API metadata.
        return version != null && version.isTextual() ? version.asText().trim().replaceAll("\\s+", " ") : "";
    }

    static String label(String requirement) {
        return ManifestVersionLabel.format(requirement);
    }
}
