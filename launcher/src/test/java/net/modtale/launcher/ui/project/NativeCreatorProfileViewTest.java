package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.modtale.launcher.model.user.CreatorProfile;
import org.junit.jupiter.api.Test;

class NativeCreatorProfileViewTest {
    @Test
    void resolvesProviderUrlsLikeTheWebProfile() {
        assertEquals("https://discord.com/users/123",
                NativeCreatorProfileView.socialUrl(account("discord", "123", "ada", "")));
        assertEquals("https://x.com/ada%20lovelace",
                NativeCreatorProfileView.socialUrl(account("twitter", "", "@ada lovelace", "")));
        assertEquals("https://bsky.app/profile/ada.bsky.social",
                NativeCreatorProfileView.socialUrl(account("bluesky", "", "ada.bsky.social", "")));
        assertEquals("https://example.com/ada",
                NativeCreatorProfileView.socialUrl(account("github", "", "ada", "https://example.com/ada")));
    }

    private static CreatorProfile.ConnectedAccount account(String provider, String id, String username, String url) {
        return new CreatorProfile.ConnectedAccount(provider, id, username, url, true);
    }
}
