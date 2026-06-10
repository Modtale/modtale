package net.modtale.controller.project;

import net.modtale.model.dto.request.project.CommentRequest;
import net.modtale.model.user.User;
import net.modtale.service.social.SocialService;
import net.modtale.service.user.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialControllerTest {

    private SocialController controller;
    private SocialService socialService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        socialService = mock(SocialService.class);
        accountService = mock(AccountService.class);
        controller = new SocialController(socialService, accountService);
    }

    @Test
    void toggleFavoriteDelegatesUsingTheCurrentUser() {
        User user = user("user-1");
        when(accountService.requireCurrentUser("favoriting a project")).thenReturn(user);

        var response = controller.toggleFavorite("project-1");

        assertEquals(200, response.getStatusCode().value());
        verify(socialService).toggleFavorite("project-1", "user-1");
    }

    @Test
    void voteCommentPassesTheVoteDirectionToTheService() {
        User user = user("user-1");
        when(accountService.requireCurrentUser("voting on comments")).thenReturn(user);

        var response = controller.voteComment("project-1", "comment-1", true);

        assertEquals(200, response.getStatusCode().value());
        verify(socialService).voteComment("project-1", "comment-1", "user-1", true);
    }

    @Test
    void addCommentUsesTheRequestPayloadContent() {
        User user = user("user-1");
        CommentRequest request = new CommentRequest();
        request.setContent("Nice work!");

        when(accountService.requireCurrentUser("posting a comment")).thenReturn(user);

        var response = controller.addComment("project-1", request);

        assertEquals(200, response.getStatusCode().value());
        verify(socialService).addComment("project-1", "user-1", "Nice work!");
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
