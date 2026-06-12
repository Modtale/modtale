package net.modtale.service.user.connection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

@Service
public class ConnectedAccountMutationService {

    public void linkProvider(
            User user,
            OAuthProvider provider,
            String providerId,
            String username,
            String profileUrl,
            boolean visible,
            String accessToken
    ) {
        storeAccessToken(user, provider, accessToken, null, null);
        upsertConnectedAccount(user, provider, providerId, username, profileUrl, visible);
    }

    public void upsertConnectedAccount(
            User user,
            OAuthProvider provider,
            String providerId,
            String username,
            String profileUrl,
            boolean visible
    ) {
        if (user.getConnectedAccounts() == null) {
            user.setConnectedAccounts(new ArrayList<>());
        }
        user.getConnectedAccounts().removeIf(account -> account.getProvider() == provider);
        user.getConnectedAccounts().add(new User.ConnectedAccount(provider, providerId, username, profileUrl, visible));
    }

    public boolean unlink(User user, OAuthProvider provider) {
        if (user.getConnectedAccounts() == null) {
            return false;
        }

        boolean removed = user.getConnectedAccounts().removeIf(account -> account.getProvider() == provider);
        if (removed) {
            clearProviderTokens(user, provider);
        }
        return removed;
    }

    public boolean toggleVisibility(User user, OAuthProvider provider) {
        if (user.getConnectedAccounts() == null) {
            return false;
        }

        return user.getConnectedAccounts().stream()
                .filter(account -> account.getProvider() == provider)
                .findFirst()
                .map(account -> {
                    account.setVisible(!account.isVisible());
                    return true;
                })
                .orElse(false);
    }

    public boolean updateProviderTokens(
            User user,
            OAuthProvider provider,
            String accessToken,
            String refreshToken,
            LocalDateTime expiresAt
    ) {
        return storeAccessToken(user, provider, accessToken, refreshToken, expiresAt);
    }

    private boolean storeAccessToken(
            User user,
            OAuthProvider provider,
            String accessToken,
            String refreshToken,
            LocalDateTime expiresAt
    ) {
        if (provider == OAuthProvider.GITHUB) {
            user.setGithubAccessToken(accessToken);
            return true;
        }

        if (provider == OAuthProvider.GITLAB) {
            user.setGitlabAccessToken(accessToken);
            if (refreshToken != null) {
                user.setGitlabRefreshToken(refreshToken);
            }
            if (expiresAt != null) {
                user.setGitlabTokenExpiresAt(expiresAt);
            }
            return true;
        }

        return false;
    }

    private void clearProviderTokens(User user, OAuthProvider provider) {
        if (provider == OAuthProvider.GITHUB) {
            user.setGithubAccessToken(null);
            return;
        }

        if (provider == OAuthProvider.GITLAB) {
            user.setGitlabAccessToken(null);
            user.setGitlabRefreshToken(null);
            user.setGitlabTokenExpiresAt(null);
        }
    }
}
