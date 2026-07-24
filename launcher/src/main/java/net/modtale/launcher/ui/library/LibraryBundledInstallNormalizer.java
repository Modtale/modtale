package net.modtale.launcher.ui.library;

import java.nio.file.Path;
import java.time.Instant;
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

final class LibraryBundledInstallNormalizer {

    private LibraryBundledInstallNormalizer() {
    }

    static Result normalize(List<InstalledProject> projects, List<HytaleInstalledMod> installedMods) {
        if (projects == null || projects.isEmpty()) {
            return new Result(List.of(), 0);
        }
        Map<String, HytaleInstalledMod> modsByFile = installedModsByFile(installedMods);
        List<InstalledProject> normalized = new ArrayList<>();
        int addedChildren = 0;
        for (InstalledProject project : projects) {
            if (!shouldSplit(project)) {
                normalized.add(project);
                continue;
            }
            SplitResult split = split(project, modsByFile);
            normalized.add(split.parent());
            normalized.addAll(split.children());
            addedChildren += split.children().size();
        }
        return new Result(dedupe(normalized), addedChildren);
    }

    private static boolean shouldSplit(InstalledProject project) {
        return project != null
                && !project.isModpack()
                && (!project.bundledProjects().isEmpty()
                || !project.dependencyProjectIds().isEmpty()
                || !project.externalDependencies().isEmpty());
    }

    private static SplitResult split(InstalledProject parent, Map<String, HytaleInstalledMod> modsByFile) {
        List<InstalledProjectReference> references = references(parent);
        Map<String, List<String>> filesByReference = new LinkedHashMap<>();
        Set<String> dependencyFiles = new LinkedHashSet<>();

        for (String file : parent.files()) {
            HytaleInstalledMod mod = modsByFile.get(normalizedFileKey(file));
            InstalledProjectReference reference = matchingReference(mod, file, references);
            if (reference == null) {
                continue;
            }
            String key = referenceKey(reference);
            filesByReference.computeIfAbsent(key, ignored -> new ArrayList<>()).add(file);
            dependencyFiles.add(normalizedFileKey(file));
        }

        if (filesByReference.isEmpty()) {
            return new SplitResult(parent, List.of());
        }

        List<String> parentFiles = parent.files().stream()
                .filter(file -> !dependencyFiles.contains(normalizedFileKey(file)))
                .toList();
        InstalledProject strippedParent = new InstalledProject(
                parent.projectId(),
                parent.slug(),
                parent.title(),
                parent.classification(),
                parent.installedVersion(),
                parent.installedVersionId(),
                parent.gameVersion(),
                parent.installedAt(),
                parent.updatedAt(),
                parentFiles.isEmpty() ? parent.files() : parentFiles,
                List.of(),
                List.of(),
                parent.source(),
                InstalledProject.INSTALL_DIRECT,
                false,
                List.of()
        );

        List<InstalledProject> children = new ArrayList<>();
        for (InstalledProjectReference reference : references) {
            List<String> files = filesByReference.get(referenceKey(reference));
            if (files == null || files.isEmpty()) {
                continue;
            }
            children.add(childProject(parent, reference, files));
        }
        return new SplitResult(strippedParent, children);
    }

    private static InstalledProject childProject(
            InstalledProject parent,
            InstalledProjectReference reference,
            List<String> files
    ) {
        String projectId = reference.isModtaleProject()
                ? reference.projectId()
                : externalProjectId(reference);
        return new InstalledProject(
                projectId,
                first(reference.slug(), projectId),
                reference.displayName(),
                first(reference.classification(), "PLUGIN"),
                first(reference.versionNumber(), parent.installedVersion()),
                "",
                parent.gameVersion(),
                safeInstant(parent.installedAt()),
                safeInstant(parent.updatedAt()),
                files,
                List.of(),
                List.of(),
                reference.isModtaleProject() ? InstalledProject.SOURCE_MODTALE : first(reference.source(), InstalledProject.SOURCE_LOCAL),
                InstalledProject.INSTALL_DIRECT,
                false,
                List.of()
        );
    }

    private static String externalProjectId(InstalledProjectReference reference) {
        return "external:" + sanitize(first(
                reference.externalId(),
                reference.id(),
                reference.externalFileName(),
                reference.displayName()
        ));
    }

    private static List<InstalledProject> dedupe(List<InstalledProject> projects) {
        Map<String, InstalledProject> byProjectId = new LinkedHashMap<>();
        for (InstalledProject project : projects) {
            if (project != null && !project.projectId().isBlank()) {
                byProjectId.putIfAbsent(project.projectId(), project);
            }
        }
        return List.copyOf(byProjectId.values());
    }

    private static List<InstalledProjectReference> references(InstalledProject installed) {
        if (!installed.bundledProjects().isEmpty()) {
            return installed.bundledProjects();
        }
        List<InstalledProjectReference> references = new ArrayList<>();
        installed.dependencyProjectIds().forEach(id -> references.add(new InstalledProjectReference(
                id, id, "", id, "", "", "", "MODTALE", "", "", "", "", "", "", null, null
        )));
        installed.externalDependencies().forEach(id -> references.add(new InstalledProjectReference(
                id, "", "", id, "", "", "", "EXTERNAL", id, "", "", "", "", "", null, null
        )));
        return references;
    }

    private static InstalledProjectReference matchingReference(
            HytaleInstalledMod mod,
            String file,
            List<InstalledProjectReference> references
    ) {
        if (mod == null || references == null || references.isEmpty()) {
            return null;
        }
        Set<String> modKeys = new LinkedHashSet<>();
        addNormalized(modKeys, mod.id());
        addNormalized(modKeys, mod.name());
        addNormalized(modKeys, fileName(file));
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

    private static Map<String, HytaleInstalledMod> installedModsByFile(List<HytaleInstalledMod> mods) {
        Map<String, HytaleInstalledMod> byFile = new LinkedHashMap<>();
        if (mods == null) {
            return byFile;
        }
        for (HytaleInstalledMod mod : mods) {
            String key = normalizedFileKey(mod.file());
            if (!key.isBlank()) {
                byFile.putIfAbsent(key, mod);
            }
        }
        return byFile;
    }

    private static void addNormalized(Set<String> keys, String value) {
        String normalized = normalizedNameKey(value);
        if (!normalized.isBlank()) {
            keys.add(normalized);
        }
    }

    private static String referenceKey(InstalledProjectReference reference) {
        return first(
                reference.projectId(),
                reference.externalId(),
                reference.id(),
                reference.slug(),
                reference.displayName()
        );
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

    private static String sanitize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "dependency";
        }
        return normalized.replaceAll("[^A-Za-z0-9._:-]+", "-").toLowerCase(Locale.ROOT);
    }

    private static Instant safeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
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

    record Result(List<InstalledProject> projects, int addedChildren) {
        Result {
            projects = projects == null ? List.of() : List.copyOf(projects);
            addedChildren = Math.max(0, addedChildren);
        }
    }

    private record SplitResult(InstalledProject parent, List<InstalledProject> children) {
        private SplitResult {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
