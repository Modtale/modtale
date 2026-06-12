package net.modtale.service.project;

import net.modtale.model.project.Project;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProjectRouteService {

    private static final Pattern LEGACY_UUID_SUFFIX = Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$", Pattern.CASE_INSENSITIVE);

    public String getProjectLink(Project project) {
        String handle = buildProjectHandle(project);
        if ("MODPACK".equals(project.getClassification().name())) return "/modpack/" + handle;
        if ("SAVE".equals(project.getClassification().name())) return "/world/" + handle;
        return "/mod/" + handle;
    }

    public String buildProjectHandle(Project project) {
        if (project == null || project.getId() == null || project.getId().isBlank()) return null;
        String customSlug = project.getSlug();
        if (customSlug != null && !customSlug.isBlank()) {
            return customSlug.trim();
        }
        String base = createSlug(project.getTitle());
        if (base == null || base.isBlank()) return project.getId();
        return base + "~" + project.getId();
    }

    public String extractProjectId(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        int tildeIndex = normalized.lastIndexOf('~');
        if (tildeIndex >= 0 && tildeIndex < normalized.length() - 1) {
            return normalized.substring(tildeIndex + 1);
        }

        Matcher matcher = LEGACY_UUID_SUFFIX.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return normalized;
    }

    public boolean hasExplicitProjectHandle(String routeKey) {
        String extractedId = extractProjectId(routeKey);
        if (extractedId == null) return false;
        return !extractedId.equals(routeKey == null ? null : routeKey.trim());
    }

    private String createSlug(String title) {
        if (title == null) return null;
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 30) slug = slug.substring(0, 30);
        return slug;
    }
}
