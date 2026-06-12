package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRouteServiceTest {

    private final ProjectRouteService service = new ProjectRouteService();

    @Test
    void getProjectLinkUsesCustomSlugVerbatim() {
        Project project = new Project();
        project.setId("project-1");
        project.setSlug("levelingcore");
        project.setTitle("LevelingCore");
        project.setClassification(ProjectClassification.PLUGIN);

        assertEquals("/mod/levelingcore", service.getProjectLink(project));
    }

    @Test
    void getProjectLinkUsesModpackPrefix() {
        Project project = new Project();
        project.setId("project-2");
        project.setSlug("mega-pack");
        project.setTitle("Mega Pack");
        project.setClassification(ProjectClassification.MODPACK);

        assertEquals("/modpack/mega-pack", service.getProjectLink(project));
    }

    @Test
    void getProjectLinkFallsBackToGeneratedHandleWhenSlugIsMissing() {
        Project project = new Project();
        project.setId("project-1");
        project.setTitle("Sky Tools");
        project.setClassification(ProjectClassification.SAVE);

        assertEquals("/world/sky-tools~project-1", service.getProjectLink(project));
    }

    @Test
    void extractProjectIdSupportsLegacyHandlesAndUuidSuffixes() {
        assertEquals("project-1", service.extractProjectId("sky-tools~project-1"));
        assertTrue(service.hasExplicitProjectHandle("sky-tools~project-1"));
        assertEquals("123e4567-e89b-12d3-a456-426614174000", service.extractProjectId("sky-tools-123e4567-e89b-12d3-a456-426614174000"));
    }
}
