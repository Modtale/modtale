package net.modtale.launcher.ui.browse.card;

import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.classificationLabel;
import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.number;
import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.value;
import static net.modtale.launcher.ui.browse.card.ProjectCardMedia.lockHeight;
import static net.modtale.launcher.ui.browse.card.ProjectCardMedia.lockWidth;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import net.modtale.launcher.install.VersionSelector;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.ui.browse.controls.BrowseOptions;
import net.modtale.launcher.ui.common.LauncherIcons;

public final class ProjectCardFactory {

    public static final String SCROLL_ACTIVE_PROPERTY = "net.modtale.launcher.scrollActive";

    private static final double GRID_WIDTH = 400;
    private static final double GRID_ICON_SIZE = 104;
    private static final double GRID_BODY_PADDING = 22;
    private static final double LIST_ICON_SIZE = 128;
    private static final double COMPACT_WIDTH = 330;
    private static final double CARD_STAT_HEIGHT = 38;
    private static final double CARD_STAT_ICON_SIZE = 16;
    private static final double COMPACT_STAT_ICON_SIZE = 14;
    private static final double TITLE_FONT_SIZE = 20;
    private static final double COMPACT_TITLE_FONT_SIZE = 14;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final ProjectCardMedia media;

    public ProjectCardFactory(Function<String, String> assetResolver, Executor executor) {
        this.media = new ProjectCardMedia(assetResolver, executor);
    }

    public void clearImageCache() {
        media.clearImageCache();
    }

    public Node create(
            ProjectSummary project,
            ProjectCardViewStyle viewStyle,
            String gameVersion,
            boolean favorite,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onOpenPage,
            Consumer<ProjectSummary> onOpenCreator,
            Consumer<ProjectSummary> onToggleFavorite,
            double cardWidth,
            double cardHeight
    ) {
        return switch (viewStyle) {
            case LIST -> listCard(project, gameVersion, favorite, onInstall, onOpenPage, onOpenCreator, onToggleFavorite);
            case COMPACT -> compactCard(project, gameVersion, favorite, onInstall, onOpenPage, onOpenCreator, onToggleFavorite, cardWidth, cardHeight);
            case GRID -> gridCard(project, gameVersion, favorite, onInstall, onOpenPage, onOpenCreator, onToggleFavorite, cardWidth, cardHeight);
        };
    }

    private Node gridCard(
            ProjectSummary project,
            String gameVersion,
            boolean favorite,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onOpenPage,
            Consumer<ProjectSummary> onOpenCreator,
            Consumer<ProjectSummary> onToggleFavorite,
            double cardWidth,
            double cardHeight
    ) {
        double width = cardWidth > 0 ? cardWidth : GRID_WIDTH;
        double height = cardHeight > 0 ? cardHeight : Math.round(width);
        double bannerHeight = Math.round(width / 3.0);
        double bodyHeight = Math.max(0, height - bannerHeight);
        boolean tight = width < 390 || bodyHeight < 272;
        boolean shelfTight = tight && bodyHeight < 216 && height < width * 0.95;
        double bodyPadding = shelfTight ? 14 : GRID_BODY_PADDING;
        double iconSize = shelfTight
                ? Math.max(64, Math.min(72, Math.round(width * 0.215)))
                : Math.max(84, Math.min(GRID_ICON_SIZE, Math.round(width * 0.215)));
        double iconOverlap = Math.round(iconSize / 2.0);
        double bodySpacing = shelfTight ? 3 : tight ? 5 : 6;
        double copySpacing = shelfTight ? 3 : tight ? 6 : 8;
        double descriptionHeight = shelfTight ? 24 : tight ? 44 : 52;
        Optional<ProjectVersion> latestVersion = latestCompatible(project, gameVersion);
        StackPane shell = new StackPane();
        shell.getStyleClass().add("project-card-shell");
        shell.setAlignment(Pos.TOP_LEFT);
        lockWidth(shell, width);
        lockHeight(shell, height);
        ProjectCardInteraction.openOnCardClick(shell, project, onOpenPage);

        VBox card = new VBox(0);
        card.getStyleClass().addAll("project-card", "project-card-grid");
        cacheCardSurface(card);
        lockWidth(card, width);
        lockHeight(card, height);

        StackPane banner = media.banner(project, width, bannerHeight);
        Node bannerMedia = banner.getChildren().isEmpty() ? null : banner.getChildren().get(0);
        banner.getChildren().add(classificationBadge(project.classification()));
        StackPane.setAlignment(banner.getChildren().getLast(), Pos.TOP_RIGHT);
        StackPane.setMargin(banner.getChildren().getLast(), new Insets(8));

        VBox body = new VBox(bodySpacing);
        body.getStyleClass().add("project-card-body");
        if (shelfTight) {
            body.getStyleClass().add("project-card-body-shelf-tight");
        }
        lockHeight(body, bodyHeight);
        VBox.setVgrow(body, Priority.ALWAYS);
        StackPane icon = media.projectIcon(project, iconSize, 4);
        VBox.setMargin(icon, new Insets(-(iconOverlap + bodyPadding), 0, 0, 0));

        VBox copy = new VBox(copySpacing);
        copy.getStyleClass().add("project-copy");
        Label title = titleText(value(project.title(), "Untitled Project"), "project-title", TITLE_FONT_SIZE);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        Label description = text(value(project.description(), "No description provided."), "project-description");
        description.setWrapText(true);
        description.setAlignment(Pos.TOP_LEFT);
        description.setPrefHeight(descriptionHeight);
        description.setMinHeight(descriptionHeight);
        description.setMaxHeight(descriptionHeight);
        VBox.setMargin(description, new Insets(tight ? 1 : 2, 0, 0, 0));
        copy.getChildren().addAll(title, authorLine(project, "By", "byline", onOpenCreator), description);

        Region footerSpacer = new Region();
        VBox.setVgrow(footerSpacer, Priority.ALWAYS);
        body.getChildren().addAll(icon, copy, footerSpacer,
                installStatsRow(project, latestVersion, favorite, onInstall, onToggleFavorite, tight));
        card.getChildren().addAll(banner, body);
        shell.getChildren().addAll(card, media.restingOutline(shell), media.hoverOutline(shell, shell));
        ProjectCardInteraction.addHoverAnimation(shell, icon, bannerMedia);
        return shell;
    }

    private Node listCard(
            ProjectSummary project,
            String gameVersion,
            boolean favorite,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onOpenPage,
            Consumer<ProjectSummary> onOpenCreator,
            Consumer<ProjectSummary> onToggleFavorite
    ) {
        HBox card = new HBox(18);
        card.getStyleClass().addAll("project-card", "project-card-list");
        cacheCardSurface(card);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        ProjectCardInteraction.openOnCardClick(card, project, onOpenPage);
        Optional<ProjectVersion> latestVersion = latestCompatible(project, gameVersion);

        StackPane icon = media.projectIcon(project, LIST_ICON_SIZE, 4);
        VBox copy = new VBox(8);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.TOP_LEFT);
        Label title = titleText(value(project.title(), "Untitled Project"), "project-title", TITLE_FONT_SIZE);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(title, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, classificationBadge(project.classification()));

        Label description = text(value(project.description(), "No description provided."), "project-description");
        description.setWrapText(true);
        description.setMaxHeight(40);
        copy.getChildren().addAll(titleRow, authorLine(project, "by", "byline", onOpenCreator), description, statsRow(project, favorite, onToggleFavorite));

        VBox actions = new VBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(versionLabel(latestVersion), installButton(project, onInstall));
        card.getChildren().addAll(icon, copy, actions);
        ProjectCardInteraction.addHoverAnimation(card, icon);
        return card;
    }

    private Node compactCard(
            ProjectSummary project,
            String gameVersion,
            boolean favorite,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onOpenPage,
            Consumer<ProjectSummary> onOpenCreator,
            Consumer<ProjectSummary> onToggleFavorite,
            double cardWidth,
            double cardHeight
    ) {
        HBox card = new HBox(16);
        card.getStyleClass().addAll("project-card", "project-card-compact");
        cacheCardSurface(card);
        card.setAlignment(Pos.CENTER_LEFT);
        lockWidth(card, cardWidth > 0 ? cardWidth : COMPACT_WIDTH);
        if (cardHeight > 0) {
            lockHeight(card, cardHeight);
        }
        ProjectCardInteraction.openOnCardClick(card, project, onOpenPage);
        Optional<ProjectVersion> latestVersion = latestCompatible(project, gameVersion);

        StackPane icon = media.projectIcon(project, 64, 2);
        VBox copy = new VBox(5);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label title = titleText(value(project.title(), "Untitled Project"), "compact-title", COMPACT_TITLE_FONT_SIZE);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        copy.getChildren().addAll(title, authorLine(project, "by", "compact-byline", onOpenCreator));

        VBox stats = new VBox(4, statLabel(LauncherIcons.Glyph.DOWNLOAD, number(project.downloadCount())),
                favoriteStat(project, favorite, onToggleFavorite));
        stats.getStyleClass().add("compact-stats");

        Button install = installButton(project, onInstall);
        install.getStyleClass().add("icon-only-button");
        install.setText("");
        install.setMinWidth(42);
        install.setPrefWidth(42);
        install.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 16));

        card.getChildren().addAll(icon, copy, stats, install, LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_RIGHT, 16));
        ProjectCardInteraction.addHoverAnimation(card, icon);
        return card;
    }

    private Node classificationBadge(String classification) {
        HBox badge = new HBox(6);
        badge.getStyleClass().add("classification-badge");
        badge.setAlignment(Pos.CENTER);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        badge.getChildren().addAll(
                LauncherIcons.icon(BrowseOptions.classification(classification).icon(), 14),
                text(classificationLabel(classification), "classification-label")
        );
        return badge;
    }

    private static void cacheCardSurface(Region card) {
        card.setCache(true);
        card.setCacheHint(CacheHint.SPEED);
    }

    private Node installStatsRow(
            ProjectSummary project,
            Optional<ProjectVersion> latestVersion,
            boolean favorite,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onToggleFavorite,
            boolean tight
    ) {
        HBox row = new HBox(tight ? 12 : 16);
        row.getStyleClass().add("project-stats");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(CARD_STAT_HEIGHT);
        row.setPrefHeight(CARD_STAT_HEIGHT);

        HBox left = new HBox(tight ? 12 : 16, statLabel(LauncherIcons.Glyph.DOWNLOAD, number(project.downloadCount()), true),
                favoriteStat(project, favorite, onToggleFavorite, true));
        left.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button install = installButton(project, onInstall);
        if (tight) {
            install.getStyleClass().add("icon-only-button");
            install.setText("");
            install.setMinWidth(38);
            install.setPrefWidth(38);
        }
        install.setMinHeight(CARD_STAT_HEIGHT);
        install.setPrefHeight(CARD_STAT_HEIGHT);
        row.getChildren().addAll(left, spacer, install);
        return row;
    }

    private Node statsRow(ProjectSummary project, boolean favorite, Consumer<ProjectSummary> onToggleFavorite) {
        HBox row = new HBox(16, statLabel(LauncherIcons.Glyph.DOWNLOAD, number(project.downloadCount()), true),
                favoriteStat(project, favorite, onToggleFavorite, true));
        row.getStyleClass().add("project-stats");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox statLabel(LauncherIcons.Glyph glyph, String value) {
        return statLabel(glyph, value, false);
    }

    private HBox statLabel(LauncherIcons.Glyph glyph, String value, boolean fullSize) {
        HBox stat = new HBox(5);
        stat.getStyleClass().addAll(fullSize ? "project-stat-large" : "project-stat");
        stat.setAlignment(Pos.CENTER_LEFT);
        if (fullSize) {
            stat.setMinHeight(CARD_STAT_HEIGHT);
            stat.setPrefHeight(CARD_STAT_HEIGHT);
        }
        stat.getChildren().addAll(LauncherIcons.icon(glyph, fullSize ? CARD_STAT_ICON_SIZE : COMPACT_STAT_ICON_SIZE),
                text(value, fullSize ? "project-stat-text-large" : "project-stat-text"));
        return stat;
    }

    private Button favoriteStat(ProjectSummary project, boolean favorite, Consumer<ProjectSummary> onToggleFavorite) {
        return favoriteStat(project, favorite, onToggleFavorite, false);
    }

    private Button favoriteStat(
            ProjectSummary project,
            boolean favorite,
            Consumer<ProjectSummary> onToggleFavorite,
            boolean fullSize
    ) {
        Button button = new Button(number(project.favoriteCount()));
        button.getStyleClass().addAll(fullSize ? "project-stat-large" : "project-stat", "favorite-stat");
        if (fullSize) {
            button.setMinHeight(CARD_STAT_HEIGHT);
            button.setPrefHeight(CARD_STAT_HEIGHT);
        }
        button.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.HEART,
                fullSize ? CARD_STAT_ICON_SIZE : COMPACT_STAT_ICON_SIZE));
        button.pseudoClassStateChanged(SELECTED, favorite);
        button.setOnAction(event -> {
            event.consume();
            onToggleFavorite.accept(project);
        });
        return button;
    }

    private HBox authorLine(
            ProjectSummary project,
            String prefix,
            String textStyleClass,
            Consumer<ProjectSummary> onOpenCreator
    ) {
        HBox row = new HBox(4);
        row.getStyleClass().add("byline-row");
        row.setAlignment(Pos.CENTER_LEFT);
        Label by = text(prefix, textStyleClass);
        Button author = new Button(value(project.author(), "Unknown"));
        author.getStyleClass().addAll("author-link", textStyleClass);
        author.setAlignment(Pos.CENTER_LEFT);
        author.setTextOverrun(OverrunStyle.ELLIPSIS);
        author.setMinWidth(0);
        author.setOnAction(event -> {
            event.consume();
            onOpenCreator.accept(project);
        });
        row.getChildren().addAll(by, author);
        return row;
    }

    private Label versionLabel(Optional<ProjectVersion> latestVersion) {
        Label latest = text(latestVersion.map(version -> "Latest " + version.versionNumber()).orElse(""), "latest-label");
        latest.setVisible(latestVersion.isPresent());
        latest.setManaged(latestVersion.isPresent());
        latest.setTextOverrun(OverrunStyle.ELLIPSIS);
        return latest;
    }

    private Button installButton(ProjectSummary project, Consumer<ProjectSummary> onInstall) {
        Button install = new Button("Install");
        install.getStyleClass().addAll("btn", "primary", "small", "project-install-button");
        install.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 16));
        install.setOnAction(event -> {
            event.consume();
            onInstall.accept(project);
        });
        return install;
    }

    private Optional<ProjectVersion> latestCompatible(ProjectSummary project, String gameVersion) {
        return VersionSelector.latestCompatible(project.versions(), gameVersion);
    }

    private Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private Label titleText(String value, String styleClass, double fontSize) {
        Label label = text(value, styleClass);
        label.setFont(Font.font("Arial Black", FontWeight.BLACK, fontSize));
        return label;
    }
}
