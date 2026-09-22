package net.modtale.controller.system;

import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class OgImageControllerTest {

    @Test
    void rejectsOversizedRasterDimensionsBeforeAllocatingTheImage() throws Exception {
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        byte[] png = output.toByteArray();
        java.nio.ByteBuffer.wrap(png).putInt(16, 50_000).putInt(20, 50_000);
        var crc = new java.util.zip.CRC32();
        crc.update(png, 12, 17);
        java.nio.ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
        org.junit.jupiter.api.Assertions.assertNull(org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                controller, "decodeFetchedImage", png, "image/png", "https://cdn.modtale.net/image.png"));
    }

    private ProjectService projectService;
    private OgImageController controller;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        controller = new OgImageController(projectService, new net.modtale.config.properties.AppR2Properties(null, null, null, null, null));
    }

    @Test
    void generateOgImageReturnsNotFoundWhenTheProjectDoesNotExist() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/og/project/project-1.png");

        org.mockito.Mockito.when(projectService.getPublicProjectByRouteKey("project-1")).thenReturn(null);
        var response = controller.generateOgImage("project-1", null, request);

        assertEquals(404, response.getStatusCode().value());
    }
}
