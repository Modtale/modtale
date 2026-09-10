package net.modtale.config.db;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bson.Document;

/** Keeps dependency references readable across the modId/projectId storage transition. */
final class ProjectDependencyDocumentCompatibility {
    private ProjectDependencyDocumentCompatibility() {}

    static int normalizeProject(Document project) {
        int changes = 0;
        Object children = project.get("childProjectIds");
        if (!(children instanceof List<?>)) children = project.get("modIds");
        if (children instanceof List<?>) {
            if (put(project, "childProjectIds", children) | put(project, "modIds", children)) changes++;
        }
        if (!(project.get("versions") instanceof List<?> versions)) return changes;
        for (Object value : versions) {
            if (!(value instanceof Document version)
                    || !(version.get("dependencies") instanceof List<?> dependencies)) continue;
            for (Object dependency : dependencies) {
                if (dependency instanceof Document document && normalizeDependency(document)) changes++;
            }
        }
        return changes;
    }

    private static boolean normalizeDependency(Document dependency) {
        boolean changed = false;
        Object projectId = first(dependency, "projectId", "modId");
        Object projectTitle = first(dependency, "projectTitle", "modTitle");
        if (projectId != null) {
            changed |= put(dependency, "projectId", projectId);
            changed |= put(dependency, "modId", projectId);
        }
        if (projectTitle != null) {
            changed |= put(dependency, "projectTitle", projectTitle);
            changed |= put(dependency, "modTitle", projectTitle);
        }
        if (first(dependency, "id", "id") == null) {
            changed |= put(dependency, "id", UUID.randomUUID().toString());
        }
        if (first(dependency, "source", "source") == null) changed |= put(dependency, "source", "MODTALE");
        String type = dependency.getString("dependencyType");
        if (type == null || type.isBlank()) {
            type = Boolean.TRUE.equals(dependency.get("isEmbedded")) ? "EMBEDDED"
                    : Boolean.TRUE.equals(dependency.get("isOptional")) ? "OPTIONAL" : "REQUIRED";
            changed |= put(dependency, "dependencyType", type);
        }
        changed |= put(dependency, "isOptional", "OPTIONAL".equals(type));
        changed |= put(dependency, "isEmbedded", "EMBEDDED".equals(type));
        if (!dependency.containsKey("hytaleProjectConfirmed")) {
            changed |= put(dependency, "hytaleProjectConfirmed", false);
        }
        return changed;
    }

    private static Object first(Document document, String primary, String fallback) {
        Object value = document.get(primary);
        if (value == null || value instanceof String text && text.isBlank()) value = document.get(fallback);
        return value instanceof String text && text.isBlank() ? null : value;
    }

    private static boolean put(Document document, String key, Object value) {
        if (document.containsKey(key) && Objects.equals(document.get(key), value)) return false;
        document.put(key, value);
        return true;
    }
}
