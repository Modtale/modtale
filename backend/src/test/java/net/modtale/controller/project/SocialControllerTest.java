package net.modtale.controller.project;

import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.model.dto.request.project.CommentRequest;
import net.modtale.model.user.User;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.social.SocialService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SocialControllerTest {

    private SocialController controller;
    private SocialService socialService;
    private AccountService accountService;
    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        socialService = mock(SocialService.class);
        accountService = mock(AccountService.class);
        accessControlService = mock(AccessControlService.class);
        controller = new SocialController(socialService, accountService, accessControlService);
    }

    @Test
    void toggleFavoriteDelegatesUsingTheCurrentUser() {
        User user = user("user-1");
        when(accountService.requireCurrentUser((Authentication) null, "favoriting a project")).thenReturn(user);

        var response = controller.toggleFavorite("project-1", null);

        assertEquals(200, response.getStatusCode().value());
        verify(socialService).toggleFavorite("project-1", "user-1");
    }

    @Test
    void voteCommentPassesTheVoteDirectionToTheService() {
        User user = user("user-1");
        when(accountService.requireCurrentUser((Authentication) null, "voting on comments")).thenReturn(user);

        var response = controller.voteComment("project-1", "comment-1", true, null);

        assertEquals(200, response.getStatusCode().value());
        verify(socialService).voteComment("project-1", "comment-1", "user-1", true);
    }

    @Test
    void addCommentUsesTheRequestPayloadContent() {
        User user = user("user-1");
        CommentRequest request = new CommentRequest();
        request.setContent("Nice work!");

        when(accountService.requireCurrentUser((Authentication) null, "posting a comment")).thenReturn(user);

        var response = controller.addComment("project-1", request, null);

        assertEquals(200, response.getStatusCode().value());
        verify(socialService).addComment("project-1", "user-1", "Nice work!");
    }

    @Test
    void addCommentRejectsApiKeyRequests() {
        Authentication authentication = mock(Authentication.class);
        CommentRequest request = new CommentRequest();
        request.setContent("Nice work!");

        when(accessControlService.isApiKey(authentication)).thenReturn(true);

        assertThrows(ApiKeyOperationForbiddenException.class,
                () -> controller.addComment("project-1", request, authentication));

        verifyNoInteractions(socialService);
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
