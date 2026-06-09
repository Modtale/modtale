package net.modtale.controller.project;

import net.modtale.model.dto.request.project.RemoveGalleryImageRequest;
import net.modtale.model.user.User;
import net.modtale.service.project.MetadataService;
import net.modtale.service.user.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaControllerTest {

    private MediaController controller;
    private MetadataService metadataService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        controller = new MediaController();
        metadataService = mock(MetadataService.class);
        accountService = mock(AccountService.class);

        ReflectionTestUtils.setField(controller, "metadataService", metadataService);
        ReflectionTestUtils.setField(controller, "accountService", accountService);
    }

    @Test
    void updateIconRequiresAnAuthenticatedUser() {
        when(accountService.getCurrentUser()).thenReturn(null);

        var response = controller.updateIcon("project-1", file("icon.png"));

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(metadataService);
    }

    @Test
    void updateIconDelegatesToMetadataServiceForAuthenticatedUsers() throws Exception {
        User user = user("user-1");
        MockMultipartFile file = file("icon.png");
        when(accountService.getCurrentUser()).thenReturn(user);

        var response = controller.updateIcon("project-1", file);

        assertEquals(200, response.getStatusCode().value());
        verify(metadataService).updateProjectImage("project-1", file, user, false);
    }

    @Test
    void updateBannerMapsValidationErrorsToBadRequests() throws Exception {
        User user = user("user-1");
        MockMultipartFile file = file("banner.png");
        when(accountService.getCurrentUser()).thenReturn(user);
        doThrow(new IllegalStateException("File is too large")).when(metadataService)
                .updateProjectImage("project-1", file, user, true);

        var response = controller.updateBanner("project-1", file);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("File is too large", response.getBody());
    }

    @Test
    void addGalleryImageMapsSecurityExceptionsToForbidden() throws Exception {
        User user = user("user-1");
        MockMultipartFile file = file("gallery.png");
        when(accountService.getCurrentUser()).thenReturn(user);
        doThrow(new SecurityException("Not allowed")).when(metadataService)
                .addGalleryImage("project-1", file, user);

        var response = controller.addGalleryImage("project-1", file);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("Not allowed", response.getBody());
    }

    @Test
    void addGalleryImageWrapsUnexpectedFailuresWithTheUploadFallbackMessage() throws Exception {
        User user = user("user-1");
        MockMultipartFile file = file("gallery.png");
        when(accountService.getCurrentUser()).thenReturn(user);
        doThrow(new RuntimeException("boom")).when(metadataService)
                .addGalleryImage("project-1", file, user);

        var response = controller.addGalleryImage("project-1", file);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Failed to upload gallery image: boom", response.getBody());
    }

    @Test
    void removeGalleryImageRequiresAnImageUrl() {
        User user = user("user-1");
        when(accountService.getCurrentUser()).thenReturn(user);

        var response = controller.removeGalleryImage("project-1", new RemoveGalleryImageRequest());

        assertEquals(400, response.getStatusCode().value());
        assertEquals("imageUrl is required", response.getBody());
    }

    @Test
    void removeGalleryImageDelegatesWhenTheRequestIsValid() {
        User user = user("user-1");
        RemoveGalleryImageRequest request = new RemoveGalleryImageRequest();
        request.setImageUrl("https://cdn.example/gallery.png");
        when(accountService.getCurrentUser()).thenReturn(user);

        var response = controller.removeGalleryImage("project-1", request);

        assertEquals(200, response.getStatusCode().value());
        verify(metadataService).removeGalleryImage("project-1", "https://cdn.example/gallery.png", user);
    }

    private static MockMultipartFile file(String filename) {
        return new MockMultipartFile("file", filename, "image/png", new byte[]{1, 2, 3});
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
