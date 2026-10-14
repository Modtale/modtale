package net.modtale.service.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import net.modtale.model.project.ModpackConfigReference;
import net.modtale.model.project.ProjectDependency;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class ModpackOverrideArchive {
    public static final String CONFIG_MANIFEST = "modtale.configs.json";
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    // Every accepted override file must have a config owner; enforce the eventual config limits while streaming.
    private static final int MAX_FILES = 100;
    private static final int MAX_FILE_SIZE = 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 32L * 1024 * 1024;
    private static final long MAX_ARCHIVE_SIZE = 100L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 256;
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".sh", ".bat", ".cmd", ".ps1",
            ".vbs", ".js", ".jsp", ".php", ".py", ".pl", ".html", ".htm",
            ".svg", ".hta", ".jar", ".zip", ".rar", ".7z", ".tar", ".gz"
    );
    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    );

    private ModpackOverrideArchive() {}

    public static List<OverrideFile> read(InputStream source) throws IOException {
        return readBundle(source).files();
    }

    public static Bundle readBundle(InputStream source) throws IOException {
        try (source) {
            Path archive = Files.createTempFile("modtale-override-", ".zip");
            try {
                long size = 0;
                try (OutputStream output = Files.newOutputStream(archive)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = source.read(buffer)) != -1) {
                        if (read == 0) throw new IOException("Override upload stream made no progress.");
                        size += read;
                        if (size > MAX_ARCHIVE_SIZE) throw new IOException("Override bundle exceeds the 100 MiB upload limit.");
                        output.write(buffer, 0, read);
                    }
                }
                LocalRead local;
                try (InputStream input = Files.newInputStream(archive)) { local = readLocal(input); }
                validateCentralDirectory(archive, local.entries());
                return local.bundle();
            } finally {
                Files.deleteIfExists(archive);
            }
        }
    }

    private static LocalRead readLocal(InputStream source) throws IOException {
        List<OverrideFile> files = new ArrayList<>();
        byte[] manifest = null;
        Set<String> paths = new HashSet<>();
        Set<String> zipPaths = new HashSet<>();
        Map<String, EntryFingerprint> entries = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entries.size() >= MAX_ZIP_ENTRIES || !zipPaths.add(entry.getName().toLowerCase(Locale.ROOT)))
                    throw new IOException("Override bundle contains too many or case-colliding ZIP entries.");
                if (entry.isDirectory()) {
                    entries.put(entry.getName(), new EntryFingerprint(0, digest(new byte[0]), true));
                    continue;
                }
                if (CONFIG_MANIFEST.equals(entry.getName())) {
                    if (manifest != null) throw new IOException("Duplicate config manifest.");
                    manifest = zip.readNBytes(128 * 1024 + 1);
                    if (manifest.length > 128 * 1024) throw new IOException("Config manifest exceeds 128 KiB.");
                    entries.put(entry.getName(), new EntryFingerprint(manifest.length, digest(manifest), false));
                    continue;
                }
                if (files.size() >= MAX_FILES) throw new IOException("Override bundle contains too many files.");
                ParsedPath parsed = parsePath(entry.getName());
                if (!paths.add(parsed.path().toLowerCase(Locale.ROOT))) {
                    throw new IOException("Override bundle contains duplicate or case-colliding paths.");
                }
                byte[] bytes = zip.readNBytes(MAX_FILE_SIZE + 1);
                if (bytes.length > MAX_FILE_SIZE) throw new IOException("An override config exceeds the 1 MiB limit.");
                total += bytes.length;
                if (total > MAX_TOTAL_SIZE) throw new IOException("Override bundle exceeds the 32 MiB expanded limit.");
                files.add(new OverrideFile(parsed.path(), bytes));
                entries.put(entry.getName(), new EntryFingerprint(bytes.length, digest(bytes), false));
            }
        }
        if (files.isEmpty()) throw new IOException("Override bundle does not contain any files.");
        files.sort(Comparator.comparing(OverrideFile::path));
        List<ModpackConfigReference> configs = readConfigs(manifest, files);
        for (OverrideFile file : files) {
            if (configs.stream().noneMatch(config -> config.path().equals(file.path()))) throw new IOException("Universe defaults must have a config owner.");
        }
        return new LocalRead(new Bundle(List.copyOf(files), configs), Map.copyOf(entries));
    }

    private static void validateCentralDirectory(Path archive, Map<String, EntryFingerprint> local) throws IOException {
        int expectedEntries = validateEndRecord(archive);
        if (expectedEntries != local.size()) throw new IOException("ZIP local entries differ from the central directory.");
        Set<String> seen = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            if (zip.size() != expectedEntries) throw new IOException("ZIP central directory count changed.");
            var central = zip.entries();
            while (central.hasMoreElements()) {
                ZipEntry entry = central.nextElement();
                EntryFingerprint expected = local.get(entry.getName());
                if (expected == null || !seen.add(entry.getName()) || expected.directory() != entry.isDirectory())
                    throw new IOException("ZIP central directory differs from local entries.");
                byte[] bytes;
                try (InputStream input = zip.getInputStream(entry)) {
                    bytes = input.readNBytes((int) expected.size() + 1);
                }
                if (bytes.length != expected.size() || !digest(bytes).equals(expected.sha256()))
                    throw new IOException("ZIP central entry bytes differ from local entries.");
            }
        }
        if (seen.size() != local.size()) throw new IOException("ZIP central directory omits local entries.");
    }

    private static int validateEndRecord(Path archive) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(archive.toFile(), "r")) {
            long size = file.length();
            if (size < 22) throw new IOException("ZIP end record is missing.");
            file.seek(0);
            if (file.read() != 'P' || file.read() != 'K' || file.read() != 3 || file.read() != 4)
                throw new IOException("ZIP contains a leading payload.");
            int tailSize = (int) Math.min(size, 22 + 65535);
            byte[] tail = new byte[tailSize];
            file.seek(size - tailSize);file.readFully(tail);
            int matched = -1;
            long centralOffset = -1, centralSize = -1;
            for (int i = 0; i <= tailSize - 22; i++) {
                if (tail[i] != 'P' || tail[i + 1] != 'K' || tail[i + 2] != 5 || tail[i + 3] != 6
                        || unsigned16(tail, i + 20) != tailSize - i - 22) continue;
                long position = size - tailSize + i;
                int count = unsigned16(tail, i + 10);
                if (unsigned16(tail, i + 4) != 0 || unsigned16(tail, i + 6) != 0
                        || unsigned16(tail, i + 8) != count || count > MAX_ZIP_ENTRIES
                        || unsigned32(tail, i + 16) + unsigned32(tail, i + 12) != position) continue;
                if (matched >= 0) throw new IOException("ZIP has ambiguous end records.");
                matched = count;
                centralOffset = unsigned32(tail, i + 16);
                centralSize = unsigned32(tail, i + 12);
            }
            if (matched < 0) throw new IOException("ZIP end record or trailing data is invalid.");
            validateCentralRecords(file, centralOffset, centralSize, matched);
            return matched;
        }
    }
    private static void validateCentralRecords(RandomAccessFile file, long offset, long size, int count) throws IOException {
        long position = offset, end = offset + size;
        for (int i = 0; i < count; i++) {
            if (position + 46 > end) throw new IOException("ZIP central directory is truncated.");
            byte[] header = new byte[46];file.seek(position);file.readFully(header);
            if (header[0] != 'P' || header[1] != 'K' || header[2] != 1 || header[3] != 2)
                throw new IOException("ZIP central directory has an invalid record.");
            int flags = unsigned16(header, 8), method = unsigned16(header, 10);
            int nameLength = unsigned16(header, 28), extraLength = unsigned16(header, 30);
            int commentLength = unsigned16(header, 32);
            long externalAttributes = unsigned32(header, 38);
            int unixType = (int) (externalAttributes >>> 28) & 0xf;
            if ((flags & 1) != 0 || method != 0 && method != 8 || unsigned16(header, 34) != 0
                    || unsigned32(header, 20) == 0xffffffffL || unsigned32(header, 24) == 0xffffffffL
                    || unsigned32(header, 42) >= offset || nameLength > 2048 || extraLength > 8192
                    || commentLength > 1024 || unixType != 0 && unixType != 4 && unixType != 8)
                throw new IOException("ZIP central entry uses unsupported or unsafe metadata.");
            position += 46L + nameLength + extraLength + commentLength;
            if (position > end) throw new IOException("ZIP central directory exceeds its declared size.");
        }
        if (position != end) throw new IOException("ZIP central directory contains undeclared records.");
    }
    private static int unsigned16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | (bytes[offset + 1] & 0xff) << 8;
    }
    private static long unsigned32(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(unsigned16(bytes, offset) | unsigned16(bytes, offset + 2) << 16);
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    private record EntryFingerprint(long size, String sha256, boolean directory) {}
    private record LocalRead(Bundle bundle, Map<String, EntryFingerprint> entries) {}

    private static List<ModpackConfigReference> readConfigs(byte[] bytes, List<OverrideFile> files) throws IOException {
        if (bytes == null) return List.of();
        JsonNode root = JSON.readTree(bytes);
        if (root == null || !"modtale-configs".equals(root.path("format").asText())
                || root.path("formatVersion").asInt() != 1 || !root.path("configs").isArray()
                || root.path("configs").size() > 100) throw new IOException("Unsupported config manifest.");
        List<ModpackConfigReference> configs = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long total = 0;
        for (JsonNode node : root.path("configs")) {
            String projectId = node.path("projectId").asText();
            String provider = node.path("source").asText();
            String path = node.path("path").asText();
            if (projectId.isBlank() || projectId.length() > 200 || !Set.of("MODTALE", "CURSEFORGE", "GITHUB", "WEBSITE", "OTHER").contains(provider)
                    || !path.equals(parsePath(path).path()) || !paths.add(path.toLowerCase(Locale.ROOT))) {
                throw new IOException("Invalid or duplicate config association.");
            }
            OverrideFile file = files.stream().filter(item -> item.path().equals(path)).findFirst()
                    .orElseThrow(() -> new IOException("Config manifest references a missing file: " + path));
            String[] parts = path.split("/");
            if (("Mods".equals(parts[1]) && parts.length < 4)
                    || ("Saves".equals(parts[1]) && (parts.length < 6 || !"mods".equals(parts[3])))) {
                throw new IOException("Config must be inside a mod folder: " + path);
            }
            String filename = parts[parts.length - 1].toLowerCase(Locale.ROOT);
            if (filename.equals("manifest.json") || !filename.matches(".*\\.(json|toml|yaml|yml|properties|cfg|conf|ini)")) {
                throw new IOException("Unsupported config file type: " + path);
            }
            total += file.bytes().length;
            if (file.bytes().length > 1024 * 1024 || total > 32L * 1024 * 1024) throw new IOException("Attached config size limit exceeded.");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.bytes())).toString();
            if (text.indexOf('\0') >= 0) throw new IOException("Config contains binary data.");
            if (filename.endsWith(".json")) {
                try (var parser = JSON.createParser(file.bytes())) {
                    if (JSON.readTree(parser) == null || parser.nextToken() != null) throw new IOException("Config must contain one JSON value.");
                }
            }
            String hash;
            try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.bytes())); }
            catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
            if (!hash.equals(node.path("sha256").asText())) throw new IOException("Config checksum mismatch: " + path);
            configs.add(new ModpackConfigReference(projectId, provider, path, hash));
        }
        configs.sort(Comparator.comparing(ModpackConfigReference::path));
        return List.copyOf(configs);
    }

    public static void validateOwners(List<ModpackConfigReference> configs, List<ProjectDependency> dependencies) throws IOException {
        Set<String> owners = new HashSet<>();
        if (dependencies != null) for (ProjectDependency dependency : dependencies) owners.add(dependency.getSource().name() + ":" + dependency.getProjectId());
        for (ModpackConfigReference config : configs) {
            if (!owners.contains(config.ownerKey())) throw new IOException("Config belongs to a mod not included in this pack: " + config.projectId());
        }
    }

    public record Bundle(List<OverrideFile> files, List<ModpackConfigReference> configs) {}

    private static ParsedPath parsePath(String raw) throws IOException {
        String path = raw == null ? "" : raw.replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.matches("^[A-Za-z]:.*") || path.contains("//")) {
            throw new IOException("Override bundle contains an unsafe path.");
        }
        for (String segment : path.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Override bundle contains an unsafe path.");
            }
        }
        String lower = path.toLowerCase(Locale.ROOT);
        String prefix = "overrides/";
        if (!lower.startsWith(prefix)) {
            throw new IOException("Override files must be inside overrides/.");
        }
        String relative = path.substring(prefix.length());
        if (relative.isBlank()) {
            throw new IOException("Override bundle contains an empty override path.");
        }
        String[] relativeSegments = relative.split("/", -1);
        String hytaleRoot;
        if ("Universe".equals(relativeSegments[0]) && relativeSegments.length >= 4 && "mods".equals(relativeSegments[1])) {
            hytaleRoot = "Universe";
        } else {
            throw new IOException("Only mod-associated configs under overrides/Universe/mods/ are supported. Saves and shared overrides are not supported.");
        }
        if (relativeSegments.length < 2) {
            throw new IOException("Override bundle contains an empty Hytale destination path.");
        }
        for (String segment : relative.split("/")) {
            if (segment.matches(".*[<>:\"|?*\\p{Cntrl}].*") || segment.endsWith(".") || segment.endsWith(" ")) {
                throw new IOException("Override bundle contains a non-portable path.");
            }
            String baseName = segment.toLowerCase(Locale.ROOT).split("\\.", 2)[0];
            if (WINDOWS_DEVICE_NAMES.contains(baseName)) {
                throw new IOException("Override bundle contains a reserved device name.");
            }
        }
        if (BLOCKED_EXTENSIONS.stream().anyMatch(lower::endsWith)) {
            throw new IOException("Override bundle contains a blocked executable, script, or nested archive.");
        }
        return new ParsedPath(prefix + hytaleRoot + "/" + String.join("/",
                java.util.Arrays.copyOfRange(relativeSegments, 1, relativeSegments.length)));
    }

    private record ParsedPath(String path) {}
    public record OverrideFile(String path, byte[] bytes) {}
}
