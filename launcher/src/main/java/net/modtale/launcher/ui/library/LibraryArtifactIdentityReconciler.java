package net.modtale.launcher.ui.library;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ArtifactIdentity;

final class LibraryArtifactIdentityReconciler {
    private LibraryArtifactIdentityReconciler() {}

    static Result reconcile(List<InstalledProject> installed, List<ArtifactIdentity.Match> matches) {
        Map<String, ArtifactIdentity.Match> byFile = new LinkedHashMap<>();
        for (ArtifactIdentity.Match match : matches == null ? List.<ArtifactIdentity.Match>of() : matches) {
            if (match != null && match.confidence() >= 90) byFile.put(normalize(match.key()), match);
        }
        List<InstalledProject> output = new ArrayList<>();
        int resolved = 0;
        for (InstalledProject project : installed == null ? List.<InstalledProject>of() : installed) {
            ArtifactIdentity.Match match = project.files().stream().map(LibraryArtifactIdentityReconciler::normalize)
                    .map(byFile::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            boolean sameIdentity = match != null && match.projectId().equals(project.projectId())
                    && match.source().equalsIgnoreCase(project.source());
            boolean sameOrUnknownVersion = match != null && (match.versionId() == null || match.versionId().isBlank()
                    || match.versionId().equals(project.installedVersionId()));
            if (match == null || (sameIdentity && sameOrUnknownVersion)) {
                output.add(project);
                continue;
            }
            InstalledProject replacement = new InstalledProject(match.projectId(), first(match.slug(), project.slug()),
                    first(match.title(), project.title()), first(match.classification(), project.classification()),
                    first(project.installedVersion(), match.versionNumber()), first(match.versionId(), project.installedVersionId()),
                    project.gameVersion(), project.installedAt(), project.updatedAt(), project.files(), project.dependencyProjectIds(),
                    project.externalDependencies(), match.source(), project.installType(), project.modpackUnlocked(),
                    project.bundledProjects());
            int existingIndex = indexOf(output, replacement.projectId());
            if (existingIndex >= 0) output.set(existingIndex, mergeFiles(output.get(existingIndex), replacement.files()));
            else output.add(replacement);
            resolved++;
        }
        Map<String, InstalledProject> unique = new LinkedHashMap<>();
        for (InstalledProject project : output) {
            InstalledProject existing = unique.get(project.projectId());
            unique.put(project.projectId(), existing == null ? project : mergeFiles(existing, project.files()));
        }
        return new Result(List.copyOf(unique.values()), resolved);
    }

    private static int indexOf(List<InstalledProject> projects, String id) {
        for (int i = 0; i < projects.size(); i++) if (projects.get(i).projectId().equals(id)) return i;
        return -1;
    }

    private static InstalledProject mergeFiles(InstalledProject project, List<String> files) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(project.files());
        merged.addAll(files);
        return new InstalledProject(project.projectId(), project.slug(), project.title(), project.classification(),
                project.installedVersion(), project.installedVersionId(), project.gameVersion(), project.installedAt(),
                project.updatedAt(), List.copyOf(merged), project.dependencyProjectIds(), project.externalDependencies(),
                project.source(), project.installType(), project.modpackUnlocked(), project.bundledProjects());
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) return "";
        try { return Path.of(path).toAbsolutePath().normalize().toString(); }
        catch (RuntimeException ignored) { return path.trim(); }
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    record Result(List<InstalledProject> projects, int resolvedCount) {}
}
