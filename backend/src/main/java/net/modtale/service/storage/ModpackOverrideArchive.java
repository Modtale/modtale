package net.modtale.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.modtale.model.project.ProjectDependency;

public final class ModpackOverrideArchive {
    private static final int MAX_FILES = 10_000;
    private static final long MAX_FILE_SIZE = 32L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 512L * 1024 * 1024;
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
        List<OverrideFile> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (files.size() >= MAX_FILES) throw new IOException("Override bundle contains too many files.");
                ParsedPath parsed = parsePath(entry.getName());
                if (!paths.add(parsed.path().toLowerCase(Locale.ROOT))) {
                    throw new IOException("Override bundle contains duplicate or case-colliding paths.");
                }
                byte[] bytes = zip.readNBytes((int) MAX_FILE_SIZE + 1);
                if (bytes.length > MAX_FILE_SIZE) throw new IOException("An override file exceeds the 32 MiB limit.");
                total += bytes.length;
                if (total > MAX_TOTAL_SIZE) throw new IOException("Override bundle exceeds the 512 MiB expanded limit.");
                files.add(new OverrideFile(parsed.path(), parsed.environment(), bytes));
            }
        }
        if (files.isEmpty()) throw new IOException("Override bundle does not contain any files.");
        return List.copyOf(files);
    }

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
        ProjectDependency.Environment environment;
        String prefix;
        if (lower.startsWith("overrides/common/")) {
            environment = ProjectDependency.Environment.COMMON;
            prefix = "overrides/common/";
        } else if (lower.startsWith("overrides/client/")) {
            environment = ProjectDependency.Environment.CLIENT;
            prefix = "overrides/client/";
        } else if (lower.startsWith("overrides/server/")) {
            environment = ProjectDependency.Environment.SERVER;
            prefix = "overrides/server/";
        }
        else throw new IOException("Override files must be inside overrides/common, overrides/client, or overrides/server.");
        String relative = path.substring(prefix.length());
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
        return new ParsedPath(prefix + relative, environment);
    }

    private record ParsedPath(String path, ProjectDependency.Environment environment) {}
    public record OverrideFile(String path, ProjectDependency.Environment environment, byte[] bytes) {}
}
