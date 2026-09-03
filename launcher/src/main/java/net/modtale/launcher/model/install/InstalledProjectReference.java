package net.modtale.launcher.model.install;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.modtale.launcher.model.project.ProjectDependency;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledProjectReference(
        String id,
        String projectId,
        String slug,
        String title,
        String classification,
        String versionNumber,
        String dependencyType,
        String source,
        String externalId,
        String externalUrl,
        String externalFileUrl,
        String externalFileName,
        String cachedFileUrl,
        String icon,
        Boolean optional,
        Boolean embedded
) {
    public InstalledProjectReference {
        id = value(id);
        projectId = value(projectId);
        slug = value(slug);
        title = value(title);
        classification = value(classification);
        versionNumber = value(versionNumber);
        dependencyType = value(dependencyType);
        source = value(source);
        externalId = value(externalId);
        externalUrl = value(externalUrl);
        externalFileUrl = value(externalFileUrl);
        externalFileName = value(externalFileName);
        cachedFileUrl = value(cachedFileUrl);
        icon = value(icon);
    }

    public static InstalledProjectReference fromDependency(ProjectDependency dependency) {
        if (dependency == null) {
            return empty();
        }
        return new InstalledProjectReference(
                dependency.id(),
                dependency.projectId(),
                dependency.slug(),
                displayTitle(dependency),
                dependency.classification(),
                dependency.versionNumber(),
                dependency.dependencyType(),
                dependency.source(),
                dependency.externalId(),
                dependency.externalUrl(),
                dependency.externalFileUrl(),
                dependency.externalFileName(),
                dependency.cachedFileUrl(),
                dependency.icon(),
                dependency.optional(),
                dependency.embedded()
        );
    }

    public ProjectDependency toDependency() {
        return new ProjectDependency(
                id,
                projectId,
                title,
                versionNumber,
                dependencyType,
                source,
                externalId,
                externalUrl,
                externalFileUrl,
                externalFileName,
                cachedFileUrl,
                false,
                icon,
                title,
                classification,
                slug,
                optional,
                embedded
        );
    }

    public boolean isModtaleProject() {
        return !projectId.isBlank() && (source.isBlank() || "MODTALE".equalsIgnoreCase(source));
    }

    public String routeKey() {
        return !slug.isBlank() ? slug : projectId;
    }

    public String displayName() {
        if (!title.isBlank()) {
            return title;
        }
        if (!externalId.isBlank()) {
            return source.isBlank() ? externalId : source + ":" + externalId;
        }
        return !projectId.isBlank() ? projectId : value(id, "Bundled project");
    }

    private static InstalledProjectReference empty() {
        return new InstalledProjectReference("", "", "", "", "", "", "", "", "", "", "", "", "", "", null, null);
    }

    private static String displayTitle(ProjectDependency dependency) {
        if (dependency.title() != null && !dependency.title().isBlank()) {
            return dependency.title();
        }
        return dependency.projectTitle();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(String value, String fallback) {
        String normalized = value(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
