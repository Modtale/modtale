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

final class LibraryModpackUnlockConverter {

    private LibraryModpackUnlockConverter() {
    }

    static Result convert(
            List<InstalledProject> projects,
            InstalledProject modpack,
            List<HytaleInstalledMod> installedMods
    ) {
        if (modpack == null || !modpack.isModpack()) {
            return new Result(projects == null ? List.of() : projects, 0);
        }

        List<InstalledProject> children = childProjects(modpack, installedMods);
        if (children.isEmpty()) {
            return new Result(projects == null ? List.of() : projects, 0);
        }

        Map<String, InstalledProject> converted = new LinkedHashMap<>();
        if (projects != null) {
            for (InstalledProject project : projects) {
                if (project == null || sameProject(project, modpack)) {
                    continue;
                }
                converted.put(project.projectId(), project);
            }
        }
        for (InstalledProject child : children) {
            converted.merge(child.projectId(), child, LibraryModpackUnlockConverter::mergeProject);
        }
        return new Result(List.copyOf(converted.values()), children.size());
    }

    private static List<InstalledProject> childProjects(InstalledProject modpack, List<HytaleInstalledMod> installedMods) {
        Map<String, HytaleInstalledMod> modsByFile = installedModsByFile(installedMods);
        List<InstalledProjectReference> references = references(modpack);
        Map<String, List<String>> filesByReference = new LinkedHashMap<>();
        Set<String> matchedFiles = new LinkedHashSet<>();

        for (String file : modpack.files()) {
            HytaleInstalledMod mod = modsByFile.get(normalizedFileKey(file));
            InstalledProjectReference reference = matchingReference(mod, file, references);
            if (reference == null) {
                continue;
            }
            filesByReference.computeIfAbsent(referenceKey(reference), ignored -> new ArrayList<>()).add(file);
            matchedFiles.add(normalizedFileKey(file));
        }

        List<InstalledProject> children = new ArrayList<>();
        for (InstalledProjectReference reference : references) {
            List<String> files = filesByReference.get(referenceKey(reference));
            if (files != null && !files.isEmpty()) {
                children.add(referenceProject(modpack, reference, files));
            }
        }

        for (String file : modpack.files()) {
            if (matchedFiles.contains(normalizedFileKey(file))) {
                continue;
            }
            HytaleInstalledMod mod = modsByFile.get(normalizedFileKey(file));
            children.add(mod == null
                    ? fileProject(modpack, file)
                    : manifestProject(modpack, mod, file));
        }

        if (children.isEmpty()) {
            for (InstalledProjectReference reference : references) {
                children.add(referenceProject(modpack, reference, List.of()));
            }
        }
        return dedupe(children);
    }

    private static InstalledProject referenceProject(
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

    private static InstalledProject manifestProject(InstalledProject parent, HytaleInstalledMod mod, String file) {
        String id = first(mod.id(), fileProjectId(file));
        return new InstalledProject(
                id,
                id,
                first(mod.name(), id),
                "PLUGIN",
                first(mod.version(), parent.installedVersion()),
                "",
                parent.gameVersion(),
                safeInstant(parent.installedAt()),
                safeInstant(parent.updatedAt()),
                List.of(file),
                List.of(),
                List.of(),
                InstalledProject.SOURCE_LOCAL,
                InstalledProject.INSTALL_DIRECT,
                false,
                List.of()
        );
    }

    private static InstalledProject fileProject(InstalledProject parent, String file) {
        String fileName = fileName(file);
        String id = fileProjectId(file);
        return new InstalledProject(
                id,
                id,
                first(fileName.replaceFirst("(?i)\\.jar$", ""), "Installed mod"),
                "PLUGIN",
                parent.installedVersion(),
                "",
                parent.gameVersion(),
                safeInstant(parent.installedAt()),
                safeInstant(parent.updatedAt()),
                List.of(file),
                List.of(),
                List.of(),
                InstalledProject.SOURCE_LOCAL,
                InstalledProject.INSTALL_DIRECT,
                false,
                List.of()
        );
    }

    private static InstalledProject mergeProject(InstalledProject existing, InstalledProject child) {
        LinkedHashSet<String> files = new LinkedHashSet<>(existing.files());
        files.addAll(child.files());
        return new InstalledProject(
                existing.projectId(),
                first(existing.slug(), child.slug()),
                first(existing.title(), child.title()),
                first(existing.classification(), child.classification()),
                first(existing.installedVersion(), child.installedVersion()),
                first(existing.installedVersionId(), child.installedVersionId()),
                first(existing.gameVersion(), child.gameVersion()),
                safeInstant(existing.installedAt()),
                later(existing.updatedAt(), child.updatedAt()),
                List.copyOf(files),
                List.of(),
                List.of(),
                first(existing.source(), child.source(), InstalledProject.SOURCE_MODTALE),
                InstalledProject.INSTALL_DIRECT,
                false,
                List.of()
        );
    }

    private static List<InstalledProject> dedupe(List<InstalledProject> projects) {
        Map<String, InstalledProject> byProjectId = new LinkedHashMap<>();
        for (InstalledProject project : projects) {
            if (project != null && !project.projectId().isBlank()) {
                byProjectId.merge(project.projectId(), project, LibraryModpackUnlockConverter::mergeProject);
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

    private static boolean sameProject(InstalledProject left, InstalledProject right) {
        return left != null && right != null && left.projectId().equals(right.projectId());
    }

    private static String externalProjectId(InstalledProjectReference reference) {
        return "external:" + sanitize(first(
                reference.externalId(),
                reference.id(),
                reference.externalFileName(),
                reference.displayName()
        ));
    }

    private static String fileProjectId(String file) {
        return "local:" + sanitize(fileName(file).replaceFirst("(?i)\\.jar$", ""));
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
            return "mod";
        }
        return normalized.replaceAll("[^A-Za-z0-9._:-]+", "-").toLowerCase(Locale.ROOT);
    }

    private static Instant safeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private static Instant later(Instant left, Instant right) {
        Instant safeLeft = safeInstant(left);
        Instant safeRight = safeInstant(right);
        return safeRight.isAfter(safeLeft) ? safeRight : safeLeft;
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

    record Result(List<InstalledProject> projects, int convertedCount) {
        Result {
            projects = projects == null ? List.of() : List.copyOf(projects);
            convertedCount = Math.max(0, convertedCount);
        }
    }
}
