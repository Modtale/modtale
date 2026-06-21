package net.modtale.controller.user;

import net.modtale.model.dto.request.user.UpdateProfileRequest;
import net.modtale.model.dto.response.common.ResourceUrlResponse;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.media.MediaUploadService;
import net.modtale.service.project.query.SearchService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.validation.FileValidationService;
import net.modtale.service.social.SocialService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private UserController controller;
    private AccountService accountService;
    private SocialService socialService;
    private UserRepository userRepository;
    private SearchService searchService;
    private AccessControlService accessControlService;
    private FileValidationService validationService;
    private MediaUploadService mediaUploadService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        socialService = mock(SocialService.class);
        userRepository = mock(UserRepository.class);
        searchService = mock(SearchService.class);
        accessControlService = mock(AccessControlService.class);
        validationService = mock(FileValidationService.class);
        mediaUploadService = mock(MediaUploadService.class);

        controller = new UserController(
                accountService,
                socialService,
                userRepository,
                searchService,
                accessControlService,
                validationService,
                mediaUploadService
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateProfileRefreshesSessionAuthenticationWhenUsernameChanges() {
        User currentUser = user("user-1", "ada");
        currentUser.setRoles(java.util.List.of("USER"));

        User updatedUser = user("user-1", "ada-renamed");
        updatedUser.setRoles(java.util.List.of("USER"));

        UpdateProfileRequest requestPayload = new UpdateProfileRequest();
        requestPayload.setUsername("ada-renamed");
        requestPayload.setBio("new bio");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        when(accountService.requireCurrentUser(null, "updating your profile")).thenReturn(currentUser);
        when(accountService.updateUserProfile("user-1", "new bio", "ada-renamed")).thenReturn(updatedUser);

        var response = controller.updateProfile(requestPayload, request, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ada-renamed", response.getBody().getUsername());
        assertSame(updatedUser, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertTrue(request.getSession(false).getAttributeNames().hasMoreElements());
    }

    @Test
    void uploadAvatarReturnsTypedUrlResponse() throws Exception {
        User currentUser = user("user-1", "ada");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        when(accountService.requireCurrentUser(null, "uploading an avatar")).thenReturn(currentUser);
        when(mediaUploadService.uploadPublicUrl(eq(file), eq("avatars/ada"), any())).thenReturn("https://cdn.example/avatar.png");

        var response = controller.uploadAvatar(file, null);

        assertEquals(200, response.getStatusCode().value());
        ResourceUrlResponse body = response.getBody();
        assertEquals("https://cdn.example/avatar.png", body.url());
        verify(accountService).updateUserAvatar("user-1", "https://cdn.example/avatar.png");
    }

    @Test
    void followUserDelegatesUsingTheAuthenticatedUserId() {
        User currentUser = user("user-1", "ada");
        when(accountService.requireCurrentUser(null, "following a user")).thenReturn(currentUser);

        var response = controller.followUser("user-2", null);

        assertEquals(200, response.getStatusCode().value());
        verify(socialService).followUser("user-1", "user-2");
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
