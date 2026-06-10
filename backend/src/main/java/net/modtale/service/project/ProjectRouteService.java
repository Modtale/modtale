package net.modtale.service.project;

import net.modtale.model.project.Project;
import org.springframework.stereotype.Service;

@Service
public class ProjectRouteService {

    public String getProjectLink(Project project) {
        String handle = buildProjectHandle(project);
        if ("MODPACK".equals(project.getClassification().name())) return "/modpack/" + handle;
        if ("SAVE".equals(project.getClassification().name())) return "/world/" + handle;
        return "/mod/" + handle;
    }

    public String buildProjectHandle(Project project) {
        if (project == null || project.getId() == null || project.getId().isBlank()) return null;
        String base = project.getSlug();
        if (base == null || base.isBlank()) {
            base = createSlug(project.getTitle());
        }
        if (base != null && project.getId() != null) {
            if (base.equals(project.getId())) return project.getId();
            if (base.endsWith("~" + project.getId())) base = base.substring(0, base.length() - project.getId().length() - 1);
            else if (base.endsWith("-" + project.getId())) base = base.substring(0, base.length() - project.getId().length() - 1);
        }
        if (base == null || base.isBlank()) return project.getId();
        return base + "~" + project.getId();
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
