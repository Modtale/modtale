package net.modtale.controller.project;

import java.io.IOException;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectMediaOperationException;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.dto.request.project.AddGalleryVideoRequest;
import net.modtale.model.dto.request.project.RemoveGalleryImageRequest;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.project.media.ProjectMediaService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaControllerTest {

    private MediaController controller;
    private ProjectMediaService projectMediaService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        projectMediaService = mock(ProjectMediaService.class);
        accountService = mock(AccountService.class);
        controller = new MediaController(projectMediaService, accountService);
    }

    @Test
    void updateIconRequiresAnAuthenticatedUser() {
        when(accountService.requireCurrentUser(null, "updating a project icon"))
                .thenThrow(new UnauthorizedException("You need to sign in before updating a project icon."));

        assertThrows(UnauthorizedException.class, () -> controller.updateIcon("project-1", file("icon.png"), null));
        verifyNoInteractions(projectMediaService);
    }

    @Test
    void updateIconDelegatesToMetadataServiceForAuthenticatedUsers() throws Exception {
        User user = user("user-1");
        MockMultipartFile file = file("icon.png");
        when(accountService.requireCurrentUser(null, "updating a project icon")).thenReturn(user);

        var response = controller.updateIcon("project-1", file, null);

        assertEquals(200, response.getStatusCode().value());
        verify(projectMediaService).updateProjectImage("project-1", file, user, false);
    }

    @Test
    void updateBannerPropagatesDomainValidationErrors() {
        User user = user("user-1");
        MockMultipartFile file = file("banner.png");
        when(accountService.requireCurrentUser(null, "updating a project banner")).thenReturn(user);
        doThrow(new InvalidProjectRequestException("File is too large")).when(projectMediaService)
                .updateProjectImage("project-1", file, user, true);

        InvalidProjectRequestException error = assertThrows(
                InvalidProjectRequestException.class,
                () -> controller.updateBanner("project-1", file, null)
        );

        assertEquals("File is too large", error.getMessage());
    }

    @Test
    void addGalleryImageTranslatesSecurityExceptions() throws Exception {
        User user = user("user-1");
        MockMultipartFile file = file("gallery.png");
        when(accountService.requireCurrentUser(null, "uploading a gallery image")).thenReturn(user);
        doThrow(new ProjectOperationForbiddenException("Not allowed")).when(projectMediaService)
                .addGalleryImage("project-1", file, user);

        ForbiddenOperationException error = assertThrows(
                ForbiddenOperationException.class,
                () -> controller.addGalleryImage("project-1", file, null)
        );

        assertEquals("Not allowed", error.getMessage());
    }

    @Test
    void addGalleryImagePropagatesMediaOperationFailures() {
        User user = user("user-1");
        MockMultipartFile file = file("gallery.png");
        when(accountService.requireCurrentUser(null, "uploading a gallery image")).thenReturn(user);
        doThrow(new ProjectMediaOperationException("Failed to upload gallery image: boom", new IOException("boom"))).when(projectMediaService)
                .addGalleryImage("project-1", file, user);

        ProjectMediaOperationException error = assertThrows(
                ProjectMediaOperationException.class,
                () -> controller.addGalleryImage("project-1", file, null)
        );

        assertEquals("Failed to upload gallery image: boom", error.getMessage());
    }

    @Test
    void removeGalleryImageDelegatesWhenTheRequestIsValid() {
        User user = user("user-1");
        RemoveGalleryImageRequest request = new RemoveGalleryImageRequest();
        request.setImageUrl("https://cdn.example/gallery.png");
        when(accountService.requireCurrentUser(null, "removing a gallery image")).thenReturn(user);

        var response = controller.removeGalleryImage("project-1", request, null);

        assertEquals(200, response.getStatusCode().value());
        verify(projectMediaService).removeGalleryImage("project-1", "https://cdn.example/gallery.png", user);
    }

    @Test
    void addGalleryVideoDelegatesWhenTheRequestIsValid() {
        User user = user("user-1");
        Project project = new Project();
        project.setId("project-1");
        AddGalleryVideoRequest request = new AddGalleryVideoRequest();
        request.setVideoUrl("https://youtu.be/dQw4w9WgXcQ");
        when(accountService.requireCurrentUser(null, "adding a gallery video")).thenReturn(user);
        when(projectMediaService.addGalleryVideo("project-1", "https://youtu.be/dQw4w9WgXcQ", user)).thenReturn(project);

        var response = controller.addGalleryVideo("project-1", request, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("project-1", response.getBody().getId());
        verify(projectMediaService).addGalleryVideo("project-1", "https://youtu.be/dQw4w9WgXcQ", user);
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
