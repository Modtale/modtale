package net.modtale.service.user;

import net.modtale.model.dto.response.common.ResourceUrlResponse;
import net.modtale.model.user.User;
import net.modtale.service.media.MediaUploadService;
import net.modtale.service.security.FileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationApplicationServiceTest {

    private OrganizationApplicationService service;
    private OrganizationService organizationService;
    private MediaUploadService mediaUploadService;
    private FileValidationService fileValidationService;

    @BeforeEach
    void setUp() {
        organizationService = mock(OrganizationService.class);
        mediaUploadService = mock(MediaUploadService.class);
        fileValidationService = mock(FileValidationService.class);
        service = new OrganizationApplicationService(organizationService, mediaUploadService, fileValidationService);
    }

    @Test
    void uploadOrganizationAvatarValidatesUploadsAndReturnsThePublicUrl() throws Exception {
        User currentUser = new User();
        currentUser.setId("user-1");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        when(mediaUploadService.uploadPublicUrl(eq(file), eq("avatars/org-1"), any()))
                .thenReturn("https://cdn.modtale.test/avatars/org-1/avatar.png");

        ResourceUrlResponse response = service.uploadOrganizationAvatar("org-1", file, currentUser);

        assertEquals("https://cdn.modtale.test/avatars/org-1/avatar.png", response.url());
        verify(mediaUploadService).uploadPublicUrl(eq(file), eq("avatars/org-1"), any());
        verify(organizationService).updateOrganizationAvatar("org-1", "https://cdn.modtale.test/avatars/org-1/avatar.png", currentUser);
    }
}
