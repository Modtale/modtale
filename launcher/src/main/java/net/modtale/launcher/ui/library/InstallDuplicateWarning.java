package net.modtale.launcher.ui.library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.worldlist.WorldModList;

final class InstallDuplicateWarning {

    private InstallDuplicateWarning() {
    }

    static Result forSummary(List<InstalledProject> installedProjects, ProjectSummary summary) {
        if (summary == null) {
            return Result.empty();
        }
        return findDuplicates(installedProjects, List.of(new Candidate(
                summary.id(),
                summary.slug(),
                summary.title(),
                ""
        )));
    }

    static Result forProject(
            List<InstalledProject> installedProjects,
            ProjectDetail project,
            ProjectVersion version,
            List<ProjectDependency> dependencies,
            boolean includeOptionalDependencies
    ) {
        if (project == null) {
            return Result.empty();
        }
        List<Candidate> candidates = new ArrayList<>();
        candidates.add(new Candidate(
                project.id(),
                project.slug(),
                project.title(),
                version == null ? "" : version.versionNumber()
        ));
        for (ProjectDependency dependency : installableDependencies(dependencies, includeOptionalDependencies)) {
            candidates.add(new Candidate(
                    dependency.projectId(),
                    dependency.slug(),
                    first(dependency.title(), dependency.projectTitle(), dependency.projectId()),
                    dependency.versionNumber()
            ));
        }
        return findDuplicates(installedProjects, candidates);
    }

    static Result forWorldModList(List<InstalledProject> installedProjects, WorldModList list) {
        if (list == null || list.mods().isEmpty()) {
            return Result.empty();
        }
        List<Candidate> candidates = list.mods().stream()
                .filter(item -> item != null && item.downloadable())
                .map(item -> new Candidate(
                        item.projectId(),
                        item.slug(),
                        first(item.title(), item.projectId(), item.slug(), item.modId()),
                        item.versionNumber()
                ))
                .toList();
        return findDuplicates(installedProjects, candidates);
    }

    private static Result findDuplicates(List<InstalledProject> installedProjects, List<Candidate> candidates) {
        Map<String, InstalledEntry> installedByKey = installedByKey(installedProjects);
        Map<String, Duplicate> duplicates = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            for (String key : candidate.keys()) {
                InstalledEntry installed = installedByKey.get(key);
                if (installed == null) {
                    continue;
                }
                duplicates.putIfAbsent(candidate.duplicateKey(), new Duplicate(candidate, installed));
                break;
            }
        }
        return new Result(List.copyOf(duplicates.values()));
    }

    private static Map<String, InstalledEntry> installedByKey(List<InstalledProject> installedProjects) {
        Map<String, InstalledEntry> entries = new LinkedHashMap<>();
        if (installedProjects == null || installedProjects.isEmpty()) {
            return entries;
        }
        for (InstalledProject installed : installedProjects) {
            if (installed == null) {
                continue;
            }
            InstalledEntry projectEntry = new InstalledEntry(
                    installed.projectId(),
                    installed.slug(),
                    first(installed.title(), installed.slug(), installed.projectId(), "Installed mod"),
                    installed.installedVersion()
            );
            putEntry(entries, projectEntry);
            for (InstalledProjectReference reference : bundledReferences(installed)) {
                InstalledEntry referenceEntry = new InstalledEntry(
                        reference.projectId(),
                        reference.slug(),
                        first(reference.displayName(), reference.slug(), reference.projectId(), "Bundled mod"),
                        reference.versionNumber()
                );
                putEntry(entries, referenceEntry);
            }
        }
        return entries;
    }

    private static void putEntry(Map<String, InstalledEntry> entries, InstalledEntry entry) {
        for (String key : entry.keys()) {
            entries.putIfAbsent(key, entry);
        }
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
                id, id, "", id, "", "", "", "MODTALE", "", "", "", "", "", "", null, null
        )));
        return references;
    }

    private static List<ProjectDependency> installableDependencies(
            List<ProjectDependency> dependencies,
            boolean includeOptionalDependencies
    ) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        return dependencies.stream()
                .filter(dependency -> dependency != null
                        && !dependency.isExternal()
                        && !dependency.isEmbedded()
                        && !isBlank(dependency.projectId())
                        && (includeOptionalDependencies || !dependency.isOptional()))
                .toList();
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Set<String> keys(String projectId, String slug) {
        Set<String> keys = new LinkedHashSet<>();
        addKey(keys, projectId);
        addKey(keys, slug);
        return keys;
    }

    private static void addKey(Set<String> keys, String value) {
        String key = normalizeKey(value);
        if (!key.isBlank()) {
            keys.add(key);
        }
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record Result(List<Duplicate> duplicates) {
        Result {
            duplicates = duplicates == null ? List.of() : List.copyOf(duplicates);
        }

        static Result empty() {
            return new Result(List.of());
        }

        boolean hasDuplicates() {
            return !duplicates.isEmpty();
        }

        int count() {
            return duplicates.size();
        }

        String summary() {
            List<String> names = duplicates.stream()
                    .map(Duplicate::displayName)
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .limit(4)
                    .toList();
            int remaining = duplicates.size() - names.size();
            return String.join(", ", names) + (remaining > 0 ? ", +" + remaining + " more" : "");
        }
    }

    record Duplicate(Candidate candidate, InstalledEntry installed) {
        String displayName() {
            return first(candidate.title(), installed.title(), candidate.projectId(), candidate.slug());
        }
    }

    private record Candidate(String projectId, String slug, String title, String version) {
        private Candidate {
            projectId = first(projectId);
            slug = first(slug);
            title = first(title, projectId, slug);
            version = first(version);
        }

        private Set<String> keys() {
            return InstallDuplicateWarning.keys(projectId, slug);
        }

        private String duplicateKey() {
            return first(projectId, slug, title).toLowerCase(Locale.ROOT);
        }
    }

    private record InstalledEntry(String projectId, String slug, String title, String version) {
        private InstalledEntry {
            projectId = first(projectId);
            slug = first(slug);
            title = first(title, projectId, slug);
            version = first(version);
        }

        private Set<String> keys() {
            return InstallDuplicateWarning.keys(projectId, slug);
        }
    }
}
