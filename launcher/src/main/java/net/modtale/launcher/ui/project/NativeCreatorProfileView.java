package net.modtale.launcher.ui.project;

import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.number;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.user.CreatorProfile;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherLayout;

final class NativeCreatorProfileView {

    private static final double CONTENT_MAX_WIDTH = 1568;
    private static final double DESKTOP_NAV_OVERLAP = 91;
    private static final double PROFILE_CARD_HEIGHT = 316;
    private static final double PROFILE_CARD_LIFT = -32;
    private static final double AVATAR_SIZE = 224;
    private static final double PROJECT_GRID_GAP = 24;
    private static final double BANNER_FADE_BASE_HEIGHT = 128;
    private static final int DESKTOP_PROJECT_COLUMNS = 4;

    private final CachedImageLoader imageLoader;
    private final ProjectCardFactory projectCardFactory;
    private final Supplier<String> gameVersion;
    private final Supplier<CurrentUser> currentUser;
    private final Function<String, Boolean> favoriteResolver;
    private final Consumer<ProjectSummary> installProject;
    private final Consumer<ProjectSummary> openProject;
    private final Consumer<ProjectSummary> openCreator;
    private final Consumer<ProjectSummary> toggleFavorite;
    private final Runnable showDiscover;
    private final Runnable toggleFollow;
    private final Runnable copyCreatorId;
    private final Consumer<CreatorProfile> reportCreator;
    private final Consumer<String> openUrl;
    private final ReadOnlyDoubleProperty scrollPixels;

    NativeCreatorProfileView(
            CachedImageLoader imageLoader,
            ProjectCardFactory projectCardFactory,
            Supplier<String> gameVersion,
            Supplier<CurrentUser> currentUser,
            Function<String, Boolean> favoriteResolver,
            Consumer<ProjectSummary> installProject,
            Consumer<ProjectSummary> openProject,
            Consumer<ProjectSummary> openCreator,
            Consumer<ProjectSummary> toggleFavorite,
            Runnable showDiscover,
            Runnable toggleFollow,
            Runnable copyCreatorId,
            Consumer<CreatorProfile> reportCreator,
            Consumer<String> openUrl,
            ReadOnlyDoubleProperty scrollPixels
    ) {
        this.imageLoader = imageLoader;
        this.projectCardFactory = projectCardFactory;
        this.gameVersion = gameVersion;
        this.currentUser = currentUser;
        this.favoriteResolver = favoriteResolver;
        this.installProject = installProject;
        this.openProject = openProject;
        this.openCreator = openCreator;
        this.toggleFavorite = toggleFavorite;
        this.showDiscover = showDiscover;
        this.toggleFollow = toggleFollow;
        this.copyCreatorId = copyCreatorId;
        this.reportCreator = reportCreator == null ? profile -> {
        } : reportCreator;
        this.openUrl = openUrl;
        this.scrollPixels = scrollPixels;
    }

    Node render(CreatorProfile profile, ProjectPage projects, List<CreatorProfile> relatedProfiles, boolean loading, boolean compact) {
        VBox page = new VBox(0);
        page.getStyleClass().add("creator-profile-page");
        page.setAlignment(Pos.TOP_CENTER);
        page.setFillWidth(true);
        page.setMinWidth(0);
        page.setMaxWidth(Double.MAX_VALUE);

        StackPane hero = hero(profile);
        hero.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(compact ? 280 : 360, page.getWidth() / 3.0 - (compact ? 0 : DESKTOP_NAV_OVERLAP)),
                page.widthProperty()
        ));
        hero.minHeightProperty().bind(hero.prefHeightProperty());

        Node card = loading ? loadingCard() : profileCard(profile, projects);
        VBox.setMargin(card, LauncherLayout.launcherPageInsets(PROFILE_CARD_LIFT, 0));

        VBox body = profileBody(profile, projects, relatedProfiles, loading, compact);
        VBox.setMargin(body, compact
                ? LauncherLayout.launcherPageInsets(36, 80)
                : LauncherLayout.launcherPageInsets(64, 80));

        page.getChildren().addAll(hero, card, body);
        page.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> hero.getPrefHeight() + PROFILE_CARD_LIFT + PROFILE_CARD_HEIGHT
                        + (compact ? 36 : 64) + body.prefHeight(-1) + 80,
                hero.prefHeightProperty(),
                body.heightProperty()
        ));
        page.prefHeightProperty().bind(page.minHeightProperty());
        return page;
    }

    private StackPane hero(CreatorProfile profile) {
        StackPane hero = new StackPane();
        hero.getStyleClass().add("creator-profile-hero");
        hero.setMaxWidth(Double.MAX_VALUE);

        StackPane media = new StackPane();
        media.getStyleClass().add("creator-profile-banner");
        media.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        String bannerUrl = profile == null ? "" : value(profile.bannerUrl(), "");
        if (bannerUrl.isBlank()) {
            media.getChildren().add(fallbackBanner());
        } else {
            media.getStyleClass().add("letterboxed");
            media.setClip(rectangleClip(media));
            ImageView image = containedImage(bannerUrl, media, 2400, 800);
            image.getStyleClass().add("creator-profile-banner-image");
            media.getChildren().add(image);
        }

        Region fade = new Region();
        fade.getStyleClass().add("creator-profile-banner-fade");
        fade.setMouseTransparent(true);
        NativeBannerScrollEffect.bind(media, fade, scrollPixels, BANNER_FADE_BASE_HEIGHT);

        HBox backLayer = new HBox();
        backLayer.setAlignment(Pos.TOP_LEFT);
        backLayer.setMaxWidth(Double.MAX_VALUE);
        backLayer.setMouseTransparent(false);
        StackPane.setAlignment(backLayer, Pos.TOP_CENTER);
        StackPane.setMargin(backLayer, LauncherLayout.launcherPageInsets(25, 0));
        Button back = new Button("Back", LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_LEFT, 16));
        back.getStyleClass().add("creator-profile-back");
        back.setOnAction(event -> showDiscover.run());
        backLayer.getChildren().add(back);

        hero.getChildren().addAll(media, fade, backLayer);
        return hero;
    }

    private Region fallbackBanner() {
        Region fallback = new Region();
        fallback.getStyleClass().add("creator-profile-banner-fallback");
        fallback.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return fallback;
    }

    private HBox loadingCard() {
        HBox card = new HBox(40);
        card.getStyleClass().addAll("creator-profile-card", "creator-profile-loading-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(PROFILE_CARD_HEIGHT);
        card.setPrefHeight(PROFILE_CARD_HEIGHT);
        card.getChildren().add(NativeSpinner.centered());
        return card;
    }

    private HBox profileCard(CreatorProfile profile, ProjectPage projects) {
        HBox card = new HBox(40);
        card.getStyleClass().add("creator-profile-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(PROFILE_CARD_HEIGHT);
        card.setPrefHeight(PROFILE_CARD_HEIGHT);

        StackPane avatar = avatar(profile);
        HBox.setMargin(avatar, new Insets(-96, 0, 0, 8));

        VBox copy = new VBox(0);
        copy.getStyleClass().add("creator-profile-copy");
        copy.setTranslateY(-15);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        copy.getChildren().addAll(
                heading(profile),
                actionRow(profile),
                bio(profile),
                statDivider(),
                statRow(profile, projects)
        );

        card.getChildren().addAll(avatar, copy);
        return card;
    }

    private StackPane avatar(CreatorProfile profile) {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("creator-profile-avatar");
        lock(frame, AVATAR_SIZE, AVATAR_SIZE);

        StackPane media = new StackPane();
        media.getStyleClass().add("creator-profile-avatar-media");
        double mediaSize = AVATAR_SIZE - 16;
        lock(media, mediaSize, mediaSize);
        Rectangle clip = new Rectangle(mediaSize, mediaSize);
        clip.setArcWidth(40);
        clip.setArcHeight(40);
        media.setClip(clip);

        String avatarUrl = profile == null ? "" : value(profile.avatarUrl(), "");
        if (!avatarUrl.isBlank()) {
            ImageView image = coverImage(avatarUrl, media, AVATAR_SIZE, AVATAR_SIZE);
            image.getStyleClass().add("creator-profile-avatar-image");
            media.getChildren().add(image);
        } else {
            Label initial = new Label(initial(profile == null ? "" : profile.username()));
            initial.getStyleClass().add("creator-profile-avatar-initial");
            media.getChildren().add(initial);
        }
        frame.getChildren().add(media);
        return frame;
    }

    private HBox heading(CreatorProfile profile) {
        HBox row = new HBox(10);
        row.getStyleClass().add("creator-profile-heading-row");
        row.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(value(profile.username(), "Creator"));
        title.getStyleClass().add("creator-profile-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        row.getChildren().add(title);

        if (profile.organization()) {
            row.getChildren().add(badge("Organization", "organization", LauncherIcons.Glyph.BOX));
        }
        for (String badge : profile.badges()) {
            String normalized = value(badge, "").toUpperCase(Locale.ROOT);
            if (normalized.equals("OG") || normalized.equals("VERIFIED")) {
                row.getChildren().add(badge(normalized.equals("OG") ? "OG" : "Verified", normalized.toLowerCase(Locale.ROOT), null));
            }
        }
        return row;
    }

    private Node badge(String text, String style, LauncherIcons.Glyph glyph) {
        HBox badge = new HBox(5);
        badge.getStyleClass().addAll("creator-profile-badge", style);
        badge.setAlignment(Pos.CENTER);
        if (glyph != null) {
            badge.getChildren().add(LauncherIcons.icon(glyph, 13));
        }
        badge.getChildren().add(new Label(text));
        return badge;
    }

    private HBox actionRow(CreatorProfile profile) {
        HBox row = new HBox(10);
        row.getStyleClass().add("creator-profile-action-row");
        row.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(row, new Insets(7, 0, 0, 0));

        CurrentUser user = currentUser.get();
        boolean self = user != null && value(user.id(), "").equals(value(profile.id(), ""));
        boolean signedIn = user != null;
        boolean following = signedIn && user.followsUser(profile.id());

        Button follow = new Button(self ? "Manage Profile" : signedIn ? following ? "Following" : "Follow" : "Sign in to follow");
        follow.getStyleClass().addAll("creator-profile-follow", following ? "following" : "primary");
        follow.setGraphic(LauncherIcons.icon(self ? LauncherIcons.Glyph.GEAR
                : signedIn ? following ? LauncherIcons.Glyph.CHECK : LauncherIcons.Glyph.USER
                : LauncherIcons.Glyph.LOG_OUT, 18));
        follow.setOnAction(event -> toggleFollow.run());

        Button copy = iconAction(LauncherIcons.Glyph.COPY, "Copy ID");
        copy.setOnAction(event -> copyCreatorId.run());

        Button report = iconAction(LauncherIcons.Glyph.FLAG, "Report User");
        report.getStyleClass().add("report");
        report.setOnAction(event -> reportCreator.accept(profile));

        row.getChildren().addAll(follow, copy);
        if (!self) {
            row.getChildren().add(report);
        }
        return row;
    }

    private Button iconAction(LauncherIcons.Glyph glyph, String accessibleText) {
        Button button = new Button(null, LauncherIcons.icon(glyph, 20));
        button.getStyleClass().add("creator-profile-icon-action");
        button.setAccessibleText(accessibleText);
        return button;
    }

    private Label bio(CreatorProfile profile) {
        Label bio = new Label(value(profile.bio(), ""));
        bio.getStyleClass().add("creator-profile-bio");
        bio.setWrapText(true);
        bio.setMinHeight(Region.USE_PREF_SIZE);
        bio.setVisible(!bio.getText().isBlank());
        bio.setManaged(!bio.getText().isBlank());
        VBox.setMargin(bio, new Insets(20, 0, 0, 0));
        return bio;
    }

    private Region statDivider() {
        Region line = new Region();
        line.getStyleClass().add("creator-profile-divider");
        line.setMaxWidth(Double.MAX_VALUE);
        line.setMinHeight(1);
        line.setPrefHeight(1);
        VBox.setMargin(line, new Insets(26, 0, 0, 0));
        return line;
    }

    private HBox statRow(CreatorProfile profile, ProjectPage projects) {
        HBox row = new HBox(0);
        row.getStyleClass().add("creator-profile-stat-row");
        row.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(row, new Insets(23, 0, 0, 0));
        row.getChildren().add(stats(profile, projects));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        HBox socials = socials(profile);
        row.getChildren().add(socials);
        return row;
    }

    private HBox stats(CreatorProfile profile, ProjectPage projects) {
        int downloads = 0;
        int favorites = 0;
        List<ProjectSummary> content = projects == null ? List.of() : projects.content();
        for (ProjectSummary project : content) {
            downloads += project.downloadCount();
            favorites += project.favoriteCount();
        }
        HBox row = new HBox(34);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(
                stat(number(downloads), "Downloads"),
                stat(number(favorites), "Favorites"),
                stat(number(followerCount(profile)), "Followers"),
                stat(number(projects == null ? content.size() : Math.toIntExact(Math.min(Integer.MAX_VALUE, projects.totalElements()))), "Projects")
        );
        return row;
    }

    private VBox stat(String value, String label) {
        VBox stat = new VBox(1);
        stat.getStyleClass().add("creator-profile-stat");
        Label number = new Label(value);
        number.getStyleClass().add("creator-profile-stat-number");
        Label caption = new Label(label.toUpperCase(Locale.ROOT));
        caption.getStyleClass().add("creator-profile-stat-caption");
        stat.getChildren().addAll(number, caption);
        return stat;
    }

    private HBox socials(CreatorProfile profile) {
        HBox row = new HBox(8);
        row.getStyleClass().add("creator-profile-socials");
        row.setAlignment(Pos.CENTER_RIGHT);
        for (CreatorProfile.ConnectedAccount account : profile.connectedAccounts()) {
            if (account.isVisible()) {
                row.getChildren().add(socialButton(account));
            }
        }
        return row;
    }

    private Button socialButton(CreatorProfile.ConnectedAccount account) {
        String provider = value(account.provider(), "website").toLowerCase(Locale.ROOT);
        Button button = new Button(null, socialIcon(provider));
        button.getStyleClass().addAll("creator-profile-social-button", provider);
        button.setAccessibleText(provider + " profile");
        String url = socialUrl(account);
        button.setOnAction(event -> openUrl.accept(url));
        return button;
    }

    private Node socialIcon(String provider) {
        return switch (provider) {
            case "discord" -> LauncherIcons.brandIcon(LauncherIcons.BrandGlyph.DISCORD, 18);
            case "github" -> LauncherIcons.brandIcon(LauncherIcons.BrandGlyph.GITHUB, 18);
            case "gitlab" -> LauncherIcons.brandIcon(LauncherIcons.BrandGlyph.GITLAB, 18);
            default -> LauncherIcons.icon(LauncherIcons.Glyph.GLOBE, 18);
        };
    }

    private String socialUrl(CreatorProfile.ConnectedAccount account) {
        String provider = value(account.provider(), "").toLowerCase(Locale.ROOT);
        if (provider.equals("discord") && !value(account.providerId(), "").isBlank()) {
            return "https://discord.com/users/" + account.providerId();
        }
        return value(account.profileUrl(), "");
    }

    private VBox profileBody(
            CreatorProfile profile,
            ProjectPage projects,
            List<CreatorProfile> relatedProfiles,
            boolean loading,
            boolean compact
    ) {
        VBox body = new VBox(0);
        body.getStyleClass().add("creator-profile-body");
        body.setMaxWidth(Double.MAX_VALUE);
        body.setMinWidth(0);

        if (!loading && relatedProfiles != null && !relatedProfiles.isEmpty()) {
            body.getChildren().add(relatedProfiles(profile, relatedProfiles));
        }

        Label heading = new Label("Published Work");
        heading.getStyleClass().add("creator-profile-section-title");
        body.getChildren().add(heading);

        Node content = loading ? projectSkeletonGrid(compact) : projects(projects, compact);
        VBox.setMargin(content, new Insets(24, 0, 0, 0));
        body.getChildren().add(content);
        return body;
    }

    private VBox relatedProfiles(CreatorProfile profile, List<CreatorProfile> relatedProfiles) {
        VBox section = new VBox(14);
        section.getStyleClass().add("creator-profile-related-section");
        Label title = new Label(profile.organization() ? "Organization Members" : "Member Organizations");
        title.getStyleClass().add("creator-profile-section-title");
        FlowPane chips = new FlowPane(16, 12);
        chips.getStyleClass().add("creator-profile-related-grid");
        for (CreatorProfile related : relatedProfiles) {
            chips.getChildren().add(profileChip(related));
        }
        section.getChildren().addAll(title, chips);
        VBox.setMargin(section, new Insets(0, 0, 44, 0));
        return section;
    }

    private HBox profileChip(CreatorProfile profile) {
        HBox chip = new HBox(12);
        chip.getStyleClass().add("creator-profile-related-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setOnMouseClicked(event -> openUrl.accept("https://modtale.net/creator/" + value(profile.username(), profile.id())));
        chip.setCursor(Cursor.HAND);
        StackPane avatar = smallAvatar(profile);
        VBox copy = new VBox(1);
        Label name = new Label(value(profile.username(), "Creator"));
        name.getStyleClass().add("creator-profile-related-name");
        Label role = new Label(profile.organization() ? "Organization" : "Member");
        role.getStyleClass().add("creator-profile-related-role");
        copy.getChildren().addAll(name, role);
        chip.getChildren().addAll(avatar, copy);
        return chip;
    }

    private StackPane smallAvatar(CreatorProfile profile) {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("creator-profile-related-avatar");
        lock(frame, 40, 40);
        String avatarUrl = value(profile.avatarUrl(), "");
        if (!avatarUrl.isBlank()) {
            frame.setClip(roundedClip(frame, 8));
            ImageView image = coverImage(avatarUrl, frame, 80, 80);
            frame.getChildren().add(image);
        } else {
            Label initial = new Label(initial(profile.username()));
            initial.getStyleClass().add("creator-profile-related-initial");
            frame.getChildren().add(initial);
        }
        return frame;
    }

    private Node projectSkeletonGrid(boolean compact) {
        FlowPane grid = projectGrid();
        int count = compact ? 3 : 8;
        for (int i = 0; i < count; i++) {
            Region skeleton = new Region();
            skeleton.getStyleClass().add("creator-profile-project-skeleton");
            lock(skeleton, compact ? 320 : projectCardWidth(), compact ? 160 : projectCardWidth());
            grid.getChildren().add(skeleton);
        }
        return grid;
    }

    private Node projects(ProjectPage projects, boolean compact) {
        List<ProjectSummary> content = projects == null ? List.of() : projects.content();
        if (content.isEmpty()) {
            VBox empty = new VBox(10);
            empty.getStyleClass().add("creator-profile-empty");
            empty.setAlignment(Pos.CENTER);
            empty.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.BOX, 36),
                    new Label("No projects found"),
                    new Label("This user hasn't published any projects yet."));
            return empty;
        }

        FlowPane grid = projectGrid();
        double width = compact ? 320 : projectCardWidth();
        for (ProjectSummary project : content) {
            Node card = projectCardFactory.create(
                    project,
                    compact ? ProjectCardViewStyle.LIST : ProjectCardViewStyle.GRID,
                    gameVersion.get(),
                    Boolean.TRUE.equals(favoriteResolver.apply(project.id())),
                    installProject,
                    openProject,
                    openCreator,
                    toggleFavorite,
                    width,
                    compact ? 160 : width
            );
            grid.getChildren().add(card);
        }
        return grid;
    }

    private FlowPane projectGrid() {
        FlowPane grid = new FlowPane(PROJECT_GRID_GAP, PROJECT_GRID_GAP);
        grid.getStyleClass().add("creator-profile-project-grid");
        grid.setMaxWidth(CONTENT_MAX_WIDTH);
        return grid;
    }

    private double projectCardWidth() {
        return Math.floor((CONTENT_MAX_WIDTH - PROJECT_GRID_GAP * (DESKTOP_PROJECT_COLUMNS - 1)) / DESKTOP_PROJECT_COLUMNS);
    }

    private ImageView coverImage(String url, Region box, double requestedWidth, double requestedHeight) {
        ImageView image = new ImageView();
        image.setSmooth(true);
        image.setPreserveRatio(false);
        imageLoader.loadInto(image, url, requestedWidth, requestedHeight, true);
        Runnable update = () -> {
            double width = box.getWidth();
            double height = box.getHeight();
            javafx.scene.image.Image loaded = image.getImage();
            if (!Double.isFinite(width) || width <= 1 || !Double.isFinite(height) || height <= 1 || loaded == null) {
                image.setFitWidth(requestedWidth);
                image.setFitHeight(requestedHeight);
                return;
            }
            double imageWidth = loaded.getWidth();
            double imageHeight = loaded.getHeight();
            if (!Double.isFinite(imageWidth) || imageWidth <= 0 || !Double.isFinite(imageHeight) || imageHeight <= 0) {
                return;
            }
            double imageRatio = imageWidth / imageHeight;
            double boxRatio = width / height;
            if (imageRatio > boxRatio) {
                image.setFitWidth(height * imageRatio);
                image.setFitHeight(height);
            } else {
                image.setFitWidth(width);
                image.setFitHeight(width / imageRatio);
            }
        };
        box.widthProperty().addListener((observable, previous, current) -> update.run());
        box.heightProperty().addListener((observable, previous, current) -> update.run());
        image.imageProperty().addListener((observable, previous, current) -> update.run());
        return image;
    }

    private ImageView containedImage(String url, Region box, double requestedWidth, double requestedHeight) {
        ImageView image = new ImageView();
        image.setSmooth(true);
        image.setPreserveRatio(true);
        image.fitWidthProperty().bind(box.widthProperty());
        image.fitHeightProperty().bind(box.heightProperty());
        imageLoader.loadInto(image, url, requestedWidth, requestedHeight, true);
        return image;
    }

    private Rectangle rectangleClip(Region owner) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(owner.widthProperty());
        clip.heightProperty().bind(owner.heightProperty());
        return clip;
    }

    private Rectangle roundedClip(Region owner, double radius) {
        Rectangle clip = rectangleClip(owner);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        return clip;
    }

    private int followerCount(CreatorProfile profile) {
        CurrentUser user = currentUser.get();
        int count = profile.followerIds().size();
        if (user == null || value(user.id(), "").isBlank()) {
            return count;
        }
        boolean actual = user.followsUser(profile.id());
        boolean recorded = profile.followerIds().contains(user.id());
        if (actual && !recorded) {
            return count + 1;
        }
        if (!actual && recorded) {
            return Math.max(0, count - 1);
        }
        return count;
    }

    private static void lock(Region region, double width, double height) {
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
    }

    private static String initial(String value) {
        String normalized = value(value, "C").trim();
        return normalized.isEmpty() ? "C" : normalized.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
