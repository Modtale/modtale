package net.modtale.launcher.ui.project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectMeta;

final class ProjectDependencyMetadata {
    private ProjectDependencyMetadata() {}

    static String lookupKey(ProjectDependency dependency) {
        if (dependency == null) return "";
        if (dependency.isCurseForge()) {
            String id = first(dependency.externalId(), dependency.projectId());
            if (id.startsWith("curseforge:")) id = id.substring("curseforge:".length());
            return id.matches("[1-9][0-9]{0,17}") ? "curseforge:" + id : "";
        }
        if (dependency.isExternal()) return "";
        return first(dependency.projectId(), dependency.slug());
    }

    static String title(ProjectDependency dependency, ProjectMeta meta) {
        return first(meta == null ? null : meta.title(), dependency.title(), dependency.projectTitle(),
                dependency.slug(), dependency.projectId(), dependency.externalId(),
                dependency.isExternal() ? "External dependency" : "Unknown dependency");
    }

    static String icon(ProjectDependency dependency, ProjectMeta meta) {
        return first(meta == null ? null : meta.icon(), dependency.icon());
    }

    static Map<String, ProjectMeta> load(List<String> keys,
            Function<List<String>, Map<String, ProjectMeta>> batchLoader,
            Function<String, ProjectMeta> singleLoader) {
        Map<String, ProjectMeta> result = new LinkedHashMap<>();
        try {
            Map<String, ProjectMeta> batch = batchLoader.apply(keys);
            if (batch != null) result.putAll(batch);
        } catch (RuntimeException unavailable) {
            // A failed batch must not prevent individual references from resolving.
        }
        for (String key : keys) {
            if (result.get(key) != null) continue;
            try {
                ProjectMeta meta = singleLoader.apply(key);
                if (meta != null) result.put(key, meta);
            } catch (RuntimeException unavailable) {
                // Keep inline metadata and metadata resolved for other dependencies.
            }
        }
        return result;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }
}
