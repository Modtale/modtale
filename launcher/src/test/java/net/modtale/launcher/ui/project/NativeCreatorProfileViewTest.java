package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
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

    @Test
    void bannerFitsImagesLoadedBeforeOrAfterLayoutWithoutCropping() {
        StackPane banner = new StackPane();
        var image = NativeCreatorProfileView.bannerImage(banner);
        image.setImage(new WritableImage(400, 400));
        banner.resize(900, 300);
        assertEquals(300, image.getBoundsInLocal().getWidth(), 0.001);
        assertEquals(300, image.getBoundsInLocal().getHeight(), 0.001);

        image.setImage(new WritableImage(1200, 200));
        assertEquals(900, image.getBoundsInLocal().getWidth(), 0.001);
        assertEquals(150, image.getBoundsInLocal().getHeight(), 0.001);

        banner.resize(600, 200);
        assertEquals(600, image.getBoundsInLocal().getWidth(), 0.001);
        assertEquals(100, image.getBoundsInLocal().getHeight(), 0.001);

        image.setImage(null);
        banner.resize(1200, 400);
        image.setImage(new WritableImage(300, 900));
        assertEquals(400.0 / 3, image.getBoundsInLocal().getWidth(), 0.001);
        assertEquals(400, image.getBoundsInLocal().getHeight(), 0.001);
    }

    private static CreatorProfile.ConnectedAccount account(String provider, String id, String username, String url) {
        return new CreatorProfile.ConnectedAccount(provider, id, username, url, true);
    }
}
