package net.modtale.launcher.ui.shell;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;
import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import net.modtale.launcher.LauncherPerformanceProbe;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.protocol.LauncherProtocolRequest;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.account.LauncherAccountMenu;
import net.modtale.launcher.ui.account.LauncherHytaleAuthGate;
import net.modtale.launcher.ui.activity.LauncherFollowingController;
import net.modtale.launcher.ui.activity.LauncherNotificationsController;
import net.modtale.launcher.ui.activity.LauncherNotificationsMenu;
import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.browse.controls.BrowseOptions;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherFonts;
import net.modtale.launcher.ui.common.LauncherLayout;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherScrollSupport;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.feedback.LauncherFeedbackView;
import net.modtale.launcher.ui.library.LauncherLibraryController;
import net.modtale.launcher.ui.play.LauncherPlayController;
import net.modtale.launcher.ui.project.ProjectPageController;
import net.modtale.launcher.ui.settings.LauncherSettingsController;

public final class LauncherShell {

    private static final double DEFAULT_STAGE_WIDTH = 1320;
    private static final double DEFAULT_STAGE_HEIGHT = 880;
    private static final double MIN_STAGE_WIDTH = 640;
    private static final double MIN_STAGE_HEIGHT = 480;
    private static final double STAGE_SCREEN_MARGIN = 48;
    private static final double WORKSPACE_SPACING = 44;
    private static final double RAIL_WIDTH = 238;
    private static final double BRAND_LOGO_HOVER_SCALE = 1.06;
    private static final Duration BRAND_LOGO_SCALE_DURATION = Duration.millis(140);
    private static final double NAVBAR_TEXT_FONT_SIZE = 14;
    private static final double RESIZE_HANDLE_SIZE = 8;
    private static final String CUSTOM_WINDOW_CHROME_PROPERTY = "modtale.launcher.customWindowChrome";
    private static final Duration[] STAGE_VISIBILITY_RETRY_DELAYS = {
            Duration.millis(125),
            Duration.millis(500),
            Duration.seconds(1),
            Duration.seconds(2),
            Duration.seconds(4)
    };

    private final LauncherNavigation navigation;
    private final LauncherFeedbackView feedbackView;
    private final LauncherFeedback feedback;
    private final LauncherAccountController accountController;
    private final ProjectBrowseController browseController;
    private final ProjectPageController projectPageController;
    private final LauncherPlayController playController;
    private final LauncherLibraryController libraryController;
    private final LauncherNotificationsController notificationsController;
    private final LauncherNotificationsMenu notificationsMenu;
    private final LauncherFollowingController followingController;
    private final LauncherSettingsController settingsController;
    private final LauncherScrollSupport scrollSupport;
    private final LauncherHytaleAuthGate hytaleAuthGate;
    private final LauncherBrowseMenu browseMenu;
    private final LauncherAccountMenu accountMenu;
    private final LauncherToolbarActions toolbarActions;
    private final Map<LauncherView, Node> navButtons = new LinkedHashMap<>();
    private final Map<BrowseOptions.BrowseViewOption, Button> railButtons = new LinkedHashMap<>();
    private final List<PauseTransition> stageVisibilityRetries = new ArrayList<>();
    private final StackPane viewDeck;
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();

    private Stage stage;
    private Application.Parameters launchParameters;
    private BorderPane appRoot;
    private Node navbarNode;
    private StackPane sceneLayer;
    private HBox workspaceRoot;
    private Node railNode;
    private HBox mainToolbar;
    private ScrollPane contentScroll;
    private VBox contentBody;
    private Node hytaleGateNode;
    private Node windowControlsNode;
    private boolean appUnlocked;
    private boolean modtaleInitialLoadsStarted;
    private boolean startupSnapshotScheduled;
    private boolean startupProtocolHandled;
    private boolean undecoratedWindow;
    private boolean nativeWindowMoveInProgress;
    private LinuxWindowManagerSupport.ResizeDirection fallbackResizeDirection;
    private double windowDragOffsetX;
    private double windowDragOffsetY;
    private double resizeStartScreenX;
    private double resizeStartScreenY;
    private double resizeStartStageX;
    private double resizeStartStageY;
    private double resizeStartWidth;
    private double resizeStartHeight;

    public LauncherShell(
            LauncherNavigation navigation,
            LauncherFeedbackView feedbackView,
            LauncherFeedback feedback,
            LauncherAccountController accountController,
            HytaleAuthService hytaleAuthService,
            ProjectBrowseController browseController,
            ProjectPageController projectPageController,
            LauncherPlayController playController,
            LauncherLibraryController libraryController,
            LauncherNotificationsController notificationsController,
            LauncherNotificationsMenu notificationsMenu,
            LauncherFollowingController followingController,
            LauncherSettingsController settingsController,
            LauncherScrollSupport scrollSupport,
            CachedImageLoader accountImageLoader,
            StackPane viewDeck
    ) {
        this.navigation = navigation;
        this.feedbackView = feedbackView;
        this.feedback = feedback;
        this.accountController = accountController;
        this.browseController = browseController;
        this.projectPageController = projectPageController;
        this.playController = playController;
        this.libraryController = libraryController;
        this.notificationsController = notificationsController;
        this.notificationsMenu = notificationsMenu;
        this.followingController = followingController;
        this.settingsController = settingsController;
        this.scrollSupport = scrollSupport;
        this.viewDeck = viewDeck;
        this.hytaleAuthGate = new LauncherHytaleAuthGate(
                hytaleAuthService,
                settingsController,
                feedback,
                this::unlock
        );
        this.browseMenu = new LauncherBrowseMenu(browseController, () -> sceneLayer, navigation::currentView);
        this.accountMenu = new LauncherAccountMenu(
                accountController,
                accountImageLoader,
                () -> sceneLayer,
                this::showView,
                navigation::currentView,
                followingController::showModal,
                () -> {
                    browseMenu.hide();
                    notificationsMenu.hide();
                    followingController.hideModal();
                }
        );
        this.notificationsMenu.attachOverlay(
                () -> sceneLayer,
                () -> {
                    browseMenu.hide();
                    accountMenu.hide();
                    followingController.hideModal();
                }
        );
        this.toolbarActions = new LauncherToolbarActions(
                browseController,
                playController,
                libraryController,
                settingsController
        );
    }

    public void start(Stage primaryStage, Application.Parameters parameters) {
        LauncherFonts.load();
        stage = primaryStage;
        launchParameters = parameters;
        undecoratedWindow = shouldUseUndecoratedWindow();
        if (undecoratedWindow) {
            primaryStage.initStyle(StageStyle.UNDECORATED);
        }
        StackPane root = new StackPane();
        sceneLayer = root;
        root.getStyleClass().add("app-root");
        appRoot = new BorderPane();
        navbarNode = navbar();
        appRoot.setTop(navbarNode);
        appRoot.setCenter(workspace());
        hytaleGateNode = hytaleAuthGate.view();
        if (undecoratedWindow) {
            configureWindowDrag(hytaleGateNode);
        }
        root.getChildren().addAll(appRoot, hytaleGateNode, feedbackView.toast());
        if (browseMenu.panel() != null) {
            root.getChildren().add(browseMenu.panel());
        }
        if (notificationsMenu.panel() != null) {
            root.getChildren().add(notificationsMenu.panel());
        }
        if (accountMenu.panel() != null) {
            root.getChildren().add(accountMenu.panel());
        }
        root.getChildren().add(followingController.modal());
        if (undecoratedWindow) {
            windowControlsNode = windowControls();
            root.getChildren().add(windowControlsNode);
            StackPane.setAlignment(windowControlsNode, Pos.TOP_RIGHT);
            StackPane.setMargin(windowControlsNode, new Insets(3, 4, 0, 0));
        }
        StackPane.setAlignment(feedbackView.toast(), Pos.BOTTOM_RIGHT);
        StackPane.setMargin(feedbackView.toast(), new Insets(0, 24, 24, 0));

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = boundedStageSize(DEFAULT_STAGE_WIDTH, MIN_STAGE_WIDTH, visualBounds.getWidth());
        double initialHeight = boundedStageSize(DEFAULT_STAGE_HEIGHT, MIN_STAGE_HEIGHT, visualBounds.getHeight());
        Scene scene = new Scene(root, initialWidth, initialHeight, Color.web("#0B1120"));
        scene.getStylesheets().add(Objects.requireNonNull(getClass()
                .getResource("/net/modtale/launcher/ui/nativefx/launcher.css")).toExternalForm());
        LauncherPerformanceProbe.install(scene);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::hideDropdownsOnOutsidePress);
        if (undecoratedWindow) {
            configureWindowResize(scene);
        }
        primaryStage.setTitle("Modtale Launcher");
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(boundedStageMinimum(MIN_STAGE_WIDTH, visualBounds.getWidth()));
        primaryStage.setMinHeight(boundedStageMinimum(MIN_STAGE_HEIGHT, visualBounds.getHeight()));
        primaryStage.setScene(scene);
        centerStage(primaryStage, visualBounds, initialWidth, initialHeight);

        buildViews();
        settingsController.reloadControls();
        if (hasLinkedHytaleAccount()) {
            unlock();
        } else {
            showHytaleGate("Sign in with Hytale to use Modtale Launcher.", true);
        }
        revealPrimaryStage(primaryStage);
        scheduleSnapshotIfRequested();
        Platform.runLater(() -> {
            if (!appUnlocked) {
                showHytaleGate("Sign in with Hytale to use Modtale Launcher.", true);
            }
            bringWindowControlsToFront();
            revealPrimaryStage(primaryStage);
        });
        scheduleStageVisibilityRetries(primaryStage);
    }

    public Node sceneRoot() {
        return stage == null || stage.getScene() == null ? null : stage.getScene().getRoot();
    }

    public VBox contentBody() {
        return contentBody;
    }

    public void unlock() {
        if (!hasLinkedHytaleAccount()) {
            lock();
            return;
        }
        if (appUnlocked) {
            playController.syncMetrics();
            return;
        }
        appUnlocked = true;
        setVisibleManaged(hytaleGateNode, false);
        setVisibleManaged(appRoot, true);
        setVisibleManaged(workspaceRoot, true);
        setVisibleManaged(windowControlsNode, undecoratedWindow);
        bringWindowControlsToFront();
        LauncherView requestedView = launchParameters == null
                ? LauncherView.defaultView()
                : LauncherView.fromId(launchParameters.getNamed().get("view"));
        showView(requestedView);
        if (accountController.isSignedIn()) {
            startModtaleInitialLoads();
        }
        handleStartupProtocolIfRequested();
    }

    public void lock() {
        appUnlocked = false;
        setVisibleManaged(appRoot, false);
        showHytaleGate("Sign in with Hytale to use Modtale Launcher.", true);
    }

    public void onHytaleAccountsChanged() {
        if (hasLinkedHytaleAccount()) {
            unlock();
        } else {
            lock();
        }
    }

    public void onModtaleSignedIn() {
        if (appUnlocked) {
            startModtaleInitialLoads();
            playController.refreshCatalogShelves();
        }
        accountMenu.updateSelected();
    }

    public void onModtaleSignedOut() {
        modtaleInitialLoadsStarted = false;
        playController.resetCatalogShelves();
        if (requiresModtaleSession(navigation.currentView())) {
            showView(LauncherView.PLAY);
        }
        accountMenu.hide();
        notificationsMenu.hide();
        accountMenu.updateSelected();
    }

    public void showView(LauncherView view) {
        if (!appUnlocked) {
            showHytaleGate("Sign in with Hytale to use Modtale Launcher.", true);
            return;
        }
        browseMenu.hide();
        notificationsMenu.hide();
        accountMenu.hide();
        followingController.hideModal();
        LauncherView nextView = view == null ? LauncherView.defaultView() : view;
        if (requiresModtaleSession(nextView) && !accountController.isSignedIn()) {
            feedback.showToast("Modtale sign-in required", "Sign in with Modtale to browse projects, update mods, or view account activity.");
            LauncherView fallback = requiresModtaleSession(navigation.currentView())
                    ? LauncherView.PLAY
                    : navigation.currentView();
            if (fallback != navigation.currentView()) {
                showView(fallback);
            }
            return;
        }
        navigation.activate(nextView);
        boolean webMode = nextView == LauncherView.PROJECT;
        boolean discoverMode = nextView == LauncherView.DISCOVER;
        boolean launcherPage = nextView == LauncherView.PLAY || nextView == LauncherView.LIBRARY;
        boolean libraryPage = nextView == LauncherView.LIBRARY;
        boolean documentMode = usesDocumentHeight(nextView);
        boolean playPage = nextView == LauncherView.PLAY;
        toggleStyleClass(sceneLayer, "play-screen", playPage);
        setVisibleManaged(navbarNode, true);
        setVisibleManaged(railNode, discoverMode);
        setVisibleManaged(mainToolbar, !webMode && !discoverMode && !launcherPage);
        if (nextView == LauncherView.NOTIFICATIONS) {
            notificationsController.refresh();
        } else if (nextView == LauncherView.LIBRARY) {
            libraryController.refresh();
        } else if (nextView == LauncherView.PLAY) {
            playController.syncMetrics();
        }
        if (workspaceRoot != null) {
            toggleStyleClass(workspaceRoot, "play-workspace", playPage);
            workspaceRoot.setSpacing(discoverMode ? WORKSPACE_SPACING : 0);
            workspaceRoot.setPadding(webMode
                    ? Insets.EMPTY
                    : libraryPage
                            ? LauncherLayout.navbarInsets(18, 28)
                            : launcherPage
                                    ? LauncherLayout.LAUNCHER_WORKSPACE_INSETS
                                    : LauncherLayout.WORKSPACE_INSETS);
        }
        if (contentBody != null) {
            contentBody.setSpacing(webMode ? 0 : 16);
            contentBody.setPadding(webMode || discoverMode || launcherPage ? Insets.EMPTY : new Insets(16, 0, 0, 0));
            contentBody.setMinHeight(documentMode ? Region.USE_PREF_SIZE : 0);
        }
        if (contentScroll != null) {
            contentScroll.setFitToHeight(!documentMode);
        }
        VBox.setVgrow(viewDeck, documentMode ? Priority.NEVER : Priority.ALWAYS);
        viewDeck.setMinHeight(documentMode ? Region.USE_PREF_SIZE : 0);
        viewDeck.getChildren().forEach(node -> {
            boolean active = nextView.equals(node.getUserData());
            node.setVisible(active);
            node.setManaged(active);
        });
        navButtons.forEach((key, button) -> pseudo(button, "selected", key.equals(nextView)
                || (nextView == LauncherView.PROJECT && key == LauncherView.DISCOVER)));
        browseMenu.updateSelected(nextView);
        accountMenu.updateSelected();
        updateRailButtons();
        pageTitle.setText(LauncherShellTitles.titleFor(nextView, browseController));
        pageSubtitle.setText(LauncherShellTitles.subtitleFor(nextView, browseController));
        toolbarActions.update(navigation.currentView());
        if (discoverMode) {
            resetContentScrollToTop();
        }
    }

    private static void toggleStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null || styleClass == null || styleClass.isBlank()) {
            return;
        }
        node.getStyleClass().remove(styleClass);
        if (enabled) {
            node.getStyleClass().add(styleClass);
        }
    }

    static boolean usesDocumentHeight(LauncherView view) {
        return view == LauncherView.PROJECT
                || view == LauncherView.DISCOVER
                || view == LauncherView.LIBRARY
                || view == LauncherView.UPDATES;
    }

    private void resetContentScrollToTop() {
        if (contentScroll == null) {
            return;
        }
        contentScroll.setVvalue(contentScroll.getVmin());
        Platform.runLater(() -> {
            if (contentScroll != null) {
                contentScroll.setVvalue(contentScroll.getVmin());
            }
        });
    }

    private Node navbar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("navbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(LauncherLayout.NAVBAR_INSETS);

        ImageView logo = new ImageView(new Image(Objects.requireNonNull(getClass()
                .getResource("/net/modtale/launcher/ui/nativefx/assets/logo_light.png")).toExternalForm(), true));
        logo.setFitHeight(36);
        logo.setPreserveRatio(true);
        Button brand = new Button(null, logo);
        brand.getStyleClass().add("brand");
        brand.setMinWidth(142);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setMnemonicParsing(false);
        brand.setOnAction(event -> showView(LauncherView.PLAY));
        configureBrandLogoHoverAnimation(brand, logo);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(brand, spacer);
        addNav(bar, LauncherView.PLAY, "Play", LauncherIcons.Glyph.ZAP);
        addNav(bar, LauncherView.LIBRARY, "Library", LauncherIcons.Glyph.SAVE);
        Button browseButton = browseMenu.button();
        navButtons.put(LauncherView.DISCOVER, browseButton);
        bar.getChildren().add(browseButton);
        Region separator = new Region();
        separator.getStyleClass().add("nav-divider");
        bar.getChildren().add(separator);
        bar.getChildren().add(notificationsMenu.button());
        bar.getChildren().add(accountMenu.button());
        if (undecoratedWindow) {
            configureWindowDrag(bar);
        }
        return bar;
    }

    private Node windowControls() {
        HBox controls = new HBox(0);
        controls.getStyleClass().add("window-controls");
        controls.setAlignment(Pos.CENTER);
        controls.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Button minimize = windowControl("Minimize", LauncherIcons.Glyph.MINUS);
        minimize.setOnAction(event -> stage.setIconified(true));
        Button maximize = windowControl("Maximize", LauncherIcons.Glyph.MAXIMIZE);
        maximize.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));
        stage.maximizedProperty().addListener((observable, wasMaximized, isMaximized) ->
                updateMaximizeControl(maximize, isMaximized));
        updateMaximizeControl(maximize, stage.isMaximized());
        Button close = windowControl("Close", LauncherIcons.Glyph.X);
        close.getStyleClass().add("close");
        close.setOnAction(event -> stage.close());
        controls.getChildren().addAll(minimize, maximize, close);
        return controls;
    }

    private Button windowControl(String tooltip, LauncherIcons.Glyph glyph) {
        Button button = new Button(null, LauncherIcons.icon(glyph, 12));
        button.getStyleClass().add("window-control");
        button.setMnemonicParsing(false);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private void updateMaximizeControl(Button button, boolean maximized) {
        button.setGraphic(LauncherIcons.icon(maximized ? LauncherIcons.Glyph.RESTORE : LauncherIcons.Glyph.MAXIMIZE, 12));
        button.setTooltip(new Tooltip(maximized ? "Restore" : "Maximize"));
    }

    private void bringWindowControlsToFront() {
        if (windowControlsNode != null) {
            windowControlsNode.toFront();
        }
    }

    private void configureWindowDrag(Node dragSurface) {
        dragSurface.setOnMousePressed(event -> {
            nativeWindowMoveInProgress = false;
            if (!isPrimaryButtonEvent(event) || isWindowControlEvent(event) || event.getClickCount() > 1) {
                return;
            }
            if (LinuxWindowManagerSupport.beginMove(stage, event)) {
                nativeWindowMoveInProgress = true;
                event.consume();
                return;
            }
            if (stage.isMaximized()) {
                return;
            }
            windowDragOffsetX = event.getSceneX();
            windowDragOffsetY = event.getSceneY();
        });
        dragSurface.setOnMouseDragged(event -> {
            if (nativeWindowMoveInProgress) {
                event.consume();
                return;
            }
            if (isWindowControlEvent(event) || stage.isMaximized()) {
                return;
            }
            stage.setX(event.getScreenX() - windowDragOffsetX);
            stage.setY(event.getScreenY() - windowDragOffsetY);
        });
        dragSurface.setOnMouseReleased(event -> nativeWindowMoveInProgress = false);
        dragSurface.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !isWindowControlEvent(event)) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }

    private void configureWindowResize(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::updateResizeCursor);
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> scene.setCursor(Cursor.DEFAULT));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::startWindowResize);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::dragFallbackResize);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> fallbackResizeDirection = null);
    }

    private void startWindowResize(MouseEvent event) {
        fallbackResizeDirection = null;
        if (!isPrimaryButtonEvent(event) || stage.isMaximized() || isWindowControlEvent(event)) {
            return;
        }
        LinuxWindowManagerSupport.ResizeDirection direction = resizeDirection(event);
        if (direction == null) {
            return;
        }
        if (LinuxWindowManagerSupport.beginResize(stage, event, direction)) {
            event.consume();
            return;
        }
        fallbackResizeDirection = direction;
        resizeStartScreenX = event.getScreenX();
        resizeStartScreenY = event.getScreenY();
        resizeStartStageX = stage.getX();
        resizeStartStageY = stage.getY();
        resizeStartWidth = stage.getWidth();
        resizeStartHeight = stage.getHeight();
        event.consume();
    }

    private void dragFallbackResize(MouseEvent event) {
        if (fallbackResizeDirection == null) {
            return;
        }
        double deltaX = event.getScreenX() - resizeStartScreenX;
        double deltaY = event.getScreenY() - resizeStartScreenY;
        double minWidth = stage.getMinWidth();
        double minHeight = stage.getMinHeight();
        double nextX = resizeStartStageX;
        double nextY = resizeStartStageY;
        double nextWidth = resizeStartWidth;
        double nextHeight = resizeStartHeight;

        if (fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.RIGHT
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.TOP_RIGHT
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.BOTTOM_RIGHT) {
            nextWidth = Math.max(minWidth, resizeStartWidth + deltaX);
        }
        if (fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.LEFT
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.TOP_LEFT
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.BOTTOM_LEFT) {
            nextWidth = Math.max(minWidth, resizeStartWidth - deltaX);
            nextX = resizeStartStageX + resizeStartWidth - nextWidth;
        }
        if (fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.BOTTOM
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.BOTTOM_LEFT
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.BOTTOM_RIGHT) {
            nextHeight = Math.max(minHeight, resizeStartHeight + deltaY);
        }
        if (fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.TOP
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.TOP_LEFT
                || fallbackResizeDirection == LinuxWindowManagerSupport.ResizeDirection.TOP_RIGHT) {
            nextHeight = Math.max(minHeight, resizeStartHeight - deltaY);
            nextY = resizeStartStageY + resizeStartHeight - nextHeight;
        }

        stage.setX(nextX);
        stage.setY(nextY);
        stage.setWidth(nextWidth);
        stage.setHeight(nextHeight);
        event.consume();
    }

    private void updateResizeCursor(MouseEvent event) {
        if (stage.isMaximized() || isWindowControlEvent(event)) {
            stage.getScene().setCursor(Cursor.DEFAULT);
            return;
        }
        LinuxWindowManagerSupport.ResizeDirection direction = resizeDirection(event);
        stage.getScene().setCursor(cursorForResizeDirection(direction));
    }

    private LinuxWindowManagerSupport.ResizeDirection resizeDirection(MouseEvent event) {
        if (stage == null || stage.getScene() == null) {
            return null;
        }
        double width = stage.getScene().getWidth();
        double height = stage.getScene().getHeight();
        double x = event.getSceneX();
        double y = event.getSceneY();
        boolean left = x <= RESIZE_HANDLE_SIZE;
        boolean right = x >= width - RESIZE_HANDLE_SIZE;
        boolean top = y <= RESIZE_HANDLE_SIZE;
        boolean bottom = y >= height - RESIZE_HANDLE_SIZE;

        if (top && left) {
            return LinuxWindowManagerSupport.ResizeDirection.TOP_LEFT;
        }
        if (top && right) {
            return LinuxWindowManagerSupport.ResizeDirection.TOP_RIGHT;
        }
        if (bottom && right) {
            return LinuxWindowManagerSupport.ResizeDirection.BOTTOM_RIGHT;
        }
        if (bottom && left) {
            return LinuxWindowManagerSupport.ResizeDirection.BOTTOM_LEFT;
        }
        if (top) {
            return LinuxWindowManagerSupport.ResizeDirection.TOP;
        }
        if (right) {
            return LinuxWindowManagerSupport.ResizeDirection.RIGHT;
        }
        if (bottom) {
            return LinuxWindowManagerSupport.ResizeDirection.BOTTOM;
        }
        return left ? LinuxWindowManagerSupport.ResizeDirection.LEFT : null;
    }

    private static Cursor cursorForResizeDirection(LinuxWindowManagerSupport.ResizeDirection direction) {
        if (direction == null) {
            return Cursor.DEFAULT;
        }
        return switch (direction) {
            case TOP_LEFT, BOTTOM_RIGHT -> Cursor.NW_RESIZE;
            case TOP_RIGHT, BOTTOM_LEFT -> Cursor.NE_RESIZE;
            case TOP, BOTTOM -> Cursor.V_RESIZE;
            case LEFT, RIGHT -> Cursor.H_RESIZE;
        };
    }

    private static boolean isPrimaryButtonEvent(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY;
    }

    private static boolean isWindowControlEvent(MouseEvent event) {
        if (!(event.getTarget() instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node instanceof Button || node instanceof TextField) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private static boolean shouldUseUndecoratedWindow() {
        String customWindowChrome = System.getProperty(CUSTOM_WINDOW_CHROME_PROPERTY);
        if (customWindowChrome != null && !customWindowChrome.isBlank()) {
            return Boolean.parseBoolean(customWindowChrome);
        }
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static void configureBrandLogoHoverAnimation(Button brand, Node logo) {
        ScaleTransition scale = new ScaleTransition(BRAND_LOGO_SCALE_DURATION, logo);
        scale.setInterpolator(Interpolator.EASE_BOTH);
        brand.setOnMouseEntered(event -> animateBrandLogoScale(scale, BRAND_LOGO_HOVER_SCALE));
        brand.setOnMouseExited(event -> animateBrandLogoScale(scale, 1));
    }

    private static void animateBrandLogoScale(ScaleTransition scale, double targetScale) {
        scale.stop();
        scale.setToX(targetScale);
        scale.setToY(targetScale);
        scale.playFromStart();
    }

    private static double boundedStageSize(double preferredSize, double minimumSize, double availableSize) {
        double availableWithMargin = availableSize - STAGE_SCREEN_MARGIN;
        if (availableWithMargin <= 0) {
            return preferredSize;
        }
        return Math.max(Math.min(minimumSize, availableWithMargin), Math.min(preferredSize, availableWithMargin));
    }

    private static double boundedStageMinimum(double minimumSize, double availableSize) {
        double availableWithMargin = availableSize - STAGE_SCREEN_MARGIN;
        if (availableWithMargin <= 0) {
            return minimumSize;
        }
        return Math.max(320, Math.min(minimumSize, availableWithMargin));
    }

    private static void centerStage(Stage primaryStage, Rectangle2D visualBounds, double width, double height) {
        primaryStage.setX(visualBounds.getMinX() + Math.max(0, (visualBounds.getWidth() - width) / 2));
        primaryStage.setY(visualBounds.getMinY() + Math.max(0, (visualBounds.getHeight() - height) / 2));
    }

    private void revealPrimaryStage(Stage primaryStage) {
        if (!primaryStage.isShowing()) {
            primaryStage.show();
        }
        primaryStage.setIconified(false);
        primaryStage.toFront();
        primaryStage.requestFocus();
        if (undecoratedWindow) {
            primaryStage.setAlwaysOnTop(true);
            Platform.runLater(() -> primaryStage.setAlwaysOnTop(false));
        }
    }

    private void scheduleStageVisibilityRetries(Stage primaryStage) {
        for (Duration delay : STAGE_VISIBILITY_RETRY_DELAYS) {
            scheduleStageVisibilityRetry(primaryStage, delay);
        }
    }

    private void scheduleStageVisibilityRetry(Stage primaryStage, Duration delay) {
        PauseTransition retry = new PauseTransition(delay);
        stageVisibilityRetries.add(retry);
        retry.setOnFinished(event -> {
            stageVisibilityRetries.remove(retry);
            revealPrimaryStage(primaryStage);
        });
        retry.play();
    }

    private void showHytaleGate(String status, boolean allowSignIn) {
        setVisibleManaged(appRoot, false);
        setVisibleManaged(hytaleGateNode, true);
        setVisibleManaged(windowControlsNode, undecoratedWindow);
        bringWindowControlsToFront();
        hytaleAuthGate.show(status, allowSignIn);
    }

    private Node workspace() {
        workspaceRoot = new HBox(WORKSPACE_SPACING);
        workspaceRoot.getStyleClass().add("workspace");
        workspaceRoot.setPadding(LauncherLayout.WORKSPACE_INSETS);
        workspaceRoot.setFillHeight(true);
        workspaceRoot.setMaxHeight(Double.MAX_VALUE);
        railNode = rail();
        workspaceRoot.getChildren().addAll(railNode, content());
        return workspaceRoot;
    }

    private Node rail() {
        VBox rail = new VBox(16);
        rail.getStyleClass().add("rail");
        rail.setPrefWidth(RAIL_WIDTH);
        rail.setMinWidth(RAIL_WIDTH);

        TextField browseSearch = browseController.searchField();
        browseSearch.getStyleClass().add("quick-search");
        browseSearch.setPromptText("Search projects...");
        browseSearch.setOnAction(event -> browseController.searchProjects());
        browseSearch.setMaxWidth(Double.MAX_VALUE);
        StackPane browseSearchShell = new StackPane(browseSearch);
        browseSearchShell.getStyleClass().add("search-shell");
        browseSearchShell.setMaxWidth(Double.MAX_VALUE);
        Node searchIcon = LauncherIcons.icon(LauncherIcons.Glyph.SEARCH, 16);
        searchIcon.getStyleClass().add("search-icon");
        searchIcon.setMouseTransparent(true);
        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 13));
        browseSearchShell.getChildren().add(searchIcon);
        Button clearSearch = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 12));
        clearSearch.getStyleClass().add("search-clear-button");
        clearSearch.setMnemonicParsing(false);
        clearSearch.setAccessibleText("Clear search");
        clearSearch.setTooltip(new Tooltip("Clear search"));
        clearSearch.setOnAction(event -> {
            browseController.resetSearchQuery();
            browseSearch.requestFocus();
        });
        StackPane.setAlignment(clearSearch, Pos.CENTER_RIGHT);
        StackPane.setMargin(clearSearch, new Insets(0, 9, 0, 0));
        browseSearch.textProperty().addListener((observable, oldValue, newValue) ->
                updateSearchResetControl(clearSearch, newValue));
        updateSearchResetControl(clearSearch, browseSearch.getText());
        browseSearchShell.getChildren().add(clearSearch);

        VBox browse = railCard("Browse");
        for (BrowseOptions.BrowseViewOption view : BrowseOptions.BROWSE_VIEWS) {
            addRailButton(browse, view, view.label(), view.icon(), false,
                    event -> browseController.selectBrowseView(view));
        }

        rail.getChildren().addAll(browseSearchShell, browse);
        return rail;
    }

    private void updateSearchResetControl(Button clearSearch, String query) {
        boolean hasQuery = query != null && !query.isEmpty();
        setVisibleManaged(clearSearch, hasQuery);
    }

    private Node content() {
        VBox content = new VBox(0);
        content.getStyleClass().add("content");
        content.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox toolbar = new HBox(16);
        mainToolbar = toolbar;
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(3, pageTitle, pageSubtitle);
        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().addAll(titleBox, spacer, toolbarActions.view());

        ScrollPane scrollPane = new ScrollPane();
        contentScroll = scrollPane;
        scrollPane.getStyleClass().add("content-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPannable(true);
        scrollSupport.configure(scrollPane, false);
        contentBody = new VBox(viewDeck);
        contentBody.getStyleClass().add("body");
        contentBody.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(viewDeck, Priority.ALWAYS);
        viewDeck.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scrollPane.setContent(contentBody);
        projectPageController.attachScrollPane(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        content.getChildren().addAll(toolbar, scrollPane);
        return content;
    }

    private void buildViews() {
        viewDeck.getChildren().setAll(
                browseController.view(),
                projectPageController.view(),
                playController.view(),
                libraryController.libraryView(),
                libraryController.updatesView(),
                notificationsController.view(),
                settingsController.view()
        );
    }

    private void startModtaleInitialLoads() {
        if (modtaleInitialLoadsStarted || !accountController.isSignedIn()) {
            return;
        }
        modtaleInitialLoadsStarted = true;
        browseController.loadGameVersionFilters();
        browseController.searchProjects();
        scheduleSnapshotIfRequested();
    }

    private void scheduleSnapshotIfRequested() {
        if (startupSnapshotScheduled || launchParameters == null) {
            return;
        }
        startupSnapshotScheduled = true;
        LauncherSnapshotService.scheduleIfRequested(
                launchParameters.getNamed().get("screenshot"),
                sceneRoot()
        );
    }

    private void handleStartupProtocolIfRequested() {
        if (startupProtocolHandled || launchParameters == null) {
            return;
        }
        LauncherProtocolRequest request = LauncherProtocolRequest.from(launchParameters);
        if (!request.hasInstallList()) {
            return;
        }
        startupProtocolHandled = true;
        showView(LauncherView.LIBRARY);
        libraryController.installWorldModList(request.installListId());
    }

    private void addNav(HBox bar, LauncherView view, String label, LauncherIcons.Glyph icon) {
        Button button = new Button(label);
        button.getStyleClass().add("nav-btn");
        applyNavbarTitleFont(button);
        button.setGraphic(LauncherIcons.icon(icon, 16));
        button.setOnAction(event -> showView(view));
        navButtons.put(view, button);
        bar.getChildren().add(button);
    }

    private void hideDropdownsOnOutsidePress(MouseEvent event) {
        browseMenu.hideOnOutsidePress(event.getTarget());
        notificationsMenu.hideOnOutsidePress(event.getTarget());
        accountMenu.hideOnOutsidePress(event.getTarget());
    }

    private static void applyNavbarTitleFont(Labeled text) {
        text.setFont(Font.font("Inter", FontWeight.EXTRA_BOLD, NAVBAR_TEXT_FONT_SIZE));
    }

    private void addRailButton(
            VBox card,
            BrowseOptions.BrowseViewOption key,
            String label,
            LauncherIcons.Glyph icon,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler
    ) {
        addRailButton(card, key, label, icon, true, handler);
    }

    private void addRailButton(
            VBox card,
            BrowseOptions.BrowseViewOption key,
            String label,
            LauncherIcons.Glyph icon,
            boolean showIcon,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler
    ) {
        Button button = new Button(label);
        button.getStyleClass().add("rail-link");
        if (showIcon) {
            button.setGraphic(LauncherIcons.icon(icon, 15));
        }
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(handler);
        railButtons.putIfAbsent(key, button);
        card.getChildren().add(button);
    }

    private void updateRailButtons() {
        boolean browseView = navigation.currentView() == LauncherView.DISCOVER
                || navigation.currentView() == LauncherView.PROJECT;
        railButtons.forEach((key, button) -> pseudo(button, "selected", browseView
                && key == browseController.activeBrowseView()));
    }

    private VBox railCard(String title) {
        VBox card = new VBox(6);
        card.getStyleClass().add("rail-card");
        boolean browseTitle = "Browse".equals(title);
        if (browseTitle) {
            card.getStyleClass().add("browse-rail-card");
            card.getChildren().add(browseRailTitle());
        } else {
            Label label = new Label(title);
            label.getStyleClass().add("rail-title");
            card.getChildren().add(label);
        }
        return card;
    }

    private Node browseRailTitle() {
        HBox title = new HBox();
        title.getStyleClass().add("browse-rail-title");
        for (char letter : "BROWSE".toCharArray()) {
            Label letterLabel = new Label(String.valueOf(letter));
            letterLabel.getStyleClass().add("browse-rail-title-letter");
            title.getChildren().add(letterLabel);
        }
        return title;
    }

    private boolean hasLinkedHytaleAccount() {
        return settingsController.settings().getHytaleAuthSession() != null;
    }

    private static boolean requiresModtaleSession(LauncherView view) {
        return view == LauncherView.DISCOVER
                || view == LauncherView.PROJECT
                || view == LauncherView.UPDATES
                || view == LauncherView.NOTIFICATIONS;
    }

}
