package net.modtale.service.user.connection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedAccountMutationServiceTest {

    private ConnectedAccountMutationService service;

    @BeforeEach
    void setUp() {
        service = new ConnectedAccountMutationService();
    }

    @Test
    void linkProviderStoresTokensAndUpsertsSingleAccountPerProvider() {
        User user = new User();
        user.setConnectedAccounts(new ArrayList<>(List.of(
                new User.ConnectedAccount(OAuthProvider.GITHUB, "old-id", "old", "old-url", false)
        )));

        service.linkProvider(user, OAuthProvider.GITHUB, "new-id", "new", "new-url", true, "gh-token");

        assertEquals("gh-token", user.getGithubAccessToken());
        assertEquals(1, user.getConnectedAccounts().size());
        User.ConnectedAccount account = user.getConnectedAccounts().getFirst();
        assertEquals("new-id", account.getProviderId());
        assertEquals("new", account.getUsername());
        assertTrue(account.isVisible());
    }

    @Test
    void updateProviderTokensHandlesGitlabRefreshAndExpiryWithoutOverwritingNullFields() {
        User user = new User();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        assertTrue(service.updateProviderTokens(user, OAuthProvider.GITLAB, "access", "refresh", expiresAt));
        assertEquals("access", user.getGitlabAccessToken());
        assertEquals("refresh", user.getGitlabRefreshToken());
        assertEquals(expiresAt, user.getGitlabTokenExpiresAt());

        assertTrue(service.updateProviderTokens(user, OAuthProvider.GITLAB, "new-access", null, null));
        assertEquals("new-access", user.getGitlabAccessToken());
        assertEquals("refresh", user.getGitlabRefreshToken());
        assertEquals(expiresAt, user.getGitlabTokenExpiresAt());
    }

    @Test
    void unlinkRemovesAccountsAndClearsProviderTokens() {
        User user = new User();
        user.setGithubAccessToken("gh-token");
        user.setGitlabAccessToken("gl-token");
        user.setGitlabRefreshToken("gl-refresh");
        user.setGitlabTokenExpiresAt(LocalDateTime.now());
        user.setConnectedAccounts(new ArrayList<>(List.of(
                new User.ConnectedAccount(OAuthProvider.GITHUB, "gh", "gh", "gh-url", true),
                new User.ConnectedAccount(OAuthProvider.GITLAB, "gl", "gl", "gl-url", true)
        )));

        assertTrue(service.unlink(user, OAuthProvider.GITHUB));
        assertNull(user.getGithubAccessToken());
        assertEquals(1, user.getConnectedAccounts().size());

        assertTrue(service.unlink(user, OAuthProvider.GITLAB));
        assertNull(user.getGitlabAccessToken());
        assertNull(user.getGitlabRefreshToken());
        assertNull(user.getGitlabTokenExpiresAt());
        assertTrue(user.getConnectedAccounts().isEmpty());
        assertFalse(service.unlink(user, OAuthProvider.GOOGLE));
    }

    @Test
    void toggleVisibilityReturnsWhetherAnAccountWasFound() {
        User user = new User();
        user.setConnectedAccounts(new ArrayList<>(List.of(
                new User.ConnectedAccount(OAuthProvider.BLUESKY, "id", "handle", "url", false)
        )));

        assertTrue(service.toggleVisibility(user, OAuthProvider.BLUESKY));
        assertTrue(user.getConnectedAccounts().getFirst().isVisible());
        assertFalse(service.toggleVisibility(user, OAuthProvider.GITHUB));
    }
}
