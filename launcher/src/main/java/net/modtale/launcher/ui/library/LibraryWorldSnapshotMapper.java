package net.modtale.launcher.ui.library;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.worldlist.CreateWorldModListRequest;

final class LibraryWorldSnapshotMapper {

    private final List<InstalledProject> installedProjects;
    private final Map<String, HytaleInstalledMod> modsById;
    private final Map<String, InstalledProject> projectsByFile;

    private LibraryWorldSnapshotMapper(
            List<InstalledProject> installedProjects,
            List<HytaleInstalledMod> installedMods
    ) {
        this.installedProjects = installedProjects == null ? List.of() : List.copyOf(installedProjects);
        this.modsById = installedModsById(installedMods);
        this.projectsByFile = installedProjectsByFile(this.installedProjects);
    }

    static List<CreateWorldModListRequest.Item> itemsFor(
            Set<String> enabledModIds,
            List<InstalledProject> installedProjects,
            List<HytaleInstalledMod> installedMods
    ) {
        LibraryWorldSnapshotMapper mapper = new LibraryWorldSnapshotMapper(installedProjects, installedMods);
        List<CreateWorldModListRequest.Item> items = new ArrayList<>();
        if (enabledModIds == null) {
            return items;
        }
        for (String modId : enabledModIds) {
            if (modId != null && !modId.isBlank()) {
                items.add(mapper.itemFor(modId.trim()));
            }
        }
        return items;
    }

    private CreateWorldModListRequest.Item itemFor(String modId) {
        HytaleInstalledMod localMod = modsById.get(modId);
        SnapshotMatch match = matchFor(modId, localMod);
        return match == null ? externalSnapshotItem(modId, localMod) : matchedSnapshotItem(modId, localMod, match);
    }

    private SnapshotMatch matchFor(String modId, HytaleInstalledMod localMod) {
        if (localMod != null) {
            InstalledProject byFile = projectsByFile.get(normalizedFileKey(localMod.file()));
            if (byFile != null) {
                InstalledProjectReference reference = matchingReference(modId, localMod, byFile);
                if (reference != null) {
                    return SnapshotMatch.fromReference(reference, byFile.installedVersion());
                }
                return SnapshotMatch.fromProject(byFile);
            }
        }

        for (InstalledProject project : installedProjects) {
            InstalledProjectReference reference = matchingReference(modId, localMod, project);
            if (reference != null) {
                return SnapshotMatch.fromReference(reference, project.installedVersion());
            }
        }

        return installedProjects.stream()
                .filter(project -> LibraryProjectSupport.projectWorldModIds(project).contains(modId))
                .findFirst()
                .map(SnapshotMatch::fromProject)
                .orElse(null);
    }

    private InstalledProjectReference matchingReference(
            String modId,
            HytaleInstalledMod localMod,
            InstalledProject project
    ) {
        if (project == null) {
            return null;
        }
        List<InstalledProjectReference> references = bundledReferences(project);
        if (references.isEmpty()) {
            return null;
        }
        Set<String> modKeys = new LinkedHashSet<>();
        addNormalized(modKeys, modId);
        if (localMod != null) {
            addNormalized(modKeys, localMod.id());
            addNormalized(modKeys, localMod.name());
            addNormalized(modKeys, fileName(localMod.file()));
        }
        if (modKeys.isEmpty()) {
            return null;
        }

        for (InstalledProjectReference reference : references) {
            Set<String> referenceKeys = new LinkedHashSet<>();
            addNormalized(referenceKeys, LibraryProjectSupport.referenceWorldModId(reference));
            addNormalized(referenceKeys, reference.title());
            addNormalized(referenceKeys, reference.slug());
            addNormalized(referenceKeys, reference.projectId());
            addNormalized(referenceKeys, reference.externalId());
            addNormalized(referenceKeys, reference.externalFileName());
            addNormalized(referenceKeys, fileName(reference.externalFileUrl()));
            addNormalized(referenceKeys, fileName(reference.cachedFileUrl()));
            for (String key : modKeys) {
                if (referenceKeys.contains(key)) {
                    return reference;
                }
            }
        }
        return null;
    }

    private CreateWorldModListRequest.Item matchedSnapshotItem(
            String modId,
            HytaleInstalledMod localMod,
            SnapshotMatch match
    ) {
        String localTitle = localMod == null ? modId : localMod.name();
        String localVersion = localMod == null ? "" : localMod.version();
        return new CreateWorldModListRequest.Item(
                modId,
                match.projectId(),
                match.slug(),
                first(match.title(), localTitle, modId),
                first(match.versionNumber(), localVersion),
                first(match.classification(), "PLUGIN"),
                worldListSource(first(match.source(), match.projectId().isBlank() ? "OTHER" : InstalledProject.SOURCE_MODTALE)),
                match.externalId(),
                match.externalUrl(),
                match.icon()
        );
    }

    private CreateWorldModListRequest.Item externalSnapshotItem(String modId, HytaleInstalledMod localMod) {
        String title = localMod == null ? modId : first(localMod.name(), modId);
        String version = localMod == null ? "" : localMod.version();
        return new CreateWorldModListRequest.Item(
                modId,
                "",
                "",
                title,
                version,
                "PLUGIN",
                "OTHER",
                modId,
                "",
                ""
        );
    }

    private static Map<String, HytaleInstalledMod> installedModsById(List<HytaleInstalledMod> mods) {
        Map<String, HytaleInstalledMod> byId = new LinkedHashMap<>();
        if (mods == null) {
            return byId;
        }
        for (HytaleInstalledMod mod : mods) {
            if (mod != null && mod.id() != null && !mod.id().isBlank()) {
                byId.put(mod.id(), mod);
            }
        }
        return byId;
    }

    private static Map<String, InstalledProject> installedProjectsByFile(List<InstalledProject> projects) {
        Map<String, InstalledProject> byFile = new LinkedHashMap<>();
        if (projects == null) {
            return byFile;
        }
        for (InstalledProject project : projects) {
            if (project == null) {
                continue;
            }
            for (String file : project.files()) {
                String key = normalizedFileKey(file);
                if (!key.isBlank()) {
                    byFile.putIfAbsent(key, project);
                }
            }
        }
        return byFile;
    }

    private static List<InstalledProjectReference> bundledReferences(InstalledProject installed) {
        if (installed == null) {
            return List.of();
        }
        if (!installed.bundledProjects().isEmpty()) {
            return installed.bundledProjects();
        }
        List<InstalledProjectReference> references = new ArrayList<>();
        installed.dependencyProjectIds().forEach(id -> references.add(new InstalledProjectReference(
                id, id, "", id, "", "", "", InstalledProject.SOURCE_MODTALE, "", "", "", "", "", "", null, null
        )));
        installed.externalDependencies().forEach(id -> references.add(new InstalledProjectReference(
                id, "", "", id, "", "", "", "EXTERNAL", id, "", "", "", "", "", null, null
        )));
        return references;
    }

    private static void addNormalized(Set<String> keys, String value) {
        String normalized = normalizedNameKey(value);
        if (!normalized.isBlank()) {
            keys.add(normalized);
        }
    }

    private static String fileName(Path value) {
        return value == null ? "" : value.getFileName().toString();
    }

    private static String fileName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        int query = normalized.indexOf('?');
        if (query >= 0) {
            normalized = normalized.substring(0, query);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String normalizedNameKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("(?i)\\.(jar|zip|hytale)$", "");
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private static String normalizedFileKey(String file) {
        if (file == null || file.isBlank()) {
            return "";
        }
        return normalizedFileKey(Path.of(file));
    }

    private static String normalizedFileKey(Path file) {
        if (file == null) {
            return "";
        }
        return file.toAbsolutePath().normalize().toString();
    }

    private static String worldListSource(String source) {
        String normalized = first(source).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MODTALE", "CURSEFORGE", "GITHUB", "WEBSITE", "OTHER" -> normalized;
            default -> "OTHER";
        };
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

    private record SnapshotMatch(
            String projectId,
            String slug,
            String title,
            String versionNumber,
            String classification,
            String source,
            String externalId,
            String externalUrl,
            String icon
    ) {
        private SnapshotMatch {
            projectId = first(projectId);
            slug = first(slug);
            title = first(title);
            versionNumber = first(versionNumber);
            classification = first(classification);
            source = first(source);
            externalId = first(externalId);
            externalUrl = first(externalUrl);
            icon = first(icon);
        }

        private static SnapshotMatch fromProject(InstalledProject project) {
            boolean modtaleProject = LibraryProjectSupport.isModtaleProject(project);
            return new SnapshotMatch(
                    modtaleProject ? project.projectId() : "",
                    modtaleProject ? project.slug() : "",
                    project.title(),
                    project.installedVersion(),
                    project.classification(),
                    modtaleProject ? InstalledProject.SOURCE_MODTALE : first(project.source(), "OTHER"),
                    modtaleProject ? "" : first(project.projectId(), project.slug()),
                    "",
                    ""
            );
        }

        private static SnapshotMatch fromReference(InstalledProjectReference reference, String fallbackVersion) {
            boolean modtaleProject = reference.isModtaleProject();
            return new SnapshotMatch(
                    modtaleProject ? reference.projectId() : "",
                    modtaleProject ? reference.slug() : "",
                    reference.displayName(),
                    first(reference.versionNumber(), fallbackVersion),
                    first(reference.classification(), "PLUGIN"),
                    modtaleProject ? InstalledProject.SOURCE_MODTALE : first(reference.source(), "OTHER"),
                    modtaleProject ? "" : first(reference.externalId(), reference.id(), LibraryProjectSupport.referenceWorldModId(reference)),
                    reference.externalUrl(),
                    reference.icon()
            );
        }
    }
}
