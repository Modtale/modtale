package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Insets;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.model.user.CreatorProfile;
import org.junit.jupiter.api.Test;

class NativeCreatorProfileViewTest {
    @Test
    void avatarHasItsFullSizeBeforeDecodingAndAfterResize() {
        StackPane media = new StackPane();
        media.resize(208, 208);
        var image = NativeCreatorProfileView.coverImage(media);
        assertEquals(208, image.getFitWidth());
        assertEquals(208, image.getFitHeight());
        image.setImage(new WritableImage(224, 224));
        assertEquals(208, image.getBoundsInLocal().getWidth());
        assertEquals(208, image.getBoundsInLocal().getHeight());
        media.resize(80, 80);
        assertEquals(80, image.getBoundsInLocal().getWidth());
        assertEquals(80, image.getBoundsInLocal().getHeight());
    }

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

    @Test
    void growingBiographyKeepsStatsInsideTheCardsBottomPadding() {
        var card = NativeCreatorProfileView.profileCardContainer();
        card.setPadding(new Insets(40));
        Region biography = new Region();
        biography.setPrefSize(300, 40);
        Region stats = new Region();
        stats.setPrefSize(300, 60);
        VBox copy = new VBox(biography, stats);
        card.getChildren().add(copy);
        double shortHeight = card.prefHeight(500);
        biography.setPrefHeight(240);
        double longHeight = card.prefHeight(500);
        assertEquals(shortHeight + 200, longHeight, 0.001);
        assertEquals(longHeight, card.minHeight(500), 0.001);
        card.resize(500, longHeight);
        card.layout();
        copy.layout();
        double statsBottom = copy.getLayoutY() + stats.getLayoutY() + stats.getHeight();
        assertEquals(40, card.getHeight() - statsBottom, 0.001);
    }

    @Test
    void documentHeightIncludesEveryWrappedProjectRowAtTheAvailableWidth() {
        FlowPane projects = new FlowPane(24, 24);
        for (int i = 0; i < 6; i++) {
            Region tile = new Region();
            tile.setMinSize(300, 200);
            tile.setPrefSize(300, 200);
            projects.getChildren().add(tile);
        }
        VBox body = new VBox(projects);
        VBox.setMargin(body, new Insets(64, 112, 80, 112));
        assertEquals(648, NativeCreatorProfileView.preferredHeightAtPageWidth(body, 900), 0.001);
        assertEquals(424, NativeCreatorProfileView.preferredHeightAtPageWidth(body, 1200), 0.001);
        projects.setTranslateY(300);
        assertEquals(648, NativeCreatorProfileView.preferredHeightAtPageWidth(body, 900), 0.001);
    }

    private static CreatorProfile.ConnectedAccount account(String provider, String id, String username, String url) {
        return new CreatorProfile.ConnectedAccount(provider, id, username, url, true);
    }
}
