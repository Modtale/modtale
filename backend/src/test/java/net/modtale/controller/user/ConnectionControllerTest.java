package net.modtale.controller.user;

import java.util.List;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.dto.user.GitRepositoryDTO;
import net.modtale.model.user.GitRepository;
import net.modtale.model.user.User;
import net.modtale.service.user.account.AccountService;
import net.modtale.service.user.connection.ConnectionSessionService;
import net.modtale.service.user.connection.GithubService;
import net.modtale.service.user.connection.GitlabService;
import net.modtale.service.user.organization.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionControllerTest {

    private ConnectionController controller;
    private AccountService accountService;
    private OrganizationService organizationService;
    private GithubService githubService;
    private GitlabService gitlabService;
    private ConnectionSessionService connectionSessionService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        organizationService = mock(OrganizationService.class);
        githubService = mock(GithubService.class);
        gitlabService = mock(GitlabService.class);
        connectionSessionService = mock(ConnectionSessionService.class);
        controller = new ConnectionController(accountService, organizationService, githubService, gitlabService, connectionSessionService);
    }

    @Test
    void toggleVisibilityUsesCurrentUserId() {
        User user = user("user-1");
        when(accountService.requireCurrentUser("changing connection visibility")).thenReturn(user);

        var response = controller.toggleVisibility("github");

        assertEquals(200, response.getStatusCode().value());
        verify(accountService).toggleConnectionVisibility("user-1", "github");
    }

    @Test
    void getGithubReposUsesAuthorizedClientTokenWhenStoredTokenIsMissing() {
        User user = user("user-1");
        Authentication authentication = new UsernamePasswordAuthenticationToken("ada", null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        GitRepository repo = new GitRepository();
        repo.setName("modtale/sky-tools");
        repo.setUrl("https://github.com/modtale/sky-tools");
        repo.setDescription("Sky tools");

        when(accountService.requireCurrentUser("loading GitHub repositories")).thenReturn(user);
        when(connectionSessionService.loadAuthorizedClientAccessToken("github", authentication, request, "We couldn't access your GitHub connection session."))
                .thenReturn("gh-token");
        when(githubService.getUserRepos("gh-token")).thenReturn(List.of(repo));

        var result = controller.getGithubRepos(authentication, request, response);

        assertEquals(200, result.getStatusCode().value());
        GitRepositoryDTO dto = result.getBody().getFirst();
        assertEquals("modtale/sky-tools", dto.name());
        assertEquals("https://github.com/modtale/sky-tools", dto.url());
    }

    @Test
    void getGithubReposRejectsUsersWithoutAnyGithubToken() {
        User user = user("user-1");
        Authentication authentication = new UsernamePasswordAuthenticationToken("ada", null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accountService.requireCurrentUser("loading GitHub repositories")).thenReturn(user);
        when(connectionSessionService.loadAuthorizedClientAccessToken("github", authentication, request, "We couldn't access your GitHub connection session."))
                .thenReturn(null);

        assertThrows(
                UnauthorizedException.class,
                () -> controller.getGithubRepos(authentication, request, response)
        );
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
