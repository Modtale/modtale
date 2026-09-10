package net.modtale.launcher.ui.play;

import static net.modtale.launcher.ui.common.LauncherUi.dangerButton;
import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.StringConverter;
import net.modtale.launcher.ui.common.LauncherSkeleton;
import net.modtale.launcher.ui.common.LauncherSkeletonContent;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ProjectSearchQuery;
import net.modtale.launcher.discord.DiscordRichPresenceService;
import net.modtale.launcher.hytale.HytaleApiClient;
import net.modtale.launcher.news.LauncherNewsPost;
import net.modtale.launcher.news.LauncherNewsFeed;
import net.modtale.launcher.news.ModtaleNewsClient;
import net.modtale.launcher.hytale.HytaleApiException;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.hytale.HytaleFriend;
import net.modtale.launcher.hytale.HytaleGameVersionResolver;
import net.modtale.launcher.hytale.HytaleGameLauncher;
import net.modtale.launcher.hytale.HytaleLaunchResult;
import net.modtale.launcher.hytale.HytalePlatform;
import net.modtale.launcher.hytale.HytaleProfile;
import net.modtale.launcher.hytale.HytaleVersion;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseSort;
import net.modtale.launcher.ui.common.LauncherExternalLinks;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import net.modtale.launcher.ui.settings.LauncherSettingsForm;
import net.modtale.launcher.logging.LauncherLog;
import net.modtale.launcher.logging.LauncherLogger;

public final class LauncherPlayController {

    private static final LauncherLogger LOG = LauncherLog.getLogger(LauncherPlayController.class);

    private static final int BLOG_POST_PAGE_SIZE = 4;
    private static final int FRIEND_LIMIT = 6;
    private static final double SIDEBAR_WIDTH = 336;
    private static final double SIDEBAR_SCROLL_GUTTER = 12;
    private static final double SIDEBAR_PREF_HEIGHT = 672;
    private static final double NEWS_THUMBNAIL_WIDTH = 264;
    private static final double NEWS_THUMBNAIL_HEIGHT = 149;
    private static final double FRIEND_AVATAR_SIZE = 34;
    private static final double IDENTITY_AVATAR_SIZE = 40;
    private static final double IDENTITY_MENU_AVATAR_SIZE = 28;
    private static final double PROFILE_AVATAR_RADIUS = 8;
    private static final double PLAY_BUTTON_FONT_SIZE = 22;
    private static final int CATALOG_SHELF_LIMIT = 20;
    private static final double CATALOG_SECTION_GAP = 8;
    private static final DateTimeFormatter BLOG_DATE = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter BLOG_DATE_WITH_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final ModtaleApiClient apiClient;
    private final ProjectCardFactory projectCardFactory;
    private final HytaleAuthService hytaleAuthService;
    private final HytaleGameLauncher hytaleGameLauncher;
    private final DiscordRichPresenceService discordRichPresence;
    private final LauncherSettingsController settingsController;
    private final LauncherFeedback feedback;
    private final Executor executor;
    private final Function<String, Boolean> favoriteResolver;
    private final Supplier<String> gameVersion;
    private final Consumer<ProjectSummary> onInstall;
    private final Consumer<ProjectSummary> onOpenPage;
    private final Consumer<ProjectSummary> onOpenCreator;
    private final Consumer<ProjectSummary> onToggleFavorite;
    private final Label hytaleStatus = new Label("Hytale signed out");
    private final Label buildMetric = new Label("Unset");
    private final Label patchlineMetric = new Label("Latest release");
    private final Label playtimeMetric = new Label("No playtime yet");
    private final Button identityButton = new Button();
    private final StackPane identityAvatar = new StackPane();
    private final Label identityTitle = new Label("Signed out");
    private final Label identitySubtitle = new Label();
    private final VBox friendsList = new VBox(9);
    private final VBox newsList = new VBox(16);
    private final Button moreNews = new Button("More news");
    private final Label newsStatus = new Label();
    private final ModtaleNewsClient modtaleNews = new ModtaleNewsClient();
    private final CatalogShelf newReleasesShelf = new CatalogShelf(ProjectBrowseSort.NEWEST);
    private final CatalogShelf trendingShelf = new CatalogShelf(ProjectBrowseSort.TRENDING);
    private final net.modtale.launcher.hytale.HytaleAvatarClient avatarClient;
    private final Map<String, Image> imageCache = new ConcurrentHashMap<>();

    private volatile Process hytaleProcess;
    private boolean versionsLoading;
    private boolean suppressBranchVersionLoad;
    private boolean friendsLoading;
    private boolean playtimeLoading;
    private boolean blogPostsLoading;
    private boolean blogPostsLoaded;
    private List<LauncherNewsPost> blogPosts = List.of();
    private int renderedBlogPosts;
    private long versionLoadRetryAfterMillis;
    private long versionRetryScheduledAtMillis;
    private String loadedVersionsKey = "";
    private String loadedFriendsKey = "";
    private String loadedPlaytimeKey = "";
    private Node view;
    private VBox setupPopover;
    private ContextMenu identityMenu;
    private long identityMenuHiddenAtMillis;
    private Runnable onHytaleAccountsChanged = () -> {
    };
    private Consumer<ProjectBrowseSort> onBrowseCatalog = sort -> {
    };

    public LauncherPlayController(
            ModtaleApiClient apiClient,
            ProjectCardFactory projectCardFactory,
            HytaleAuthService hytaleAuthService,
            HytaleGameLauncher hytaleGameLauncher,
            DiscordRichPresenceService discordRichPresence,
            LauncherSettingsController settingsController,
            LauncherFeedback feedback,
            Executor executor,
            Function<String, Boolean> favoriteResolver,
            Supplier<String> gameVersion,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onOpenPage,
            Consumer<ProjectSummary> onOpenCreator,
            Consumer<ProjectSummary> onToggleFavorite
    ) {
        this.apiClient = apiClient;
        this.projectCardFactory = projectCardFactory;
        this.hytaleAuthService = hytaleAuthService;
        this.hytaleGameLauncher = hytaleGameLauncher;
        this.discordRichPresence = discordRichPresence;
        this.settingsController = settingsController;
        this.feedback = feedback;
        this.executor = executor;
        this.avatarClient = new net.modtale.launcher.hytale.HytaleAvatarClient(hytaleAuthService, executor);
        this.favoriteResolver = favoriteResolver == null ? id -> false : favoriteResolver;
        this.gameVersion = gameVersion == null ? () -> "" : gameVersion;
        this.onInstall = onInstall == null ? project -> {
        } : onInstall;
        this.onOpenPage = onOpenPage == null ? project -> {
        } : onOpenPage;
        this.onOpenCreator = onOpenCreator == null ? project -> {
        } : onOpenCreator;
        this.onToggleFavorite = onToggleFavorite == null ? project -> {
        } : onToggleFavorite;
    }

    public Node view() {
        if (view == null) {
            view = buildView();
        }
        return view;
    }

    public void setOnHytaleAccountsChanged(Runnable onHytaleAccountsChanged) {
        this.onHytaleAccountsChanged = onHytaleAccountsChanged == null ? () -> {
        } : onHytaleAccountsChanged;
    }

    public void setOnBrowseCatalog(Consumer<ProjectBrowseSort> onBrowseCatalog) {
        this.onBrowseCatalog = onBrowseCatalog == null ? sort -> {
        } : onBrowseCatalog;
    }

    public void syncMetrics() {
        LauncherSettings settings = settingsController.settings();
        HytaleAuthSession session = settings.getHytaleAuthSession();
        syncIdentitySummary(settings, session);
        buildMetric.setText(selectedVersionLabel(settings));
        syncPatchlineMetric(settings.getHytaleBranch());
        syncPlaytimeMetric(session);
        syncSidebarData(settings, session);
        syncCatalogShelves(false);
        maybeLoadHytaleVersions();
        maybeLoadHytalePlaytime();
    }

    public void refreshCatalogShelves() {
        resetCatalogShelf(newReleasesShelf);
        resetCatalogShelf(trendingShelf);
        syncCatalogShelves(true);
    }

    public void loadHytaleVersions() {
        loadHytaleVersions(false);
    }

    private void loadHytaleVersions(boolean force) {
        LauncherSettings settings = settingsController.settings();
        HytaleAuthSession session = settings.getHytaleAuthSession();
        if (session == null || !session.hasRefreshToken()) {
            return;
        }
        String versionKey = versionsKey(settings, session);
        LauncherSettingsForm form = settingsController.form();
        applyCachedHytaleVersions(settings, session, form);
        if (versionsLoading || (!force && versionKey.equals(loadedVersionsKey) && !form.hytaleVersionCombo().getItems().isEmpty())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (versionLoadRetryAfterMillis > now) {
            return;
        }

        versionsLoading = true;
        form.hytaleVersionCombo().setPromptText("Loading builds");
        settingsController.saveFromFields(false);
        feedback.log("Loading Hytale builds...");
        CompletableFuture.supplyAsync(() -> {
            LauncherSettings currentSettings = settingsController.settings();
            return loadAllHytaleVersions(currentSettings, session);
        }, executor).whenComplete((payload, error) -> Platform.runLater(() -> {
            versionsLoading = false;
            if (error != null) {
                long retryDelay = versionLoadRetryDelayMillis(error);
                versionLoadRetryAfterMillis = System.currentTimeMillis() + retryDelay;
                boolean cached = applyCachedHytaleVersions(settingsController.settings(), session, form);
                if (!cached) {
                    form.hytaleVersionCombo().setPromptText("Build unavailable");
                }
                scheduleHytaleVersionRetry(retryDelay);
                feedback.log(hytaleBuildLoadErrorMessage(error) + (cached ? " Using cached builds." : ""));
                return;
            }
            cacheHytaleVersions(settingsController.settings(), session, payload);
            form.setHytalePatchlines(payload.patchlines(), payload.selectedPatchline());
            List<HytaleVersion> versions = payload.versionsForSelectedPatchline();
            if (versions.isEmpty() && payload.rateLimit() != null) {
                versions = settingsController.settings().cachedHytaleVersions(
                        LauncherSettings.hytaleAccountId(session),
                        hytaleVersionCachePlatform(),
                        payload.selectedPatchline()
                );
            }
            if (payload.rateLimit() == null) {
                loadedVersionsKey = versionsKey(settingsController.settings(), session);
            } else {
                loadedVersionsKey = "";
            }
            form.hytaleVersionCombo().setPromptText("Choose build");
            form.hytaleVersionCombo().setItems(FXCollections.observableArrayList(versions));
            settingsController.selectConfiguredHytaleBuild();
            settingsController.saveFromFields(false);
            long labeledVersions = versions.stream().filter(version -> !version.gameVersion().isBlank()).count();
            feedback.log(hytaleVersionLoadMessage(payload, versions.size(), labeledVersions));
            if (payload.rateLimit() == null) {
                versionLoadRetryAfterMillis = 0;
                versionRetryScheduledAtMillis = 0;
            } else {
                long retryDelay = versionLoadRetryDelayMillis(payload.rateLimit());
                versionLoadRetryAfterMillis = System.currentTimeMillis() + retryDelay;
                scheduleHytaleVersionRetry(retryDelay);
            }
            syncMetrics();
        }));
    }

    private HytaleVersionsPayload loadAllHytaleVersions(LauncherSettings settings, HytaleAuthSession session) {
        String accountId = LauncherSettings.hytaleAccountId(session);
        String platform = hytaleVersionCachePlatform();
        List<String> cachedPatchlines = settings.cachedHytalePatchlines(accountId, platform);
        List<String> patchlines;
        try {
            patchlines = hytaleAuthService.getAvailablePatchlines(settings);
        } catch (HytaleApiException ex) {
            if (ex.statusCode() != 429 || cachedPatchlines.isEmpty()) {
                throw ex;
            }
            String selectedPatchline = selectedPatchline(settings.getHytaleBranch(), cachedPatchlines);
            List<String> pending = refreshPatchlineOrder(settings, accountId, platform, cachedPatchlines, selectedPatchline);
            return new HytaleVersionsPayload(cachedPatchlines, selectedPatchline, Map.of(), pending, ex);
        }

        String selectedPatchline = selectedPatchline(settings.getHytaleBranch(), patchlines);
        List<String> refreshOrder = refreshPatchlineOrder(settings, accountId, platform, patchlines, selectedPatchline);
        Map<String, List<HytaleVersion>> versionsByPatchline = new LinkedHashMap<>();
        for (int index = 0; index < refreshOrder.size(); index++) {
            String patchline = refreshOrder.get(index);
            try {
                versionsByPatchline.put(patchline, hytaleAuthService.getAvailableVersions(settings, patchline));
            } catch (HytaleApiException ex) {
                if (ex.statusCode() != 429) {
                    throw ex;
                }
                return new HytaleVersionsPayload(
                        patchlines,
                        selectedPatchline,
                        versionsByPatchline,
                        refreshOrder.subList(index, refreshOrder.size()),
                        ex
                );
            }
        }
        return new HytaleVersionsPayload(patchlines, selectedPatchline, versionsByPatchline, List.of(), null);
    }

    private List<String> refreshPatchlineOrder(
            LauncherSettings settings,
            String accountId,
            String platform,
            List<String> patchlines,
            String selectedPatchline
    ) {
        LinkedHashSet<String> available = new LinkedHashSet<>(orderedPatchlines(patchlines));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        settings.pendingHytalePatchlines(accountId, platform).stream()
                .map(HytaleApiClient::normalizeBranch)
                .filter(available::contains)
                .forEach(ordered::add);
        String selected = HytaleApiClient.normalizeBranch(selectedPatchline);
        if (available.contains(selected)) {
            ordered.add(selected);
        }
        ordered.addAll(available);
        return List.copyOf(ordered);
    }

    private boolean applyCachedHytaleVersions(LauncherSettings settings, HytaleAuthSession session, LauncherSettingsForm form) {
        String accountId = LauncherSettings.hytaleAccountId(session);
        if (accountId.isBlank()) {
            return false;
        }
        String platform = hytaleVersionCachePlatform();
        List<String> cachedPatchlines = settings.cachedHytalePatchlines(accountId, platform);
        String selectedPatchline = cachedPatchlines.isEmpty()
                ? HytaleApiClient.normalizeBranch(settings.getHytaleBranch())
                : selectedPatchline(settings.getHytaleBranch(), cachedPatchlines);
        if (!cachedPatchlines.isEmpty()) {
            suppressBranchVersionLoad = true;
            try {
                form.setHytalePatchlines(cachedPatchlines, selectedPatchline);
            } finally {
                suppressBranchVersionLoad = false;
            }
            settings.setHytaleBranch(form.hytaleBranchCombo().getValue());
        }
        List<HytaleVersion> cachedVersions = settings.cachedHytaleVersions(accountId, platform, selectedPatchline);
        if (cachedVersions.isEmpty()) {
            return false;
        }
        form.hytaleVersionCombo().setPromptText("Choose build");
        form.hytaleVersionCombo().setItems(FXCollections.observableArrayList(cachedVersions));
        settingsController.selectConfiguredHytaleBuild();
        settingsController.applyFromFields();
        buildMetric.setText(selectedVersionLabel(settings));
        return true;
    }

    private void cacheHytaleVersions(LauncherSettings settings, HytaleAuthSession session, HytaleVersionsPayload payload) {
        String accountId = LauncherSettings.hytaleAccountId(session);
        if (accountId.isBlank() || payload == null) {
            return;
        }
        String platform = hytaleVersionCachePlatform();
        settings.cacheHytalePatchlines(accountId, platform, payload.patchlines());
        settings.cachePendingHytalePatchlines(accountId, platform, payload.pendingPatchlines());
        payload.versionsByPatchline().forEach((patchline, versions) ->
                settings.cacheHytaleVersions(accountId, platform, patchline, versions));
        settingsController.saveCurrentSettings();
    }

    private static String hytaleVersionCachePlatform() {
        return HytalePlatform.os() + "/" + HytalePlatform.arch();
    }

    private String hytaleVersionLoadMessage(HytaleVersionsPayload payload, int selectedBuildCount, long labeledVersions) {
        int loadedPatchlines = payload.versionsByPatchline().size();
        int totalPatchlines = payload.patchlines().size();
        String selectedLabel = LauncherSettingsForm.hytalePatchlineLabel(payload.selectedPatchline());
        if (payload.rateLimit() != null) {
            String next = payload.pendingPatchlines().isEmpty()
                    ? ""
                    : " Next refresh will start with " + LauncherSettingsForm.hytalePatchlineLabel(payload.pendingPatchlines().getFirst()) + ".";
            return "Cached Hytale builds for " + loadedPatchlines + "/" + totalPatchlines
                    + " patchlines before rate limiting." + next;
        }
        if (selectedBuildCount == 0) {
            return "Hytale did not expose any " + selectedLabel + " builds for this platform.";
        }
        return "Cached Hytale builds for " + loadedPatchlines + "/" + totalPatchlines
                + " patchlines. " + selectedLabel + " has " + selectedBuildCount + " build" + plural(selectedBuildCount)
                + (labeledVersions == 0 ? "." : " with " + labeledVersions + " official version label" + plural((int) labeledVersions) + ".");
    }

    private void scheduleHytaleVersionRetry(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        long scheduledAt = System.currentTimeMillis() + delayMillis;
        if (versionRetryScheduledAtMillis >= scheduledAt - 500) {
            return;
        }
        versionRetryScheduledAtMillis = scheduledAt;
        CompletableFuture.delayedExecutor(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS, executor)
                .execute(() -> Platform.runLater(() -> {
                    if (System.currentTimeMillis() + 500 >= versionLoadRetryAfterMillis) {
                        loadHytaleVersions(false);
                    }
                }));
    }

    public void launchHytale() {
        if (isHytaleRunning()) {
            feedback.showToast("Already running", "Hytale is already running.");
            return;
        }
        settingsController.saveFromFields(false);
        feedback.runAsync("Launching Hytale...", () -> hytaleGameLauncher.launch(settingsController.settings()), result -> {
            hytaleProcess = result.process();
            long startedAtMillis = System.currentTimeMillis();
            discordRichPresence.showPlayingHytale(selectedVersionLabel(settingsController.settings()), startedAtMillis);
            monitorHytaleProcess(result, startedAtMillis);
            String build = settingsController.settings().getHytaleBuild() > 0
                    ? " build " + settingsController.settings().getHytaleBuild()
                    : "";
            feedback.log("Launched Hytale" + build + " as " + result.username() + ".");
            feedback.showToast("Hytale ready", "Launching as " + result.username() + ".");
            syncMetrics();
        });
    }

    private Node buildView() {
        configureSelectors();

        StackPane root = new StackPane();
        root.setUserData(LauncherView.PLAY);
        root.getStyleClass().addAll("view", "play-view");
        root.setMinHeight(0);
        root.setPrefHeight(720);
        root.setMaxHeight(Double.MAX_VALUE);

        HBox shell = new HBox(18);
        shell.getStyleClass().add("play-shell");
        shell.setAlignment(Pos.CENTER_LEFT);
        shell.setFillHeight(true);
        shell.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(shell, Pos.CENTER);

        VBox stage = new VBox(24);
        stage.getStyleClass().add("play-stage");
        stage.setMinWidth(0);
        stage.getChildren().add(catalogStage());

        ScrollPane stageScroll = new ScrollPane(stage);
        stageScroll.getStyleClass().add("play-stage-scroll");
        stageScroll.setFitToWidth(true);
        stageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stageScroll.setMinWidth(0);
        stageScroll.viewportBoundsProperty().addListener((observable, previous, bounds) -> {
            int sections = bounds.getWidth() - 8 >= 820 ? 1 : 2;
            double available = bounds.getHeight() * 0.88 - 24 - (sections - 1) * 36;
            int capacity = Math.clamp((int) Math.floor((available / sections - 54 + 20) / 110),
                    1, CATALOG_SHELF_LIMIT);
            int count = Math.max(1, (int) Math.round(capacity * 2.0 / 3));
            for (CatalogShelf shelf : List.of(trendingShelf, newReleasesShelf)) {
                if (shelf.visibleCount != count) {
                    shelf.visibleCount = count;
                    if (!shelf.projects.isEmpty()) renderCatalogShelf(shelf, shelf.projects);
                }
            }
        });
        Node launch = launchBar();
        VBox.setMargin(launch, new Insets(0, 0, 24, 0));
        root.heightProperty().addListener((observable, previous, height) ->
                VBox.setMargin(launch, new Insets(0, 0, Math.clamp(height.doubleValue() * 0.06, 16, 64), 0)));
        VBox main = new VBox(18, stageScroll, launch);
        main.setMinWidth(0);
        VBox.setVgrow(stageScroll, Priority.ALWAYS);
        HBox.setHgrow(main, Priority.ALWAYS);

        setupPopover = setupPopover();
        setVisibleManaged(setupPopover, false);
        Node sidebar = sidebarFrame();
        HBox.setHgrow(sidebar, Priority.NEVER);
        shell.getChildren().addAll(main, sidebar);
        root.getChildren().addAll(shell, setupPopover);
        StackPane.setAlignment(setupPopover, Pos.CENTER);
        syncMetrics();
        return root;
    }

    private Node launchBar() {
        HBox bar = new HBox(launchControl());
        bar.getStyleClass().add("play-launch-bar");
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private Node catalogStage() {
        TilePane content = new TilePane(32, 36);
        content.getStyleClass().add("play-catalog-stage");
        content.setMinWidth(0);
        content.setPrefColumns(2);
        content.setTileAlignment(Pos.TOP_LEFT);
        content.prefTileWidthProperty().bind(Bindings.createDoubleBinding(
                () -> content.getWidth() >= 820 ? Math.floor((content.getWidth() - 32) / 2) : Math.floor(content.getWidth()),
                content.widthProperty()));
        content.getChildren().addAll(catalogShelf(trendingShelf, "Trending"), catalogShelf(newReleasesShelf, "New Releases"));
        return content;
    }

    private Node catalogShelf(CatalogShelf shelf, String title) {
        VBox section = new VBox(CATALOG_SECTION_GAP);
        section.getStyleClass().add("play-catalog-section");
        section.setMinWidth(0);
        section.setMaxWidth(Double.MAX_VALUE);
        section.setFillWidth(true);
        section.setAlignment(Pos.TOP_LEFT);
        section.getChildren().addAll(catalogHeader(shelf, title), catalogBody(shelf));
        return section;
    }

    private Node catalogHeader(CatalogShelf shelf, String title) {
        HBox header = new HBox(10);
        header.getStyleClass().add("play-catalog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinWidth(0);
        header.setMaxWidth(Double.MAX_VALUE);

        Label label = new Label(title);
        label.getStyleClass().add("play-catalog-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button browse = new Button("Browse All", LauncherIcons.icon(LauncherIcons.Glyph.EXTERNAL_LINK, 13));
        browse.getStyleClass().add("play-catalog-browse-all");
        browse.setTooltip(new Tooltip("Browse all " + title.toLowerCase()));
        browse.setAccessibleText("Browse all " + title.toLowerCase());
        browse.setOnAction(event -> onBrowseCatalog.accept(shelf.sort));

        header.getChildren().addAll(label, spacer, browse);
        return header;
    }

    private Node catalogBody(CatalogShelf shelf) {
        VBox body = new VBox();
        body.getStyleClass().add("play-catalog-body");
        body.setMinWidth(0);
        shelf.cardRow.getStyleClass().add("play-catalog-card-row");
        shelf.message.getStyleClass().add("play-catalog-message");
        shelf.message.setWrapText(true);
        shelf.message.setMaxWidth(Double.MAX_VALUE);
        body.getChildren().addAll(shelf.cardRow, shelf.message);
        showCatalogMessage(shelf, "Loading projects...");
        return body;
    }

    private Node launchControl() {
        VBox control = new VBox(9);
        control.getStyleClass().add("play-launch-control");
        control.setAlignment(Pos.CENTER);
        control.setMaxWidth(Region.USE_PREF_SIZE);

        StackPane split = new StackPane();
        split.getStyleClass().add("play-launch-split");
        split.setAlignment(Pos.CENTER);
        split.setMaxWidth(Region.USE_PREF_SIZE);

        Button play = new Button("Play");
        play.getStyleClass().add("play-launch-main");
        play.setFont(Font.font("Inter ExtraBold", PLAY_BUTTON_FONT_SIZE));
        play.setOnAction(event -> launchHytale());

        Button setup = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 18));
        setup.getStyleClass().add("play-launch-arrow");
        setup.setTooltip(new Tooltip("Select patchline"));
        setup.setAccessibleText("Select patchline");
        setup.setOnAction(event -> showLaunchDropdown(setup));
        StackPane.setAlignment(setup, Pos.CENTER_RIGHT);
        StackPane.setMargin(setup, new Insets(0, 7, 0, 0));

        split.getChildren().addAll(play, setup);
        control.getChildren().add(split);
        return control;
    }

    private VBox setupPopover() {
        VBox panel = new VBox(18);
        panel.getStyleClass().add("play-setup-popover");
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPrefWidth(430);
        panel.setMaxWidth(430);

        Label title = new Label("Launch Setup");
        title.getStyleClass().add("play-panel-title");
        Label status = hytaleStatus;
        status.getStyleClass().add("play-panel-status");

        panel.getChildren().addAll(title, status, setupActions());
        return panel;
    }

    private Node setupActions() {
        Button stop = dangerButton("Stop");
        stop.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.X, 15));
        stop.setOnAction(event -> stopHytale());

        FlowPane utilities = new FlowPane(10, 10, stop);
        utilities.getStyleClass().add("play-utility-actions");
        utilities.setAlignment(Pos.CENTER);
        return utilities;
    }

    private Node sidebarFrame() {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("play-sidebar-frame");
        frame.setMinHeight(0);
        frame.setMaxHeight(Double.MAX_VALUE);
        Node scroll = sidebar();
        frame.getChildren().add(scroll);
        return frame;
    }

    private Node sidebar() {
        VBox sidebar = new VBox(18);
        sidebar.getStyleClass().add("play-sidebar");
        sidebar.setMinWidth(SIDEBAR_WIDTH);
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMaxWidth(SIDEBAR_WIDTH);
        sidebar.getChildren().addAll(
                identitySection(),
                friendsSection(),
                newsSection()
        );

        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.getStyleClass().add("play-sidebar-scroll");
        scroll.setFitToWidth(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        double scrollWidth = SIDEBAR_WIDTH + SIDEBAR_SCROLL_GUTTER;
        scroll.setPrefWidth(scrollWidth);
        scroll.setMinWidth(scrollWidth);
        scroll.setMaxWidth(scrollWidth);
        scroll.setMinHeight(0);
        scroll.setPrefHeight(SIDEBAR_PREF_HEIGHT);
        scroll.setMaxHeight(Double.MAX_VALUE);
        return scroll;
    }

    private Node identitySection() {
        VBox section = sidebarSection("User");
        configureIdentityButton();
        section.getChildren().add(identityButton);
        return section;
    }

    private Node friendsSection() {
        VBox section = sidebarSection();
        section.getChildren().add(sectionHeader("Friends", LauncherIcons.Glyph.REFRESH_CW, "Refresh Hytale friends",
                () -> loadHytaleFriends(true)));
        friendsList.getStyleClass().add("play-friends-list");
        section.getChildren().add(friendsList);
        return section;
    }

    private Node newsSection() {
        VBox section = new VBox(16);
        section.getStyleClass().add("play-news-section");
        Node header = sectionHeader("News", LauncherIcons.Glyph.REFRESH_CW, "Refresh news", () -> {
            if (!blogPostsLoading) loadBlogPosts();
        });
        header.getStyleClass().add("play-news-header");
        newsList.getStyleClass().add("play-news-grid");
        newsList.setMinWidth(0);
        newsStatus.getStyleClass().add("play-sidebar-message");
        newsStatus.setWrapText(true);
        setVisibleManaged(newsStatus, false);
        moreNews.getStyleClass().addAll("btn", "secondary");
        moreNews.setOnAction(event -> appendNextBlogPosts());
        setVisibleManaged(moreNews, false);
        section.getChildren().addAll(header, newsStatus, newsList, moreNews);
        return section;
    }

    private VBox sidebarSection(String title) {
        VBox section = new VBox(11);
        section.getStyleClass().add("play-sidebar-section");
        Label heading = new Label(title);
        heading.getStyleClass().add("play-sidebar-section-title");
        section.getChildren().add(heading);
        return section;
    }

    private VBox sidebarSection() {
        VBox section = new VBox(11);
        section.getStyleClass().add("play-sidebar-section");
        return section;
    }

    private Node sectionHeader(String title, LauncherIcons.Glyph glyph, String tooltip, Runnable action) {
        HBox header = new HBox(10);
        header.getStyleClass().add("play-sidebar-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(title);
        label.getStyleClass().add("play-sidebar-section-title");
        label.setMaxHeight(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button button = new Button(null, LauncherIcons.icon(glyph, 15));
        button.getStyleClass().addAll("icon-btn", "play-sidebar-icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        button.setOnAction(event -> action.run());
        header.getChildren().addAll(label, spacer, button);
        return header;
    }

    private void configureIdentityButton() {
        if (identityButton.getGraphic() != null) {
            return;
        }
        identityButton.getStyleClass().add("play-identity-button");
        identityButton.setAccessibleText("Manage Hytale account and game profile");
        identityButton.setTooltip(new Tooltip("Manage Hytale account and game profile"));
        identityButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        identityButton.setMaxWidth(Double.MAX_VALUE);

        HBox content = new HBox(11);
        content.getStyleClass().add("play-identity-button-content");
        content.setAlignment(Pos.CENTER_LEFT);
        identityAvatar.getStyleClass().add("play-identity-avatar");
        sizeSquare(identityAvatar, IDENTITY_AVATAR_SIZE);
        updateImageAvatar(identityAvatar, "Hytale", IDENTITY_AVATAR_SIZE, PROFILE_AVATAR_RADIUS, "");

        VBox copy = new VBox(3);
        copy.setAlignment(Pos.CENTER_LEFT);
        identityTitle.getStyleClass().add("play-identity-title");
        identitySubtitle.getStyleClass().add("play-identity-subtitle");
        identityTitle.setMaxWidth(Double.MAX_VALUE);
        identitySubtitle.setMaxWidth(Double.MAX_VALUE);
        copy.getChildren().addAll(identityTitle, identitySubtitle);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        StackPane chevron = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 16));
        chevron.getStyleClass().add("play-identity-chevron");
        content.getChildren().addAll(identityAvatar, copy, spacer, chevron);
        identityButton.setGraphic(content);
        identityButton.setOnAction(event -> toggleIdentityMenu(identityButton));
    }

    private void configureSelectors() {
        LauncherSettingsForm form = settingsController.form();
        form.hytaleBranchCombo().setPromptText("Channel");
        form.hytaleVersionCombo().setPromptText("Load builds");
        form.hytaleVersionCombo().setConverter(new StringConverter<>() {
            @Override
            public String toString(HytaleVersion version) {
                return version == null ? "" : versionSwitchLabel(version);
            }

            @Override
            public HytaleVersion fromString(String value) {
                return form.hytaleVersionCombo().getItems().stream()
                        .filter(version -> versionSwitchLabel(version).equals(value))
                        .findFirst()
                        .orElse(null);
            }
        });
        form.hytaleVersionCombo().valueProperty().addListener((observable, previous, selected) -> {
            if (selected != null) {
                buildMetric.setText(versionSwitchLabel(selected));
            } else {
                buildMetric.setText(selectedVersionLabel(settingsController.settings()));
            }
        });
        form.hytaleBranchCombo().valueProperty().addListener((observable, previous, selected) -> {
            if (!suppressBranchVersionLoad && selected != null && !selected.equals(previous)) {
                loadedVersionsKey = "";
                syncPatchlineMetric(selected);
                maybeLoadHytaleVersions();
            }
        });
    }

    private void syncSidebarData(LauncherSettings settings, HytaleAuthSession session) {
        if (session == null) {
            loadedFriendsKey = "";
            friendsList.getChildren().clear();
        } else {
            String friendsKey = LauncherSettings.hytaleAccountId(session);
            if (!friendsKey.equals(loadedFriendsKey) && !friendsLoading) {
                loadHytaleFriends(false);
            }
        }
        if (!blogPostsLoaded && !blogPostsLoading) {
            loadBlogPosts();
        }
    }

    private void syncCatalogShelves(boolean force) {
        loadCatalogShelf(newReleasesShelf, force);
        loadCatalogShelf(trendingShelf, force);
    }

    private void loadCatalogShelf(CatalogShelf shelf, boolean force) {
        if (shelf.loading && !force) {
            return;
        }
        if (shelf.loaded && !force) {
            return;
        }
        shelf.loading = true;
        long requestId = ++shelf.requestId;
        showCatalogMessage(shelf, "Loading projects...");
        CompletableFuture.supplyAsync(() -> apiClient.searchProjects(catalogQuery(shelf.sort)), executor)
                .whenComplete((page, error) -> Platform.runLater(() -> {
                    if (requestId != shelf.requestId) {
                        return;
                    }
                    shelf.loading = false;
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        String detail = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                                ? "Try refreshing in a moment."
                                : cause.getMessage();
                        showCatalogMessage(shelf, "Could not load " + shelf.sort.title().toLowerCase() + ". " + detail);
                        return;
                    }
                    shelf.loaded = true;
                    renderCatalogShelf(shelf, page == null ? List.of() : page.content());
                }));
    }

    private ProjectSearchQuery catalogQuery(ProjectBrowseSort sort) {
        return new ProjectSearchQuery(
                "",
                null,
                null,
                sort.apiValue(),
                0,
                CATALOG_SHELF_LIMIT,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void renderCatalogShelf(CatalogShelf shelf, List<ProjectSummary> projects) {
        shelf.projects = List.copyOf(projects);
        shelf.cardRow.getChildren().clear();
        if (projects.isEmpty()) {
            showCatalogMessage(shelf, "No " + shelf.sort.title().toLowerCase() + " found.");
            return;
        }
        setVisibleManaged(shelf.message, false);
        setVisibleManaged(shelf.cardRow, true);
        int cardCount = Math.min(projects.size(), shelf.visibleCount);
        String selectedGameVersion = gameVersion.get();
        for (int index = 0; index < cardCount; index++) {
            ProjectSummary project = projects.get(index);
            shelf.cardRow.getChildren().add(catalogProjectCard(project, selectedGameVersion));
        }
    }

    private Node catalogProjectCard(ProjectSummary project, String selectedGameVersion) {
        Region card = (Region) projectCardFactory.create(project, ProjectCardViewStyle.COMPACT,
                selectedGameVersion, Boolean.TRUE.equals(favoriteResolver.apply(project.id())),
                onInstall, onOpenPage, onOpenCreator, onToggleFavorite, 400, 90);
        card.setMinWidth(0);
        card.setPrefWidth(Region.USE_COMPUTED_SIZE);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void showCatalogMessage(CatalogShelf shelf, String message) {
        shelf.cardRow.getChildren().clear();
        shelf.message.setText(message == null ? "" : message);
        setVisibleManaged(shelf.cardRow, false);
        setVisibleManaged(shelf.message, true);
    }

    private void resetCatalogShelf(CatalogShelf shelf) {
        shelf.requestId++;
        shelf.loading = false;
        shelf.loaded = false;
        shelf.cardRow.getChildren().clear();
    }

    private void syncIdentitySummary(LauncherSettings settings, HytaleAuthSession session) {
        if (session == null) {
            hytaleStatus.setText("Signed out");
            identityTitle.setText("Signed out");
            identitySubtitle.setText("");
            updateImageAvatar(identityAvatar, "Hytale", IDENTITY_AVATAR_SIZE, PROFILE_AVATAR_RADIUS, "");
            return;
        }

        List<HytaleProfile> profiles = profilesFor(session);
        HytaleProfile selectedProfile = selectedProfileFor(session);
        String selectedProfileName = selectedProfile.displayName();
        updateHytaleProfileAvatar(identityAvatar, selectedProfileName, IDENTITY_AVATAR_SIZE);
        hytaleStatus.setText("Ready as " + selectedProfileName);
        identityTitle.setText(selectedProfileName);
        String account = accountLabel(session);
        String profileCount = profiles.size() <= 1 ? "Hytale account" : profiles.size() + " profiles";
        identitySubtitle.setText(account.equals(selectedProfileName) ? profileCount : account + " - " + profileCount);
    }

    private String formatPlaytime(long seconds) {
        if (seconds <= 0) {
            return "No playtime yet";
        }
        long minutes = Math.max(1, seconds / 60);
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return days + "d " + (hours % 24) + "h played";
        }
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m played";
        }
        return minutes + "m played";
    }

    private void syncPlaytimeMetric(HytaleAuthSession session) {
        if (session == null || !session.hasRefreshToken()) {
            loadedPlaytimeKey = "";
            playtimeMetric.setText("No playtime yet");
            return;
        }
        if (playtimeLoading) {
            playtimeMetric.setText("Loading playtime...");
            return;
        }
        playtimeMetric.setText(formatPlaytime(selectedProfileFor(session).playtimeSeconds()));
    }

    private void loadHytalePlaytime(boolean force) {
        LauncherSettings settings = settingsController.settings();
        HytaleAuthSession session = settings.getHytaleAuthSession();
        if (session == null || !session.hasRefreshToken()) {
            loadedPlaytimeKey = "";
            playtimeMetric.setText("No playtime yet");
            return;
        }
        String playtimeKey = playtimeKey(session);
        if (playtimeLoading || (!force && playtimeKey.equals(loadedPlaytimeKey))) {
            return;
        }

        playtimeLoading = true;
        playtimeMetric.setText("Loading playtime...");
        CompletableFuture.supplyAsync(() -> hytaleAuthService.getProfilePlaytimeSeconds(settingsController.settings()), executor)
                .whenComplete((seconds, error) -> Platform.runLater(() -> {
                    playtimeLoading = false;
                    HytaleAuthSession activeSession = settingsController.settings().getHytaleAuthSession();
                    String activeKey = activeSession == null ? "" : playtimeKey(activeSession);
                    if (error != null && requiresHytaleSignIn(error)) {
                        loadedPlaytimeKey = "";
                        settingsController.reloadControls();
                        syncMetrics();
                        onHytaleAccountsChanged.run();
                        return;
                    }
                    if (!playtimeKey.equals(activeKey)) {
                        return;
                    }
                    if (error != null) {
                        loadedPlaytimeKey = playtimeKey;
                        playtimeMetric.setText("Playtime unavailable");
                        return;
                    }
                    loadedPlaytimeKey = playtimeKey;
                    playtimeMetric.setText(formatPlaytime(seconds == null ? 0 : seconds));
                }));
    }

    private void loadHytaleFriends(boolean force) {
        LauncherSettings settings = settingsController.settings();
        HytaleAuthSession session = settings.getHytaleAuthSession();
        if (session == null || !session.hasRefreshToken()) {
            loadedFriendsKey = "";
            friendsList.getChildren().clear();
            return;
        }
        String friendsKey = LauncherSettings.hytaleAccountId(session);
        if (friendsLoading || (!force && friendsKey.equals(loadedFriendsKey))) {
            return;
        }
        friendsLoading = true;
        renderFriendsLoading();
        CompletableFuture.supplyAsync(() -> hytaleAuthService.getFriends(settingsController.settings()), executor)
                .whenComplete((friends, error) -> Platform.runLater(() -> {
                    friendsLoading = false;
                    HytaleAuthSession activeSession = settingsController.settings().getHytaleAuthSession();
                    String activeKey = activeSession == null ? "" : LauncherSettings.hytaleAccountId(activeSession);
                    if (error != null && requiresHytaleSignIn(error)) {
                        loadedFriendsKey = "";
                        settingsController.reloadControls();
                        syncMetrics();
                        onHytaleAccountsChanged.run();
                        return;
                    }
                    if (!friendsKey.equals(activeKey)) {
                        return;
                    }
                    if (error != null) {
                        setFriendsMessage(friendsErrorMessage(error));
                        return;
                    }
                    loadedFriendsKey = friendsKey;
                    renderFriends(friends == null ? List.of() : friends);
                }));
    }

    private void renderFriendsLoading() {
        friendsList.getChildren().clear();
        for (int i = 0; i < 3; i++) friendsList.getChildren().add(LauncherSkeleton.of(
                friendRow(new HytaleFriend("", "Community friend", "Online", "", true))));
    }

    private void renderFriends(List<HytaleFriend> friends) {
        friendsList.getChildren().clear();
        if (friends.isEmpty()) {
            friendsList.getChildren().add(messageRow("No Hytale friends were returned for this account."));
            return;
        }
        friends.stream()
                .limit(FRIEND_LIMIT)
                .map(this::friendRow)
                .forEach(friendsList.getChildren()::add);
        if (friends.size() > FRIEND_LIMIT) {
            friendsList.getChildren().add(messageRow("+" + (friends.size() - FRIEND_LIMIT) + " more in-game."));
        }
    }

    private Node friendRow(HytaleFriend friend) {
        HBox row = new HBox(10);
        row.getStyleClass().add("play-friend-row");
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = friend.username().isBlank()
                ? avatar(friend.displayName(), FRIEND_AVATAR_SIZE, "play-friend-avatar", friend.avatarUrl())
                : hytaleProfileAvatar(friend.username(), FRIEND_AVATAR_SIZE, "play-friend-avatar");
        Region presence = new Region();
        presence.getStyleClass().addAll("play-friend-presence", friend.online() ? "online" : "offline");
        StackPane.setAlignment(presence, Pos.BOTTOM_RIGHT);
        avatar.getChildren().add(presence);

        VBox copy = new VBox(2);
        Label name = new Label(friend.displayName());
        name.getStyleClass().add("play-friend-name");
        Label status = new Label(friend.displayStatus());
        status.getStyleClass().add("play-friend-status");
        copy.getChildren().addAll(name, status);
        HBox.setHgrow(copy, Priority.ALWAYS);

        row.getChildren().addAll(avatar, copy);
        return row;
    }

    private void setFriendsMessage(String message) {
        friendsList.getChildren().setAll(messageRow(message));
    }

    private static String friendsErrorMessage(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof HytaleApiException hytaleEx && hytaleEx.requiresSignIn()) {
            return "Friends unavailable";
        }
        return "Friends are available in-game. Modtale could not read a launcher friend list yet.";
    }

    private static String hytaleBuildLoadErrorMessage(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof HytaleApiException hytaleEx && hytaleEx.statusCode() == 429) {
            long seconds = Math.max(1, versionLoadRetryDelayMillis(error) / 1000);
            return "Hytale build API is rate limited. Waiting " + seconds + "s before refreshing builds again.";
        }
        String message = cause == null ? "" : cause.getMessage();
        return message == null || message.isBlank()
                ? "Could not load Hytale builds."
                : "Could not load Hytale builds: " + message;
    }

    private static long versionLoadRetryDelayMillis(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof HytaleApiException hytaleEx && hytaleEx.statusCode() == 429) {
            return hytaleEx.retryAfterMillis() > 0 ? hytaleEx.retryAfterMillis() : 60_000;
        }
        return 15_000;
    }

    private static boolean requiresHytaleSignIn(Throwable error) {
        Throwable cause = unwrap(error);
        return cause instanceof HytaleApiException hytaleEx && hytaleEx.requiresSignIn();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause == null ? error : cause;
    }

    private void loadBlogPosts() {
        blogPostsLoading = true;
        renderNewsLoading();
        LauncherNewsFeed.load(modtaleNews::fetch,
                () -> hytaleAuthService.getAllBlogPosts().stream().map(post -> new LauncherNewsPost(
                        post.title(), post.url(), post.imageUrl(), post.publishedAt(), "Hytale")).toList(), executor)
                .thenAccept(result -> Platform.runLater(() -> {
                    blogPostsLoading = false;
                    blogPostsLoaded = true;
                    newsStatus.setText(result.failedSources().isEmpty() ? ""
                            : "Could not load " + String.join(" and ", result.failedSources()) + " news. Refresh to try again.");
                    setVisibleManaged(newsStatus, !result.failedSources().isEmpty());
                    renderInitialBlogPosts(result.posts());
                }));
    }

    private void renderNewsLoading() {
        newsList.getChildren().clear();
        setVisibleManaged(moreNews, false);
        setVisibleManaged(newsStatus, false);
        for (int i = 0; i < BLOG_POST_PAGE_SIZE; i++) newsList.getChildren().add(LauncherSkeleton.of(
                blogPostRow(new LauncherNewsPost("Loading news", "", "",
                        java.time.Instant.parse(LauncherSkeletonContent.DATE), "Modtale"))));
    }

    private void renderInitialBlogPosts(List<LauncherNewsPost> posts) {
        blogPosts = posts;
        renderedBlogPosts = 0;
        newsList.getChildren().clear();
        if (posts.isEmpty() && newsStatus.getText().isBlank()) {
            newsStatus.setText("No news articles found.");
            setVisibleManaged(newsStatus, true);
        }
        appendNextBlogPosts();
    }

    private void appendNextBlogPosts() {
        int nextCount = Math.min(renderedBlogPosts + BLOG_POST_PAGE_SIZE, blogPosts.size());
        for (int index = renderedBlogPosts; index < nextCount; index++) {
            newsList.getChildren().add(blogPostRow(blogPosts.get(index)));
        }
        renderedBlogPosts = nextCount;
        setVisibleManaged(moreNews, renderedBlogPosts < blogPosts.size());
    }

    private Node blogPostRow(LauncherNewsPost post) {
        VBox row = new VBox(7);
        row.getStyleClass().add("play-news-card");
        row.setAlignment(Pos.TOP_LEFT);
        row.setOnMouseClicked(event -> LauncherExternalLinks.open(post.url(), feedback::showToast));
        row.setFocusTraversable(true);
        row.setAccessibleRole(javafx.scene.AccessibleRole.HYPERLINK);
        row.setAccessibleText(post.source() + ": " + post.title());
        row.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER || event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                LauncherExternalLinks.open(post.url(), feedback::showToast);
                event.consume();
            }
        });
        row.setMinWidth(0);
        row.setMinHeight(Region.USE_PREF_SIZE);
        row.setMaxWidth(Double.MAX_VALUE);

        StackPane thumbnail = newsThumbnail(post);

        Label title = new Label(value(post.title(), "News"));
        title.getStyleClass().add("play-news-title");
        title.setWrapText(true);
        title.setMinHeight(Region.USE_PREF_SIZE);
        VBox copy = new VBox(title);
        copy.getStyleClass().add("play-news-copy");
        row.getChildren().addAll(thumbnail, copy);
        return row;
    }

    private StackPane newsThumbnail(LauncherNewsPost post) {
        Label initial = new Label(initialFor(post.title()));
        initial.getStyleClass().add("play-news-initial");
        StackPane thumbnail = new StackPane(initial) {
            @Override public javafx.geometry.Orientation getContentBias() {
                return javafx.geometry.Orientation.HORIZONTAL;
            }

            @Override protected double computePrefHeight(double width) {
                return (width < 0 ? NEWS_THUMBNAIL_WIDTH : width) * 9.0 / 16;
            }
        };
        thumbnail.getStyleClass().add("play-news-thumbnail");
        thumbnail.setMinSize(0, Region.USE_PREF_SIZE);
        thumbnail.setPrefWidth(NEWS_THUMBNAIL_WIDTH);
        thumbnail.setMaxSize(Double.MAX_VALUE, Region.USE_PREF_SIZE);
        if (post.imageUrl() != null && !post.imageUrl().isBlank()) {
            ImageView image = containedImageView(post.imageUrl(), NEWS_THUMBNAIL_WIDTH, NEWS_THUMBNAIL_HEIGHT);
            image.getStyleClass().add("play-news-image");
            Rectangle clip = new Rectangle(NEWS_THUMBNAIL_WIDTH, NEWS_THUMBNAIL_HEIGHT);
            clip.setArcWidth(14);
            clip.setArcHeight(14);
            image.fitWidthProperty().bind(thumbnail.widthProperty());
            image.fitHeightProperty().bind(thumbnail.heightProperty());
            clip.widthProperty().bind(thumbnail.widthProperty());
            clip.heightProperty().bind(thumbnail.heightProperty());
            image.setClip(clip);
            thumbnail.getChildren().add(image);
        }
        return thumbnail;
    }

    private ImageView containedImageView(String imageUrl, double width, double height) {
        Image image = cachedImage(imageUrl, 0, 0, true, false);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }

    private Node messageRow(String message) {
        Label label = new Label(message == null ? "" : message);
        label.getStyleClass().add("play-sidebar-message");
        label.setWrapText(true);
        return label;
    }

    private StackPane avatar(String name, double size, String styleClass, String imageUrl) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add(styleClass);
        sizeSquare(avatar, size);
        updateImageAvatar(avatar, name, size, size / 2.0, imageUrl);
        return avatar;
    }

    private StackPane hytaleProfileAvatar(String username, double size, String styleClass) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add(styleClass);
        sizeSquare(avatar, size);
        updateHytaleProfileAvatar(avatar, username, size);
        return avatar;
    }

    private void updateHytaleProfileAvatar(StackPane avatar, String username, double size) {
        updateImageAvatar(avatar, username, size, PROFILE_AVATAR_RADIUS, "");
        ImageView image = new ImageView();
        image.setFitWidth(size);
        image.setFitHeight(size);
        image.setPreserveRatio(true);
        image.setSmooth(true);
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(PROFILE_AVATAR_RADIUS * 2);
        clip.setArcHeight(PROFILE_AVATAR_RADIUS * 2);
        image.setClip(clip);
        avatar.getChildren().add(image);
        avatarClient.avatarUrl(username).whenComplete((url, error) -> Platform.runLater(() -> {
            if (error == null && avatar.getChildren().contains(image)) {
                Image loaded = cachedImage(url, size, size, true, true);
                image.setImage(loaded);
                Node initial = avatar.getChildren().getFirst();
                initial.visibleProperty().bind(Bindings.createBooleanBinding(
                        () -> loaded.getProgress() < 1 || loaded.isError(),
                        loaded.progressProperty(), loaded.errorProperty()));
            }
        }));
    }

    private void updateImageAvatar(StackPane avatar, String name, double size, double radius, String imageUrl) {
        avatar.getChildren().clear();
        Label initial = new Label(initialFor(name));
        initial.getStyleClass().add("play-avatar-initial");
        avatar.getChildren().add(initial);
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        Image image = cachedImage(imageUrl, size, size, true, true);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setSmooth(true);
        Rectangle clip = new Rectangle(size, size);
        double arc = Math.min(size, radius * 2.0);
        clip.setArcWidth(arc);
        clip.setArcHeight(arc);
        imageView.setClip(clip);
        avatar.getChildren().add(imageView);
    }

    private void sizeSquare(Region node, double size) {
        node.setMinSize(size, size);
        node.setPrefSize(size, size);
        node.setMaxSize(size, size);
    }

    private void sizeRegion(Region node, double width, double height) {
        node.setMinSize(width, height);
        node.setPrefSize(width, height);
        node.setMaxSize(width, height);
    }

    private Image cachedImage(String imageUrl, double requestedWidth, double requestedHeight, boolean preserveRatio, boolean resize) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String key = imageUrl + "|" + requestedWidth + "x" + requestedHeight + "|" + preserveRatio + "|" + resize;
        return imageCache.computeIfAbsent(key, ignored -> {
            Image image = resize
                    ? new Image(imageUrl, requestedWidth, requestedHeight, preserveRatio, true, true)
                    : new Image(imageUrl, true);
            image.errorProperty().addListener((observable, previous, failed) -> {
                if (Boolean.TRUE.equals(failed)) {
                    imageCache.remove(key, image);
                }
            });
            return image;
        });
    }

    private String initialFor(String value) {
        String text = value(value, "H");
        return text.substring(0, 1).toUpperCase();
    }

    private String formatBlogDate(Instant publishedAt) {
        if (publishedAt == null || publishedAt.equals(Instant.EPOCH)) {
            return "Hytale Blog";
        }
        int postYear = publishedAt.atZone(ZoneId.systemDefault()).getYear();
        if (postYear == Year.now().getValue()) {
            return BLOG_DATE.format(publishedAt);
        }
        return BLOG_DATE_WITH_YEAR.format(publishedAt);
    }

    private void toggleIdentityMenu(Node owner) {
        if (identityMenu != null && identityMenu.isShowing()) {
            identityMenu.hide();
            return;
        }
        if (System.currentTimeMillis() - identityMenuHiddenAtMillis < 180) {
            return;
        }
        showIdentityMenu(owner);
    }

    private void showIdentityMenu(Node owner) {
        if (identityMenu != null) {
            identityMenu.hide();
        }
        ContextMenu menu = new ContextMenu();
        identityMenu = menu;
        menu.getStyleClass().add("play-identity-menu");
        menu.setOnHidden(event -> {
            identityMenuHiddenAtMillis = System.currentTimeMillis();
            if (identityMenu == menu) {
                identityMenu = null;
            }
        });

        LauncherSettings settings = settingsController.settings();
        HytaleAuthSession activeSession = settings.getHytaleAuthSession();
        String activeAccountId = activeSession == null ? "" : LauncherSettings.hytaleAccountId(activeSession);
        String activeProfileId = activeSession == null ? "" : value(activeSession.getUuid(), "");
        List<HytaleAuthSession> sessions = settings.getHytaleAuthSessions();
        for (HytaleAuthSession session : sessions) {
            CustomMenuItem header = new CustomMenuItem(identityMenuHeader(session, menu), false);
            header.setHideOnClick(false);
            menu.getItems().add(header);

            List<HytaleProfile> profiles = profilesFor(session);
            if (profiles.isEmpty()) {
                CustomMenuItem empty = new CustomMenuItem(identityMenuMessage("No game profiles found"), false);
                empty.setHideOnClick(false);
                menu.getItems().add(empty);
                continue;
            }
            String accountId = LauncherSettings.hytaleAccountId(session);
            for (HytaleProfile profile : profiles) {
                boolean selected = accountId.equals(activeAccountId) && profile.uuid().equals(activeProfileId);
                CustomMenuItem item = new CustomMenuItem(identityMenuProfileRow(profile, selected), true);
                item.setOnAction(event -> selectIdentity(session, profile));
                menu.getItems().add(item);
            }
        }

        CustomMenuItem addAccount = new CustomMenuItem(identityMenuActionRow(
                LauncherIcons.Glyph.USER,
                "Add Hytale account",
                "Open browser sign-in",
                false
        ), true);
        addAccount.setOnAction(event -> signInHytale());
        menu.getItems().add(addAccount);

        menu.show(owner, Side.BOTTOM, 0, 8);
    }

    private Node identityMenuHeader(HytaleAuthSession session, ContextMenu menu) {
        HBox row = new HBox(9);
        row.getStyleClass().add("play-identity-menu-header");
        row.setAlignment(Pos.CENTER_LEFT);
        HytaleProfile selectedProfile = selectedProfileFor(session);
        StackPane icon = hytaleProfileAvatar(selectedProfile.displayName(), IDENTITY_MENU_AVATAR_SIZE, "play-identity-menu-avatar");

        VBox copy = new VBox(2);
        Label name = new Label(accountLabel(session));
        name.getStyleClass().add("play-identity-menu-title");
        int profileCount = profilesFor(session).size();
        Label detail = new Label(profileCount + " game profile" + plural(profileCount));
        detail.getStyleClass().add("play-identity-menu-subtitle");
        copy.getChildren().addAll(name, detail);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Button logout = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.TRASH, 13));
        logout.getStyleClass().addAll("icon-btn", "play-identity-menu-logout");
        logout.setTooltip(new Tooltip("Log out Hytale account"));
        logout.setAccessibleText("Log out " + accountLabel(session));
        logout.setOnAction(event -> {
            event.consume();
            menu.hide();
            logoutHytaleAccount(session);
        });

        row.getChildren().addAll(icon, copy, logout);
        return row;
    }

    private Node identityMenuProfileRow(HytaleProfile profile, boolean selected) {
        HBox row = new HBox(9);
        row.getStyleClass().add("play-identity-menu-profile");
        if (selected) {
            row.getStyleClass().add("selected");
        }
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane profileAvatar = hytaleProfileAvatar(profile.displayName(), IDENTITY_MENU_AVATAR_SIZE, "play-identity-menu-avatar");
        if (selected) {
            StackPane badge = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 8));
            badge.getStyleClass().add("play-identity-menu-avatar-badge");
            StackPane.setAlignment(badge, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(badge, new Insets(0, -2, -2, 0));
            profileAvatar.getChildren().add(badge);
        }

        VBox copy = new VBox(2);
        Label name = new Label(value(profile.displayName(), "Game profile"));
        name.getStyleClass().add("play-identity-menu-title");
        Label detail = new Label(selected ? "Selected game profile" : "Use this game profile");
        detail.getStyleClass().add("play-identity-menu-subtitle");
        copy.getChildren().addAll(name, detail);
        HBox.setHgrow(copy, Priority.ALWAYS);
        row.getChildren().addAll(profileAvatar, copy);
        return row;
    }

    private Node identityMenuActionRow(LauncherIcons.Glyph glyph, String title, String subtitle, boolean danger) {
        HBox row = new HBox(9);
        row.getStyleClass().add("play-identity-menu-action");
        if (danger) {
            row.getStyleClass().add("danger");
        }
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = new StackPane(LauncherIcons.icon(glyph, 15));
        icon.getStyleClass().add("play-identity-menu-icon");
        VBox copy = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("play-identity-menu-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("play-identity-menu-subtitle");
        copy.getChildren().addAll(titleLabel, subtitleLabel);
        HBox.setHgrow(copy, Priority.ALWAYS);
        row.getChildren().addAll(icon, copy);
        return row;
    }

    private Node identityMenuMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("play-identity-menu-message");
        return label;
    }

    private void selectIdentity(HytaleAuthSession session, HytaleProfile profile) {
        if (session == null || profile == null) {
            return;
        }
        String accountId = LauncherSettings.hytaleAccountId(session);
        boolean accountChanged = !accountId.equals(settingsController.settings().getActiveHytaleAccountId());
        if (accountChanged) {
            hytaleAuthService.selectAccount(settingsController.settings(), accountId);
            settingsController.reloadFromStore();
        }
        HytaleAuthSession active = settingsController.settings().getHytaleAuthSession();
        if (active == null || !profile.uuid().equals(active.getUuid())) {
            hytaleAuthService.selectProfile(settingsController.settings(), profile);
            settingsController.reloadFromStore();
        }
        feedback.log("Selected Hytale profile " + profile.displayName() + ".");
        syncMetrics();
    }

    private List<HytaleProfile> profilesFor(HytaleAuthSession session) {
        if (session == null) {
            return List.of();
        }
        List<HytaleProfile> profiles = session.getProfiles();
        if (!profiles.isEmpty()) {
            return profiles;
        }
        if (session.getUuid() == null || session.getUuid().isBlank()) {
            return List.of();
        }
        return List.of(new HytaleProfile(session.getUsername(), session.getUuid(), session.getAccountOwnerId()));
    }

    private HytaleProfile selectedProfileFor(HytaleAuthSession session) {
        if (session == null) {
            return new HytaleProfile("", "", "");
        }
        List<HytaleProfile> profiles = profilesFor(session);
        if (profiles.isEmpty()) {
            return new HytaleProfile(session.getUsername(), session.getUuid(), session.getAccountOwnerId());
        }
        String selectedUuid = value(session.getUuid(), "");
        return profiles.stream()
                .filter(profile -> profile.uuid().equals(selectedUuid))
                .findFirst()
                .orElseGet(() -> profiles.get(0));
    }

    private String accountLabel(HytaleAuthSession session) {
        return value(session == null ? "" : session.getUsername(), "Hytale account");
    }

    private String selectedVersionLabel(LauncherSettings settings) {
        LauncherSettingsForm form = settingsController.form();
        HytaleVersion selected = form.hytaleVersionCombo().getValue();
        if (selected != null) {
            return versionSwitchLabel(selected);
        }
        if (settings.getHytaleBuild() > 0) {
            for (HytaleVersion version : form.hytaleVersionCombo().getItems()) {
                if (version.build() == settings.getHytaleBuild()) {
                    return versionSwitchLabel(version);
                }
            }
            Map<Integer, String> labels = HytaleGameVersionResolver.resolveBuildVersions(settings);
            String label = labels.get(settings.getHytaleBuild());
            return label == null || label.isBlank() ? "Build " + settings.getHytaleBuild() : label;
        }
        return "Unset";
    }

    private String versionSwitchLabel(HytaleVersion version) {
        return version == null ? "" : version.displayVersion();
    }

    private void syncPatchlineMetric(String selectedPatchline) {
        String patchline = HytaleApiClient.normalizeBranch(selectedPatchline);
        boolean showPatchline = !"release".equals(patchline);
        patchlineMetric.setText(launchPatchlineLabel(patchline));
        Node container = patchlineMetric.getParent();
        setVisibleManaged(container == null ? patchlineMetric : container, showPatchline);
    }

    private String launchPatchlineLabel(String patchline) {
        String normalized = HytaleApiClient.normalizeBranch(patchline);
        return switch (normalized) {
            case "release" -> "Latest release";
            case "pre-release" -> "Pre-release";
            default -> normalized;
        };
    }

    private void signInHytale() {
        settingsController.saveFromFields(false);
        feedback.runAsync("Opening Hytale sign-in in your browser...", () ->
                hytaleAuthService.loginAndSave(settingsController.settings()), session -> {
            settingsController.reloadFromStore();
            loadedPlaytimeKey = "";
            feedback.log("Signed in with Hytale as " + session + ".");
            feedback.showToast("Hytale ready", "Signed in as " + session + ".");
            syncMetrics();
            onHytaleAccountsChanged.run();
        });
    }

    private void logoutHytaleAccount(HytaleAuthSession session) {
        if (isHytaleRunning()) {
            feedback.showToast("Hytale is running", "Stop Hytale before logging out of this account.");
            return;
        }
        if (session == null) {
            return;
        }
        LauncherSettings settings = settingsController.settings();
        String accountId = LauncherSettings.hytaleAccountId(session);
        boolean activeAccount = accountId.equals(settings.getActiveHytaleAccountId());
        hytaleAuthService.logoutAccount(settings, accountId);
        if (activeAccount) {
            settingsController.form().hytaleVersionCombo().getItems().clear();
            loadedVersionsKey = "";
            loadedFriendsKey = "";
            loadedPlaytimeKey = "";
        }
        settingsController.reloadFromStore();
        feedback.log("Logged out of Hytale account " + accountLabel(session) + ".");
        syncMetrics();
        onHytaleAccountsChanged.run();
    }

    private void stopHytale() {
        Process process = hytaleProcess;
        if (process == null || !process.isAlive()) {
            hytaleProcess = null;
            feedback.showToast("Not running", "Hytale is not running.");
            return;
        }
        process.destroy();
        feedback.log("Asked Hytale to stop.");
    }

    private void monitorHytaleProcess(HytaleLaunchResult result, long startedAtMillis) {
        Process process = result.process();
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    feedback.log("[Hytale] " + line);
                }
            } catch (IOException ex) {
                LOG.warn("Could not read Hytale output.", ex);
                feedback.log("Could not read Hytale output: " + ex.getMessage());
            }
            try {
                int exitCode = process.waitFor();
                if (hytaleProcess == process) {
                    hytaleProcess = null;
                }
                long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - startedAtMillis) / 1000);
                if (elapsedSeconds > 0) {
                    LauncherSettings settings = settingsController.settings();
                    settings.addHytalePlaytimeSeconds(elapsedSeconds);
                    settingsController.saveCurrentSettings();
                }
                feedback.log("Hytale exited with code " + exitCode + ".");
                discordRichPresence.showLauncher();
                javafx.application.Platform.runLater(() -> {
                    loadedPlaytimeKey = "";
                    syncMetrics();
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while waiting for Hytale process.", ex);
            }
        }, executor);
    }

    private boolean isHytaleRunning() {
        Process process = hytaleProcess;
        return process != null && process.isAlive();
    }

    private void showLaunchDropdown(Node owner) {
        LauncherSettingsForm form = settingsController.form();
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("play-launch-menu");

        List<String> patchlines = form.hytaleBranchCombo().getItems().isEmpty()
                ? List.of("release", "pre-release")
                : List.copyOf(form.hytaleBranchCombo().getItems());
        String selected = HytaleApiClient.normalizeBranch(form.hytaleBranchCombo().getValue());
        for (String patchline : dropdownPatchlines(patchlines)) {
            String normalized = HytaleApiClient.normalizeBranch(patchline);
            menu.getItems().add(launchMenuItem(
                    LauncherSettingsForm.hytalePatchlineLabel(normalized),
                    LauncherIcons.Glyph.SLIDERS,
                    normalized.equals(selected),
                    () -> selectLaunchPatchline(normalized)
            ));
        }
        menu.show(owner, Side.BOTTOM, 0, 8);
    }

    private List<String> dropdownPatchlines(List<String> patchlines) {
        List<String> normalized = patchlines == null ? List.of() : patchlines.stream()
                .map(HytaleApiClient::normalizeBranch)
                .distinct()
                .toList();
        List<String> ordered = new ArrayList<>();
        if (normalized.contains("pre-release")) {
            ordered.add("pre-release");
        }
        if (normalized.contains("release")) {
            ordered.add("release");
        }
        normalized.stream().filter(patchline -> patchline.startsWith("v")).forEach(ordered::add);
        normalized.stream()
                .filter(patchline -> !ordered.contains(patchline))
                .forEach(ordered::add);
        return ordered;
    }

    private List<String> orderedPatchlines(List<String> patchlines) {
        List<String> normalized = patchlines == null ? List.of() : patchlines.stream()
                .map(HytaleApiClient::normalizeBranch)
                .distinct()
                .toList();
        List<String> ordered = new ArrayList<>();
        normalized.stream().filter(patchline -> patchline.startsWith("v")).forEach(ordered::add);
        if (normalized.contains("release")) {
            ordered.add("release");
        }
        if (normalized.contains("pre-release")) {
            ordered.add("pre-release");
        }
        normalized.stream()
                .filter(patchline -> !ordered.contains(patchline))
                .forEach(ordered::add);
        return ordered;
    }

    private MenuItem launchMenuItem(String label, LauncherIcons.Glyph glyph, boolean selected, Runnable action) {
        MenuItem item = new MenuItem(label, LauncherIcons.icon(selected ? LauncherIcons.Glyph.CHECK : glyph, 14));
        item.setOnAction(event -> action.run());
        return item;
    }

    private void selectLaunchPatchline(String patchline) {
        LauncherSettingsForm form = settingsController.form();
        String normalized = HytaleApiClient.normalizeBranch(patchline);
        if (normalized.equals(HytaleApiClient.normalizeBranch(form.hytaleBranchCombo().getValue()))) {
            return;
        }
        form.hytaleVersionCombo().setValue(null);
        form.hytaleVersionCombo().getItems().clear();
        suppressBranchVersionLoad = true;
        try {
            form.hytaleBranchCombo().setValue(normalized);
        } finally {
            suppressBranchVersionLoad = false;
        }
        settingsController.applyFromFields();
        settingsController.settings().setHytaleBuild(0);
        settingsController.saveCurrentSettings();
        loadedVersionsKey = "";
        buildMetric.setText("Loading");
        loadHytaleVersions(false);
        syncMetrics();
    }

    private void maybeLoadHytaleVersions() {
        loadHytaleVersions(false);
    }

    private void maybeLoadHytalePlaytime() {
        loadHytalePlaytime(false);
    }

    private String versionsKey(LauncherSettings settings, HytaleAuthSession session) {
        return LauncherSettings.hytaleAccountId(session) + ":" + settings.getHytaleBranch();
    }

    private String selectedPatchline(String requestedPatchline, List<String> availablePatchlines) {
        String normalized = HytaleApiClient.normalizeBranch(requestedPatchline);
        if (availablePatchlines != null && availablePatchlines.contains(normalized)) {
            return normalized;
        }
        return "release";
    }

    private String playtimeKey(HytaleAuthSession session) {
        return LauncherSettings.hytaleAccountId(session) + ":" + selectedProfileFor(session).uuid();
    }

    private String plural(int count) {
        return count == 1 ? "" : "s";
    }

    private record HytaleVersionsPayload(
            List<String> patchlines,
            String selectedPatchline,
            Map<String, List<HytaleVersion>> versionsByPatchline,
            List<String> pendingPatchlines,
            HytaleApiException rateLimit
    ) {

        private List<HytaleVersion> versionsForSelectedPatchline() {
            return versionsByPatchline.getOrDefault(selectedPatchline, List.of());
        }
    }

    private static final class CatalogShelf {
        private final ProjectBrowseSort sort;
        private final VBox cardRow = new VBox(12);
        private final Label message = new Label();

        private int visibleCount = 3;
        private List<ProjectSummary> projects = List.of();
        private boolean loading;
        private boolean loaded;
        private long requestId;

        private CatalogShelf(ProjectBrowseSort sort) {
            this.sort = sort;
        }
    }
}
