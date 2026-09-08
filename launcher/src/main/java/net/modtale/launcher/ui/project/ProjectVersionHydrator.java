package net.modtale.launcher.ui.project;

import java.util.List;
import java.util.function.Function;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;

final class ProjectVersionHydrator {

    private ProjectVersionHydrator() {
    }

    static ProjectDetail hydrate(ProjectDetail detail, ProjectSummary summary, Function<String, List<ProjectVersion>> versionLoader) {
        return hydrate(detail, summary, versionLoader, false);
    }

    static ProjectDetail hydrateOrThrow(ProjectDetail detail, ProjectSummary summary, Function<String, List<ProjectVersion>> versionLoader) {
        return hydrate(detail, summary, versionLoader, true);
    }

    private static ProjectDetail hydrate(
            ProjectDetail detail,
            ProjectSummary summary,
            Function<String, List<ProjectVersion>> versionLoader,
            boolean throwWhenNoFallback
    ) {
        if (detail == null || !detail.versions().isEmpty()) {
            return detail;
        }

        List<ProjectVersion> summaryVersions = summary == null ? List.of() : summary.versions();
        try {
            String routeKey = routeKey(detail, summary);
            if (!routeKey.isBlank()) {
                List<ProjectVersion> loadedVersions = safeVersions(versionLoader.apply(routeKey));
                if (!loadedVersions.isEmpty()) {
                    return detail.withVersions(loadedVersions);
                }
            }
        } catch (RuntimeException error) {
            if (throwWhenNoFallback && summaryVersions.isEmpty()) {
                throw error;
            }
        }

        return summaryVersions.isEmpty() ? detail : detail.withVersions(summaryVersions);
    }

    private static String routeKey(ProjectDetail detail, ProjectSummary summary) {
        String detailKey = detail == null ? "" : value(detail.routeKey());
        if (!detailKey.isBlank()) {
            return detailKey;
        }
        return summary == null ? "" : value(summary.routeKey());
    }

    private static List<ProjectVersion> safeVersions(List<ProjectVersion> versions) {
        return versions == null ? List.of() : versions;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
