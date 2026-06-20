package net.modtale.launcher.model.install;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import net.modtale.launcher.model.project.ProjectClassification;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledProject(
        String projectId,
        String slug,
        String title,
        String classification,
        String installedVersion,
        String installedVersionId,
        String gameVersion,
        Instant installedAt,
        Instant updatedAt,
        List<String> files,
        List<String> dependencyProjectIds,
        List<String> externalDependencies,
        String source,
        String installType,
        boolean modpackUnlocked,
        List<InstalledProjectReference> bundledProjects
) {
    public static final String SOURCE_MODTALE = "MODTALE";
    public static final String SOURCE_LOCAL = "LOCAL";
    public static final String INSTALL_DIRECT = "DIRECT";
    public static final String INSTALL_BUNDLE = "BUNDLE";
    public static final String INSTALL_MODPACK = "MODPACK";

    public InstalledProject {
        projectId = value(projectId);
        slug = value(slug);
        title = value(title);
        classification = value(classification);
        installedVersion = value(installedVersion);
        installedVersionId = value(installedVersionId);
        gameVersion = value(gameVersion);
        installedAt = installedAt == null ? Instant.EPOCH : installedAt;
        updatedAt = updatedAt == null ? installedAt : updatedAt;
        files = files == null ? List.of() : List.copyOf(files);
        dependencyProjectIds = dependencyProjectIds == null ? List.of() : List.copyOf(dependencyProjectIds);
        externalDependencies = externalDependencies == null ? List.of() : List.copyOf(externalDependencies);
        source = value(source, SOURCE_MODTALE);
        bundledProjects = bundledProjects == null ? List.of() : List.copyOf(bundledProjects);
        installType = value(installType, defaultInstallType(classification, bundledProjects));
    }

    public InstalledProject(
            String projectId,
            String slug,
            String title,
            String classification,
            String installedVersion,
            String installedVersionId,
            String gameVersion,
            Instant installedAt,
            Instant updatedAt,
            List<String> files,
            List<String> dependencyProjectIds,
            List<String> externalDependencies
    ) {
        this(projectId, slug, title, classification, installedVersion, installedVersionId, gameVersion,
                installedAt, updatedAt, files, dependencyProjectIds, externalDependencies,
                SOURCE_MODTALE, "", false, List.of());
    }

    public InstalledProject withModpackUnlocked(boolean unlocked) {
        return new InstalledProject(projectId, slug, title, classification, installedVersion, installedVersionId,
                gameVersion, installedAt, updatedAt, files, dependencyProjectIds, externalDependencies,
                source, installType, unlocked, bundledProjects);
    }

    public List<String> bundledModtaleProjectIds() {
        if (!bundledProjects.isEmpty()) {
            return bundledProjects.stream()
                    .filter(InstalledProjectReference::isModtaleProject)
                    .map(InstalledProjectReference::projectId)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
        }
        return dependencyProjectIds;
    }

    public boolean isModpack() {
        return ProjectClassification.isModpack(classification);
    }

    private static String defaultInstallType(String classification, List<InstalledProjectReference> bundledProjects) {
        if (ProjectClassification.isModpack(classification)) {
            return INSTALL_MODPACK;
        }
        if (bundledProjects != null && !bundledProjects.isEmpty()) {
            return INSTALL_BUNDLE;
        }
        return INSTALL_DIRECT;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(String value, String fallback) {
        String normalized = value(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
