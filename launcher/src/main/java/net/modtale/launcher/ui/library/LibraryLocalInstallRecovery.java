package net.modtale.launcher.ui.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.settings.LauncherSettings;

final class LibraryLocalInstallRecovery {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LibraryLocalInstallRecovery() {
    }

    static RecoveryResult recover(
            LauncherSettings settings,
            List<InstalledProject> recordedProjects,
            List<HytaleInstalledMod> installedMods
    ) {
        List<InstalledProject> recorded = recordedProjects == null ? List.of() : List.copyOf(recordedProjects);
        if (installedMods == null || installedMods.isEmpty()) {
            return new RecoveryResult(recorded, 0);
        }

        Map<String, InstalledProject> byProjectId = new LinkedHashMap<>();
        Set<String> recordedFiles = new LinkedHashSet<>();
        for (InstalledProject project : recorded) {
            if (project == null || project.projectId().isBlank()) {
                continue;
            }
            byProjectId.put(project.projectId(), project);
            project.files().stream()
                    .map(LibraryLocalInstallRecovery::normalizedPath)
                    .filter(path -> !path.isBlank())
                    .forEach(recordedFiles::add);
        }

        int recovered = 0;
        for (HytaleInstalledMod mod : installedMods) {
            String fileKey = normalizedPath(mod.file());
            if (fileKey.isBlank() || recordedFiles.contains(fileKey)) {
                continue;
            }
            InstalledProject project = fromInstalledMod(settings, mod, byProjectId.keySet());
            byProjectId.put(project.projectId(), project);
            recordedFiles.add(fileKey);
            recovered++;
        }
        return new RecoveryResult(List.copyOf(byProjectId.values()), recovered);
    }

    private static InstalledProject fromInstalledMod(
            LauncherSettings settings,
            HytaleInstalledMod mod,
            Set<String> existingProjectIds
    ) {
        String title = first(mod.name(), fileBaseName(mod.file()), "Local Mod");
        String manifestId = first(mod.id(), title);
        ManifestMetadata manifest = manifestMetadata(mod.file());
        String modtaleSlug = modtaleSlug(manifest.website());
        boolean modtaleProject = !modtaleSlug.isBlank();
        String projectId = uniqueProjectId(modtaleProject ? modtaleSlug : "local:" + sanitize(manifestId), existingProjectIds);
        Instant modifiedAt = lastModified(mod.file());
        return new InstalledProject(
                projectId,
                modtaleProject ? modtaleSlug : manifestId,
                title,
                "PLUGIN",
                first(mod.version(), "installed"),
                "",
                settings == null ? "" : settings.getGameVersion(),
                modifiedAt,
                modifiedAt,
                List.of(mod.file().toString()),
                List.of(),
                List.of(),
                modtaleProject ? InstalledProject.SOURCE_MODTALE : InstalledProject.SOURCE_LOCAL,
                InstalledProject.INSTALL_DIRECT,
                false,
                List.of()
        );
    }

    private static String uniqueProjectId(String base, Set<String> existingProjectIds) {
        String id = base == null || base.isBlank() ? "local:mod" : base;
        if (!existingProjectIds.contains(id)) {
            return id;
        }
        int suffix = 2;
        while (existingProjectIds.contains(id + "-" + suffix)) {
            suffix++;
        }
        return id + "-" + suffix;
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ignored) {
            return Instant.now();
        }
    }

    private static ManifestMetadata manifestMetadata(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return new ManifestMetadata("", "");
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry manifest = zip.getEntry("manifest.json");
            if (manifest == null) {
                return new ManifestMetadata("", "");
            }
            try (InputStream input = zip.getInputStream(manifest)) {
                JsonNode root = MAPPER.readTree(input);
                String author = "";
                JsonNode authors = root.path("Authors");
                if (authors.isArray() && !authors.isEmpty()) {
                    author = authors.get(0).path("Name").asText("");
                }
                return new ManifestMetadata(root.path("Website").asText(""), author);
            }
        } catch (IOException ignored) {
            return new ManifestMetadata("", "");
        }
    }

    private static String modtaleSlug(String website) {
        if (website == null || website.isBlank()) {
            return "";
        }
        String value = website.trim();
        int marker = value.toLowerCase(Locale.ROOT).indexOf("modtale.net/mod/");
        if (marker < 0) {
            return "";
        }
        String slug = value.substring(marker + "modtale.net/mod/".length());
        for (String delimiter : new String[]{"/", "?", "#"}) {
            int index = slug.indexOf(delimiter);
            if (index >= 0) {
                slug = slug.substring(0, index);
            }
        }
        return sanitize(slug);
    }

    private static String fileBaseName(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        return path.getFileName().toString().replaceFirst("(?i)\\.jar$", "");
    }

    private static String normalizedPath(Path path) {
        if (path == null) {
            return "";
        }
        return path.toAbsolutePath().normalize().toString();
    }

    private static String normalizedPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return Path.of(path).toAbsolutePath().normalize().toString();
    }

    private static String sanitize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "mod";
        }
        return normalized.replaceAll("[^A-Za-z0-9._:-]+", "-").toLowerCase(Locale.ROOT);
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    record RecoveryResult(List<InstalledProject> projects, int recoveredCount) {
        RecoveryResult {
            projects = projects == null ? List.of() : List.copyOf(projects);
            recoveredCount = Math.max(0, recoveredCount);
        }
    }

    private record ManifestMetadata(String website, String author) {
        private ManifestMetadata {
            website = website == null ? "" : website.trim();
            author = author == null ? "" : author.trim();
        }
    }
}
