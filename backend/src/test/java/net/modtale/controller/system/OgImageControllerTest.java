package net.modtale.controller.system;

import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class OgImageControllerTest {

    private ProjectService projectService;
    private OgImageController controller;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        controller = new OgImageController(projectService);
    }

    @Test
    void generateOgImageReturnsNotFoundWhenTheProjectDoesNotExist() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/og/project/project-1.png");

        org.mockito.Mockito.when(projectService.getPublicProjectByRouteKey("project-1")).thenReturn(null);
        var response = controller.generateOgImage("project-1", null, request);

        assertEquals(404, response.getStatusCode().value());
    }
}
