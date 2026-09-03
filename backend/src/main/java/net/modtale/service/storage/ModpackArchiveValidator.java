package net.modtale.service.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ModpackArchiveValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_LOCKFILE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> METADATA_FILES = Set.of(
            "modpack.json",
            "manifest.json",
            "modtale.lock.json"
    );

    private ModpackArchiveValidator() {}

    static void validate(byte[] archive) throws IOException {
        if (archive == null || archive.length == 0) {
            throw new IOException("Modpack archive is empty.");
        }

        Map<String, EntryFingerprint> entries = new HashMap<>();
        Set<String> caseFoldedNames = new HashSet<>();
        Map<String, byte[]> metadata = new HashMap<>();
        long totalBytes = 0;
        int entryCount = 0;

        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];
            while ((entry = input.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("Modpack archive contains too many entries.");
                }
                String name = validatePath(entry.getName());
                if (entry.isDirectory()) {
                    throw new IOException("Modpack archive must not contain directory entries.");
                }
                if (!caseFoldedNames.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("Modpack archive contains duplicate or case-colliding path: " + name);
                }

                MessageDigest digest = sha256Digest();
                ByteArrayOutputStream captured = METADATA_FILES.contains(name)
                        ? new ByteArrayOutputStream()
                        : null;
                long size = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    totalBytes += read;
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw new IOException("Modpack archive exceeds the uncompressed size limit.");
                    }
                    if (captured != null) {
                        if (size > MAX_LOCKFILE_BYTES) {
                            throw new IOException("Modpack metadata file exceeds the size limit.");
                        }
                        captured.write(buffer, 0, read);
                    }
                    digest.update(buffer, 0, read);
                }
                entries.put(name, new EntryFingerprint(size, HexFormat.of().formatHex(digest.digest())));
                if (captured != null) {
                    metadata.put(name, captured.toByteArray());
                }
                input.closeEntry();
            }
        }

        if (!entries.keySet().containsAll(METADATA_FILES)) {
            throw new IOException("Modpack archive is missing required metadata files.");
        }
        validateMetadata(metadata, entries);
    }

    private static void validateMetadata(
            Map<String, byte[]> metadata,
            Map<String, EntryFingerprint> archiveEntries
    ) throws IOException {
        JsonNode legacy = readJson(metadata.get("modpack.json"), "Legacy modpack manifest");
        if (!legacy.isObject() || legacy.path("formatVersion").asInt(-1) != 1
                || !"hytale".equals(legacy.path("game").asText()) || !legacy.path("files").isArray()) {
            throw new IOException("Legacy modpack manifest has an unsupported format.");
        }
        JsonNode manifest = readJson(metadata.get("manifest.json"), "Modpack manifest");
        if (!manifest.isObject() || !"modtale-pack".equals(manifest.path("format").asText())
                || manifest.path("schemaVersion").asInt(-1) != 1 || !manifest.path("pack").isObject()
                || !manifest.path("game").isObject() || !manifest.path("dependencies").isArray()) {
            throw new IOException("Modpack manifest has an unsupported format.");
        }
        for (JsonNode item : manifest.path("dependencies")) {
            validateEnvironment(item);
        }
        JsonNode lock = readJson(metadata.get("modtale.lock.json"), "Modpack lockfile");
        if (lock == null || !"modtale-lock".equals(lock.path("format").asText())
                || lock.path("lockVersion").asInt(-1) != 1 || !lock.path("pack").isObject()
                || !lock.path("gameVersions").isArray() || !lock.path("entries").isArray()) {
            throw new IOException("Modpack lockfile has an unsupported format.");
        }

        Set<String> expectedFiles = new HashSet<>(METADATA_FILES);
        for (JsonNode item : lock.path("entries")) {
            String distribution = item.path("distribution").asText();
            String source = item.path("source").asText();
            validateEnvironment(item);
            if ("REFERENCE_ONLY".equals(distribution)) {
                if (item.has("path") || item.has("size") || item.has("hashes")) {
                    throw new IOException("Reference-only lock entries must not claim bundled bytes.");
                }
                if ("CURSEFORGE".equals(source)) {
                    validateCurseForgeReference(item);
                }
                continue;
            }
            if (!"BUNDLED".equals(distribution)) {
                throw new IOException("Modpack lock entry has an unknown distribution mode.");
            }
            if ("CURSEFORGE".equals(source)) {
                throw new IOException("CurseForge artifacts must remain reference-only.");
            }

            String path = validatePath(item.path("path").asText(null));
            if (!expectedFiles.add(path)) {
                throw new IOException("Modpack lockfile contains a duplicate path: " + path);
            }
            EntryFingerprint actual = archiveEntries.get(path);
            if (actual == null) {
                throw new IOException("Modpack lockfile references a missing bundled file: " + path);
            }
            long expectedSize = item.path("size").asLong(-1);
            String expectedHash = item.path("hashes").path("sha256").asText();
            if (expectedSize <= 0 || expectedSize != actual.size()) {
                throw new IOException("Bundled file size does not match the lockfile: " + path);
            }
            if (!expectedHash.matches("[a-f0-9]{64}") || !expectedHash.equals(actual.sha256())) {
                throw new IOException("Bundled file hash does not match the lockfile: " + path);
            }
        }

        if (!archiveEntries.keySet().equals(expectedFiles)) {
            throw new IOException("Modpack archive contains files that are not declared in the lockfile.");
        }
    }

    private static void validateEnvironment(JsonNode item) throws IOException {
        if (!Set.of("COMMON", "CLIENT", "SERVER").contains(item.path("environment").asText())) {
            throw new IOException("Modpack entry has an unknown environment.");
        }
    }

    private static void validateCurseForgeReference(JsonNode item) throws IOException {
        JsonNode provider = item.path("provider");
        String projectId = provider.path("projectId").asText();
        String projectSlug = provider.path("projectSlug").asText();
        String fileId = provider.path("fileId").asText();
        if ((!projectId.matches("[0-9]+") && projectSlug.isBlank())
                || (!projectSlug.isBlank() && !projectSlug.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
                || !fileId.matches("[0-9]+")) {
            throw new IOException("CurseForge lock entry is missing a valid project and file identity.");
        }
        validateCurseForgeFileUrl(item.path("url").asText(), fileId);
        validateCurseForgeFileUrl(item.path("fileUrl").asText(), fileId);
    }

    private static void validateCurseForgeFileUrl(String value, String fileId) throws IOException {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            String path = uri.getPath();
            boolean trustedHost = host != null && (host.equalsIgnoreCase("curseforge.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".curseforge.com"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || !trustedHost
                    || path == null || !path.toLowerCase(Locale.ROOT).startsWith("/hytale/mods/")
                    || !path.matches("(?i).*/files/" + fileId + "/?")) {
                throw new IOException("CurseForge lock entry contains an invalid file-page URL.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IOException("CurseForge lock entry contains an invalid file-page URL.", ex);
        }
    }

    private static JsonNode readJson(byte[] bytes, String label) throws IOException {
        if (bytes == null) {
            throw new IOException(label + " is missing.");
        }
        try {
            JsonNode value = OBJECT_MAPPER.readTree(bytes);
            if (value == null) {
                throw new IOException(label + " is empty.");
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IOException(label + " is not valid JSON.", ex);
        }
    }

    private static String validatePath(String name) throws IOException {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                || name.indexOf('\0') >= 0 || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("Modpack archive contains an unsafe path: " + name);
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Modpack archive contains an unsafe path: " + name);
            }
        }
        return name;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", ex);
        }
    }

    private record EntryFingerprint(long size, String sha256) {}
}
