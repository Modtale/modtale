package net.modtale.launcher.ui.browse;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;
import static net.modtale.launcher.ui.common.LauncherUi.styleCombo;
import static net.modtale.launcher.ui.common.LauncherUi.styleInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ProjectSearchQuery;
import net.modtale.launcher.model.project.GameVersionCatalog;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.controls.BrowseOptions;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseCategories;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseDownloadTimeframeSelector;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseFilterOptions;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseSort;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseTags;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseViewStyleSelector;
import net.modtale.launcher.ui.browse.render.ProjectBrowserRenderer;
import net.modtale.launcher.ui.browse.search.ProjectBrowseSearchState;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherScrollSupport;
import net.modtale.launcher.ui.common.LauncherView;

public final class ProjectBrowseController {

    private static final double RESULTS_INDICATOR_WIDTH = 126;
    private static final double RESULTS_INDICATOR_SHOW_BUFFER = 10;
    private static final Duration PAGINATION_SCROLL_DURATION = Duration.millis(420);
    private static final Interpolator PAGINATION_SCROLL_EASE = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);

    private final ModtaleApiClient apiClient;
    private final Executor executor;
    private final Runnable applySettings;
    private final Consumer<String> status;
    private final Supplier<String> idleStatus;
    private final Consumer<String> log;
    private final BiConsumer<String, String> toast;
    private final Runnable showDiscover;
    private final Supplier<LauncherView> currentView;
    private final BooleanSupplier modtaleApiAvailable;
    private final TextField searchField = new TextField();
    private final ComboBox<ProjectBrowseSort> sortCombo = new ComboBox<>();
    private final ComboBox<Integer> pageSizeCombo = new ComboBox<>();
    private final StackPane projectResults = new StackPane();
    private final Label resultsIndicator = new Label("0 Results");
    private final FlowPane paginationNav = new FlowPane(24, 12);
    private final HBox paginationPageShell = new HBox(4);
    private final HBox paginationPageButtons = new HBox(4);
    private final HBox paginationJumpShell = new HBox(12);
    private final TextField jumpPageField = new TextField();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(450));
    private final ChangeListener<Number> layoutResizeListener = (observable, oldValue, newValue) ->
            scheduleLayoutRefresh(oldValue, newValue);
    private final ProjectBrowserRenderer renderer;
    private final ProjectBrowseCategories categories;
    private final ProjectBrowseTags tags;
    private final ProjectBrowseViewStyleSelector viewStyles;
    private final ProjectBrowseFilterOptions filterOptions;
    private final ProjectBrowseDownloadTimeframeSelector downloadTimeframes;
    private final ProjectBrowseSearchState searchState = new ProjectBrowseSearchState();
    private final VBox sortDropdown = new VBox();
    private final Map<ProjectBrowseSort, Button> sortOptionButtons = new LinkedHashMap<>();
    private final Map<ProjectBrowseSort, Node> sortOptionChecks = new LinkedHashMap<>();

    private Button tagToggleButton;
    private Button filterToggleButton;
    private Button sortButton;
    private Label sortButtonLabel;
    private Timeline paginationScrollTimeline;
    private Button previousPageButton;
    private Button nextPageButton;
    private Button jumpPageButton;
    private StackPane browseRoot;
    private Node view;
    private List<ProjectSummary> currentProjects = List.of();
    private boolean suppressSearch;
    private boolean layoutRefreshScheduled;
    private int currentPage;
    private int totalPageCount;
    private long totalResultCount;
    private BrowseOptions.BrowseViewOption activeBrowseView = BrowseOptions.BrowseViewOption.defaultOption();

    public ProjectBrowseController(
            ModtaleApiClient apiClient,
            Executor executor,
            ProjectCardFactory projectCardFactory,
            StackPane viewDeck,
            Supplier<VBox> contentBody,
            LauncherScrollSupport scrollSupport,
            Runnable applySettings,
            Consumer<String> status,
            Supplier<String> idleStatus,
            Consumer<String> log,
            BiConsumer<String, String> toast,
            Runnable showDiscover,
            Supplier<LauncherView> currentView,
            BooleanSupplier modtaleApiAvailable,
            Function<String, Boolean> favoriteResolver,
            Supplier<String> gameVersion,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onOpenPage,
            Consumer<ProjectSummary> onOpenCreator,
            Consumer<ProjectSummary> onToggleFavorite
    ) {
        this.apiClient = apiClient;
        this.executor = executor;
        this.applySettings = applySettings;
        this.status = status;
        this.idleStatus = idleStatus;
        this.log = log;
        this.toast = toast;
        this.showDiscover = showDiscover;
        this.currentView = currentView;
        this.modtaleApiAvailable = modtaleApiAvailable == null ? () -> true : modtaleApiAvailable;
        this.renderer = new ProjectBrowserRenderer(projectResults, viewDeck, contentBody, projectCardFactory,
                favoriteResolver, gameVersion, onInstall, onOpenPage, onOpenCreator, onToggleFavorite);
        this.categories = new ProjectBrowseCategories(scrollSupport, this::searchProjects);
        this.tags = new ProjectBrowseTags(this::searchProjects, this::refreshBrowseControls);
        this.viewStyles = new ProjectBrowseViewStyleSelector(this::searchProjects);
        this.filterOptions = new ProjectBrowseFilterOptions(
                this::searchProjects,
                this::refreshBrowseControls,
                tags::clear
        );
        this.downloadTimeframes = new ProjectBrowseDownloadTimeframeSelector(
                filterOptions::selectDateRange,
                filterOptions::selectedDateRange
        );
        configureInputs();
    }

    public TextField searchField() {
        return searchField;
    }

    public Node view() {
        if (view == null) {
            view = buildView();
        }
        return view;
    }

    public BrowseOptions.BrowseViewOption activeBrowseView() {
        return activeBrowseView;
    }

    public String title() {
        if (!tags.isEmpty()) {
            return tags.title();
        }
        BrowseOptions.BrowseViewOption viewOption = BrowseOptions.browseView(activeBrowseView);
        if (viewOption.isDefault()) {
            String sortTitle = selectedSort().title();
            if (!sortTitle.isBlank()) {
                return sortTitle;
            }
            BrowseOptions.ClassificationOption selectedClassification = categories.selectedClassification();
            return selectedClassification.isDefault()
                    ? "All Projects"
                    : "All " + selectedClassification.label();
        }
        return viewOption.label();
    }

    public String subtitle() {
        BrowseOptions.BrowseViewOption viewOption = BrowseOptions.browseView(activeBrowseView);
        if (!viewOption.isDefault()) {
            return "Browse " + viewOption.label().toLowerCase(Locale.ROOT) + " with the same filters as the web catalog.";
        }
        return "Browse the Modtale catalog and install compatible Hytale projects.";
    }

    public void selectBrowseView(BrowseOptions.BrowseViewOption browseView) {
        BrowseOptions.BrowseViewOption selected = BrowseOptions.browseView(browseView);
        activeBrowseView = selected;
        withSuppressedSearch(() -> sortCombo.setValue(selected.defaultSort()));
        refreshBrowseControls();
        showDiscover.run();
        searchProjects();
    }

    public void selectClassification(BrowseOptions.ClassificationOption classification) {
        activeBrowseView = BrowseOptions.BrowseViewOption.defaultOption();
        withSuppressedSearch(() -> sortCombo.setValue(ProjectBrowseSort.defaultSort()));
        refreshBrowseControls();
        showDiscover.run();
        categories.selectClassification(classification);
    }

    public void selectDefaultBrowsePage() {
        withSuppressedSearch(() -> {
            activeBrowseView = BrowseOptions.BrowseViewOption.defaultOption();
            searchField.clear();
            sortCombo.setValue(ProjectBrowseSort.defaultSort());
            filterOptions.reset(false);
            tags.popover().setVisible(false);
            filterOptions.popover().setVisible(false);
        });
        searchDebounce.stop();
        refreshBrowseControls();
        showDiscover.run();
        categories.selectClassification(BrowseOptions.ClassificationOption.defaultOption());
    }

    public void refreshControls() {
        categories.refresh();
        tags.refresh();
        viewStyles.refresh();
        refreshBrowseControls();
    }

    public void loadGameVersionFilters() {
        if (!modtaleApiAvailable.getAsBoolean()) {
            return;
        }
        CompletableFuture.supplyAsync(this::loadGameVersionCatalog, executor)
                .whenComplete((catalog, error) -> Platform.runLater(() -> {
                    if (error != null || catalog == null) {
                        return;
                    }
                    filterOptions.replaceGameVersionCatalog(catalog);
                }));
    }

    public void searchProjects() {
        currentPage = 0;
        requestProjects();
    }

    public void resetSearchQuery() {
        if (searchField.getText().isEmpty()) {
            return;
        }
        withSuppressedSearch(searchField::clear);
        searchDebounce.stop();
        searchProjects();
    }

    private void requestProjects() {
        if (suppressSearch) {
            return;
        }
        if (!modtaleApiAvailable.getAsBoolean()) {
            status.accept(idleStatus.get());
            log.accept("Sign in with Modtale to browse projects.");
            toast.accept("Modtale sign-in required", "Sign in with Modtale to browse projects.");
            return;
        }
        applySettings.run();
        ProjectSearchQuery query = searchQuery();
        long requestId = searchState.start(query);
        if (requestId == ProjectBrowseSearchState.DUPLICATE_SEARCH) {
            return;
        }

        updateResultsIndicator(totalResultCount, true);
        status.accept("Searching Modtale projects...");
        log.accept("Searching Modtale projects...");
        CompletableFuture.supplyAsync(() -> apiClient.searchProjects(query), executor)
                .whenComplete((page, error) -> Platform.runLater(() -> {
                    if (!searchState.acceptCompletion(query, requestId)) {
                        return;
                    }
                    status.accept(idleStatus.get());
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        updateResultsIndicator(totalResultCount, false);
                        updatePaginationControls();
                        log.accept("Error: " + cause.getMessage());
                        toast.accept("Search failed", cause.getMessage());
                        return;
                    }
                    finishSearch(page, query);
                }));
    }

    public void renderProjects() {
        renderer.render(currentProjects, viewStyles.style(), selectedPageSize());
    }

    public void applyFavoriteDelta(String projectId, int delta) {
        if (delta == 0) {
            return;
        }
        currentProjects = currentProjects.stream()
                .map(item -> item.id().equals(projectId)
                        ? item.withFavoriteCount(item.favoriteCount() + delta)
                        : item)
                .toList();
    }

    private Node buildView() {
        VBox content = new VBox(16);
        content.getStyleClass().add("browse-content");
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox filters = new VBox(10);
        filters.getStyleClass().add("browse-filter-shell");

        HBox browseControls = new HBox(12);
        browseControls.getStyleClass().add("browse-filter-row");
        browseControls.setAlignment(Pos.CENTER_LEFT);

        HBox controlRow = new HBox(8);
        controlRow.getStyleClass().add("browse-control-group");
        controlRow.setAlignment(Pos.CENTER_RIGHT);
        controlRow.setMinWidth(Region.USE_PREF_SIZE);
        tagToggleButton = popoverToggle("Tags", LauncherIcons.Glyph.TAG, tags.popover());
        filterToggleButton = popoverToggle("Filters", LauncherIcons.Glyph.FILTER, filterOptions.popover());
        sortButton = sortControl();
        controlRow.getChildren().addAll(
                pageSizeCombo,
                viewStyles.view(),
                tagToggleButton,
                filterToggleButton,
                downloadTimeframes.view(),
                sortButton
        );
        resultsIndicator.getStyleClass().add("browse-results-indicator");
        resultsIndicator.setAlignment(Pos.CENTER_LEFT);
        resultsIndicator.setMinWidth(RESULTS_INDICATOR_WIDTH);
        resultsIndicator.setPrefWidth(RESULTS_INDICATOR_WIDTH);
        resultsIndicator.setMaxWidth(RESULTS_INDICATOR_WIDTH);
        resultsIndicator.setMouseTransparent(true);
        resultsIndicator.setAccessibleText("Browse results status");
        Node categoryPills = categories.view();
        HBox.setHgrow(categoryPills, Priority.NEVER);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        browseControls.getChildren().addAll(categoryPills, resultsIndicator, spacer, controlRow);
        configureResultsIndicatorVisibility(browseControls, controlRow);

        filters.getChildren().add(browseControls);

        projectResults.getStyleClass().add("project-results");
        projectResults.setMaxWidth(Double.MAX_VALUE);
        projectResults.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(projectResults, Priority.ALWAYS);
        configurePagination();
        content.getChildren().addAll(filters, projectResults, paginationNav);

        configureSortDropdown();
        StackPane root = new StackPane(content, tags.popover(), filterOptions.popover(), sortDropdown);
        browseRoot = root;
        root.setUserData(LauncherView.DISCOVER);
        root.getStyleClass().addAll("view", "browse-view");
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, this::hideFilterDropdownsOnOutsidePress);
        return root;
    }

    private GameVersionCatalog loadGameVersionCatalog() {
        try {
            return apiClient.getGameVersionCatalog();
        } catch (RuntimeException ex) {
            return GameVersionCatalog.fromVersions(apiClient.getGameVersions());
        }
    }

    private void configureInputs() {
        sortCombo.setItems(FXCollections.observableArrayList(ProjectBrowseSort.DOWNLOADS, ProjectBrowseSort.FAVORITES));
        sortCombo.setValue(ProjectBrowseSort.defaultSort());
        sortCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProjectBrowseSort value) {
                return value == null ? ProjectBrowseSort.defaultSort().label() : value.label();
            }

            @Override
            public ProjectBrowseSort fromString(String value) {
                return ProjectBrowseSort.fromLabel(value);
            }
        });
        sortCombo.setOnAction(event -> {
            refreshBrowseControls();
            if (!suppressSearch) {
                activeBrowseView = selectedSort().browseView();
                showDiscover.run();
            }
            searchProjects();
        });
        sortCombo.valueProperty().addListener((observable, oldValue, newValue) -> refreshSortDropdown());
        pageSizeCombo.setItems(FXCollections.observableArrayList(BrowseOptions.BROWSE_ITEMS_PER_PAGE_OPTIONS));
        pageSizeCombo.setValue(BrowseOptions.DEFAULT_ITEMS_PER_PAGE);
        pageSizeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return Integer.toString(BrowseOptions.itemsPerPage(value));
            }

            @Override
            public Integer fromString(String value) {
                if (value == null || value.isBlank()) {
                    return BrowseOptions.DEFAULT_ITEMS_PER_PAGE;
                }
                try {
                    return BrowseOptions.itemsPerPage(Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                    return BrowseOptions.DEFAULT_ITEMS_PER_PAGE;
                }
            }
        });
        pageSizeCombo.setOnAction(event -> {
            tags.popover().setVisible(false);
            filterOptions.popover().setVisible(false);
            hideSortDropdown();
            searchProjects();
        });
        pageSizeCombo.setTooltip(new Tooltip("Results per page"));
        pageSizeCombo.setAccessibleText("Results per page");
        styleCombo(pageSizeCombo);
        pageSizeCombo.setMinWidth(68);
        pageSizeCombo.setPrefWidth(68);
        pageSizeCombo.setMaxWidth(68);
        pageSizeCombo.getStyleClass().add("page-size-select");
        searchDebounce.setOnFinished(event -> searchProjects());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> searchDebounce.playFromStart());
        projectResults.widthProperty().addListener(layoutResizeListener);
        projectResults.heightProperty().addListener(layoutResizeListener);
        projectResults.sceneProperty().addListener((observable, oldScene, newScene) ->
                observeSceneResizes(oldScene, newScene));
        styleInput(searchField);
        refreshBrowseControls();
    }

    private void configurePagination() {
        paginationNav.getStyleClass().add("pagination-nav");
        paginationNav.setAlignment(Pos.CENTER);
        paginationNav.setMaxWidth(Double.MAX_VALUE);

        paginationPageShell.getStyleClass().add("pagination-page-shell");
        paginationPageShell.setAlignment(Pos.CENTER);
        previousPageButton = paginationIconButton(LauncherIcons.Glyph.CHEVRON_LEFT, "Previous Page");
        nextPageButton = paginationIconButton(LauncherIcons.Glyph.CHEVRON_RIGHT, "Next Page");
        paginationPageShell.getChildren().setAll(previousPageButton, paginationPageButtons, nextPageButton);

        paginationPageButtons.getStyleClass().add("pagination-page-buttons");
        paginationPageButtons.setAlignment(Pos.CENTER);

        paginationJumpShell.getStyleClass().add("pagination-jump-shell");
        paginationJumpShell.setAlignment(Pos.CENTER);
        Label jumpLabel = new Label("JUMP");
        jumpLabel.getStyleClass().add("pagination-jump-label");
        jumpPageField.setPromptText("#");
        jumpPageField.getStyleClass().add("pagination-jump-input");
        jumpPageField.setOnAction(event -> submitJumpPage());
        jumpPageField.textProperty().addListener((observable, oldValue, newValue) -> updateJumpPageState());
        jumpPageButton = paginationIconButton(LauncherIcons.Glyph.CORNER_DOWN_LEFT, "Go");
        jumpPageButton.getStyleClass().add("pagination-jump-button");
        jumpPageButton.setOnAction(event -> submitJumpPage());
        paginationJumpShell.getChildren().setAll(jumpLabel, jumpPageField, jumpPageButton);

        paginationNav.getChildren().setAll(paginationPageShell, paginationJumpShell);
        updatePaginationControls();
    }

    private Button paginationIconButton(LauncherIcons.Glyph glyph, String accessibleText) {
        Button button = new Button();
        button.getStyleClass().addAll("pagination-button", "pagination-icon-button");
        button.setGraphic(LauncherIcons.icon(glyph, 16));
        button.setAccessibleText(accessibleText);
        button.setMinSize(36, 36);
        button.setPrefSize(36, 36);
        button.setMaxSize(36, 36);
        return button;
    }

    private Button sortControl() {
        Button button = new Button();
        button.getStyleClass().add("sort-button");
        sortButtonLabel = new Label(selectedSort().label());
        sortButtonLabel.getStyleClass().add("sort-button-label");
        sortButtonLabel.setAlignment(Pos.CENTER);
        Node chevron = LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 14);
        chevron.getStyleClass().add("sort-button-chevron");
        HBox content = new HBox(6, sortButtonLabel, chevron);
        content.getStyleClass().add("sort-button-content");
        content.setAlignment(Pos.CENTER);
        button.setGraphic(content);
        button.setOnAction(event -> toggleSortDropdown());
        refreshSortDropdown();
        return button;
    }

    private void configureSortDropdown() {
        sortDropdown.getStyleClass().add("sort-dropdown-panel");
        sortDropdown.setMinWidth(192);
        sortDropdown.setPrefWidth(192);
        sortDropdown.setMaxWidth(192);
        sortDropdown.setVisible(false);
        sortDropdown.setManaged(false);
        sortDropdown.getChildren().setAll(
                sortDropdownItem(ProjectBrowseSort.DOWNLOADS),
                sortDropdownItem(ProjectBrowseSort.FAVORITES)
        );
        refreshSortDropdown();
    }

    private Button sortDropdownItem(ProjectBrowseSort sort) {
        Button item = new Button();
        item.getStyleClass().add("sort-dropdown-item");
        Label label = new Label(sort.label());
        label.getStyleClass().add("sort-dropdown-item-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Node check = LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 15);
        check.getStyleClass().add("sort-dropdown-check");
        HBox content = new HBox(8, label, spacer, check);
        content.setAlignment(Pos.CENTER_LEFT);
        item.setGraphic(content);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setOnAction(event -> selectSort(sort));
        sortOptionButtons.put(sort, item);
        sortOptionChecks.put(sort, check);
        return item;
    }

    private void toggleSortDropdown() {
        if (sortDropdown.isVisible()) {
            hideSortDropdown();
            return;
        }
        tags.popover().setVisible(false);
        filterOptions.popover().setVisible(false);
        sortDropdown.setVisible(true);
        sortDropdown.toFront();
        positionFilterDropdown(sortButton, sortDropdown, true);
        Platform.runLater(() -> positionFilterDropdown(sortButton, sortDropdown, true));
        refreshSortDropdown();
    }

    private void hideSortDropdown() {
        sortDropdown.setVisible(false);
        refreshSortDropdown();
    }

    private void selectSort(ProjectBrowseSort sort) {
        hideSortDropdown();
        sortCombo.setValue(sort);
        refreshSortDropdown();
    }

    private void refreshSortDropdown() {
        ProjectBrowseSort selected = selectedSort();
        if (sortButtonLabel != null) {
            sortButtonLabel.setText(selected.label());
        }
        if (sortButton != null) {
            pseudo(sortButton, "selected", sortDropdown.isVisible());
        }
        sortOptionButtons.forEach((sort, button) -> pseudo(button, "selected", sort == selected));
        sortOptionChecks.forEach((sort, check) -> check.setVisible(sort == selected));
    }

    private Button popoverToggle(String label, LauncherIcons.Glyph icon, VBox popover) {
        Button button = secondaryButton(label);
        button.getStyleClass().add("toolbar-pill");
        button.setGraphic(LauncherIcons.icon(icon, 15));
        double width = "Filters".equals(label) ? 92 : 80;
        button.setMinWidth(width);
        button.setOnAction(event -> {
            boolean nextVisible = !popover.isVisible();
            tags.popover().setVisible(false);
            filterOptions.popover().setVisible(false);
            hideSortDropdown();
            popover.setVisible(nextVisible);
            if (nextVisible) {
                positionFilterDropdown(button, popover, "Filters".equals(label));
                popover.toFront();
                Platform.runLater(() -> positionFilterDropdown(button, popover, "Filters".equals(label)));
            }
            updateBrowseControlBadges();
        });
        popover.visibleProperty().addListener((observable, oldValue, visible) -> {
            if (visible) {
                positionFilterDropdown(button, popover, "Filters".equals(label));
                popover.toFront();
            }
            updateBrowseControlBadges();
            scheduleLayoutRefresh(null, null);
        });
        return button;
    }

    private void positionFilterDropdown(Button anchor, VBox popover, boolean rightAligned) {
        if (browseRoot == null || anchor == null || popover == null || anchor.getScene() == null) {
            return;
        }
        popover.applyCss();
        popover.autosize();
        Bounds anchorBounds = anchor.localToScene(anchor.getBoundsInLocal());
        if (anchorBounds == null) {
            return;
        }
        Point2D anchorMin = browseRoot.sceneToLocal(anchorBounds.getMinX(), anchorBounds.getMinY());
        Point2D anchorMax = browseRoot.sceneToLocal(anchorBounds.getMaxX(), anchorBounds.getMaxY());
        double width = popover.getLayoutBounds().getWidth() > 0 ? popover.getLayoutBounds().getWidth() : popover.prefWidth(-1);
        double maxX = Math.max(8, browseRoot.getWidth() - width - 8);
        double x = rightAligned ? anchorMax.getX() - width : anchorMin.getX();
        double y = anchorMax.getY() + 8;
        popover.relocate(clamp(x, 8, maxX), y);
    }

    private void hideFilterDropdownsOnOutsidePress(MouseEvent event) {
        boolean tagsVisible = tags.popover().isVisible();
        boolean filtersVisible = filterOptions.popover().isVisible();
        boolean sortVisible = sortDropdown.isVisible();
        if (!tagsVisible && !filtersVisible && !sortVisible) {
            return;
        }
        EventTarget target = event.getTarget();
        if (tagsVisible
                && !eventTargetInside(target, tags.popover())
                && !eventTargetInside(target, tagToggleButton)) {
            tags.popover().setVisible(false);
        }
        if (filtersVisible
                && !eventTargetInside(target, filterOptions.popover())
                && !eventTargetInside(target, filterToggleButton)) {
            filterOptions.popover().setVisible(false);
        }
        if (sortVisible
                && !eventTargetInside(target, sortDropdown)
                && !eventTargetInside(target, sortButton)) {
            hideSortDropdown();
        }
    }

    private static boolean eventTargetInside(EventTarget target, Node root) {
        if (!(target instanceof Node node) || root == null) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == root) {
                return true;
            }
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private void updateBrowseControlBadges() {
        if (tagToggleButton != null) {
            tagToggleButton.setText(tags.isEmpty() ? "Tags" : "Tags " + tags.selectedCount());
            pseudo(tagToggleButton, "selected", !tags.isEmpty() || tags.popover().isVisible());
        }
        if (filterToggleButton != null) {
            int count = filterOptions.activeFilterCount();
            filterToggleButton.setText(count == 0 ? "Filters" : "Filters " + count);
            pseudo(filterToggleButton, "selected", count > 0 || filterOptions.popover().isVisible());
        }
    }

    private void refreshBrowseControls() {
        boolean downloadSort = isDownloadSort();
        filterOptions.setDownloadSort(downloadSort);
        downloadTimeframes.setVisible(downloadSort);
        downloadTimeframes.refresh();
        updateBrowseControlBadges();
        refreshSortDropdown();
    }

    private boolean isDownloadSort() {
        return selectedSort() == ProjectBrowseSort.DOWNLOADS;
    }

    private ProjectSearchQuery searchQuery() {
        BrowseOptions.BrowseViewOption browseViewOption = BrowseOptions.browseView(activeBrowseView);
        return new ProjectSearchQuery(
                searchField.getText(),
                categories.selectedClassification().apiValue(),
                filterOptions.selectedGameVersion(),
                selectedSort().apiValue(),
                currentPage,
                selectedPageSize(),
                tags.selectedQuery(),
                filterOptions.selectedMinimumDownloads(),
                filterOptions.selectedMinimumFavorites(),
                browseViewOption.category(),
                filterOptions.selectedDateRange(),
                filterOptions.selectedOpenSource()
        );
    }

    private ProjectBrowseSort selectedSort() {
        return sortCombo.getValue() == null ? ProjectBrowseSort.defaultSort() : sortCombo.getValue();
    }

    private void withSuppressedSearch(Runnable work) {
        boolean previous = suppressSearch;
        suppressSearch = true;
        try {
            work.run();
        } finally {
            suppressSearch = previous;
        }
    }

    private int selectedPageSize() {
        return BrowseOptions.itemsPerPage(pageSizeCombo.getValue());
    }

    private void finishSearch(ProjectPage page, ProjectSearchQuery query) {
        if (page == null) {
            return;
        }
        int nextTotalPages = Math.max(0, page.totalPages());
        if (nextTotalPages > 0 && query.page() >= nextTotalPages) {
            totalPageCount = nextTotalPages;
            currentPage = nextTotalPages - 1;
            updatePaginationControls();
            requestProjects();
            return;
        }

        searchState.recordCompleted(query);
        totalPageCount = nextTotalPages;
        currentPage = totalPageCount == 0
                ? 0
                : (int) clamp(query.page(), 0, totalPageCount - 1);
        currentProjects = page.content();
        totalResultCount = Math.max(0, page.totalElements());
        updateResultsIndicator(totalResultCount, false);
        updatePaginationControls();
        renderProjects();
        log.accept("Found " + page.content().size() + " projects.");
        ProjectSearchQuery nextQuery = searchQuery();
        if (!nextQuery.equals(query) && searchState.shouldSearchForLayout(nextQuery, currentProjects)) {
            requestProjects();
        }
    }

    private void renderProjectsForLayoutChange() {
        if (currentProjects.isEmpty()) {
            return;
        }
        renderProjects();
        ProjectSearchQuery nextQuery = searchQuery();
        if (searchState.shouldSearchForLayout(nextQuery, currentProjects)) {
            requestProjects();
        }
    }

    private void updatePaginationControls() {
        boolean showPagination = totalPageCount > 1;
        setVisibleManaged(paginationNav, showPagination);
        if (!showPagination || previousPageButton == null || nextPageButton == null || jumpPageButton == null) {
            return;
        }

        previousPageButton.setDisable(currentPage <= 0);
        previousPageButton.setOnAction(event -> goToPage(currentPage - 1));
        nextPageButton.setDisable(currentPage >= totalPageCount - 1);
        nextPageButton.setOnAction(event -> goToPage(currentPage + 1));

        paginationPageButtons.getChildren().clear();
        for (PageToken token : pageTokens(currentPage, totalPageCount)) {
            if (token.ellipsis()) {
                Label dots = new Label("...");
                dots.getStyleClass().add("pagination-dots");
                paginationPageButtons.getChildren().add(dots);
                continue;
            }

            int targetPage = token.page();
            Button button = new Button(Integer.toString(targetPage + 1));
            button.getStyleClass().add("pagination-button");
            button.setMinSize(36, 36);
            button.setPrefSize(36, 36);
            button.setMaxSize(36, 36);
            button.setAccessibleText("Page " + (targetPage + 1));
            button.setOnAction(event -> goToPage(targetPage));
            pseudo(button, "selected", targetPage == currentPage);
            paginationPageButtons.getChildren().add(button);
        }
        updateJumpPageState();
    }

    private void updateJumpPageState() {
        if (jumpPageButton == null) {
            return;
        }
        jumpPageButton.setDisable(jumpPageField.getText() == null || jumpPageField.getText().isBlank());
    }

    private void submitJumpPage() {
        String rawPage = jumpPageField.getText();
        jumpPageField.clear();
        if (rawPage == null || rawPage.isBlank()) {
            return;
        }
        try {
            int targetPage = Integer.parseInt(rawPage.trim()) - 1;
            goToPage(targetPage);
        } catch (NumberFormatException ignored) {
            // Invalid jump values are intentionally treated like the web form: clear and stay put.
        }
    }

    private void goToPage(int page) {
        if (page < 0 || page >= totalPageCount || page == currentPage) {
            return;
        }
        currentPage = page;
        requestProjects();
        animateBrowseToTop();
    }

    private void animateBrowseToTop() {
        ScrollPane scrollPane = enclosingScrollPane();
        if (scrollPane != null) {
            animateBrowseToTop(scrollPane);
        }
        Platform.runLater(() -> {
            ScrollPane nextScrollPane = enclosingScrollPane();
            if (nextScrollPane != null && Math.abs(nextScrollPane.getVvalue() - nextScrollPane.getVmin()) > 0.001) {
                animateBrowseToTop(nextScrollPane);
            }
        });
    }

    private void animateBrowseToTop(ScrollPane scrollPane) {
        if (paginationScrollTimeline != null) {
            paginationScrollTimeline.stop();
        }

        double start = scrollPane.getVvalue();
        double end = scrollPane.getVmin();
        if (Math.abs(start - end) <= 0.001) {
            scrollPane.setVvalue(end);
            return;
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(scrollPane.vvalueProperty(), start)),
                new KeyFrame(PAGINATION_SCROLL_DURATION,
                        new KeyValue(scrollPane.vvalueProperty(), end, PAGINATION_SCROLL_EASE))
        );
        paginationScrollTimeline = timeline;
        timeline.setOnFinished(event -> {
            scrollPane.setVvalue(end);
            if (paginationScrollTimeline == timeline) {
                paginationScrollTimeline = null;
            }
        });
        timeline.play();
    }

    private ScrollPane enclosingScrollPane() {
        for (Node current = browseRoot; current != null; current = current.getParent()) {
            if (current instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
        }
        return null;
    }

    private static List<PageToken> pageTokens(int currentPage, int totalPages) {
        int total = Math.max(0, totalPages);
        int current = Math.max(0, currentPage) + 1;
        int delta = 2;
        Set<Integer> range = new LinkedHashSet<>();
        if (total >= 1) {
            range.add(1);
        }
        for (int i = current - delta; i <= current + delta; i++) {
            if (i < total && i > 1) {
                range.add(i);
            }
        }
        if (total > 1) {
            range.add(total);
        }

        List<Integer> sorted = new ArrayList<>(range);
        sorted.sort(Integer::compareTo);
        List<PageToken> tokens = new ArrayList<>();
        Integer previous = null;
        for (Integer page : sorted) {
            if (previous != null) {
                if (page - previous == 2) {
                    tokens.add(PageToken.page(previous));
                } else if (page - previous != 1) {
                    tokens.add(PageToken.gap());
                }
            }
            tokens.add(PageToken.page(page - 1));
            previous = page;
        }
        return tokens;
    }

    private void configureResultsIndicatorVisibility(HBox browseControls, HBox controlRow) {
        ChangeListener<Number> sizeListener = (observable, oldValue, newValue) ->
                scheduleResultsIndicatorVisibilityUpdate(browseControls, controlRow);
        browseControls.widthProperty().addListener(sizeListener);
        controlRow.widthProperty().addListener(sizeListener);
        categories.contentWidthProperty().addListener(sizeListener);
        categories.overflowingProperty().addListener((observable, oldValue, newValue) ->
                scheduleResultsIndicatorVisibilityUpdate(browseControls, controlRow));
        browseControls.sceneProperty().addListener((observable, oldScene, newScene) ->
                scheduleResultsIndicatorVisibilityUpdate(browseControls, controlRow));
        scheduleResultsIndicatorVisibilityUpdate(browseControls, controlRow);
    }

    private void scheduleResultsIndicatorVisibilityUpdate(HBox browseControls, HBox controlRow) {
        Platform.runLater(() -> updateResultsIndicatorVisibility(browseControls, controlRow));
    }

    private void updateResultsIndicatorVisibility(HBox browseControls, HBox controlRow) {
        double rowWidth = browseControls.getWidth();
        double categoryWidth = categories.contentWidth();
        if (!Double.isFinite(rowWidth) || rowWidth <= 0
                || !Double.isFinite(categoryWidth) || categoryWidth <= 0) {
            return;
        }

        double controlsWidth = measuredOrPref(controlRow.getWidth(), controlRow.prefWidth(-1));
        double availableCategoryWidthWithIndicator = rowWidth
                - controlsWidth
                - RESULTS_INDICATOR_WIDTH
                - browseControls.getSpacing() * 3;
        boolean canShowIndicator = availableCategoryWidthWithIndicator
                >= categoryWidth + RESULTS_INDICATOR_SHOW_BUFFER;
        setVisibleManaged(resultsIndicator, canShowIndicator);
    }

    private double measuredOrPref(double measured, double pref) {
        if (Double.isFinite(measured) && measured > 0) {
            return measured;
        }
        return Double.isFinite(pref) && pref > 0 ? pref : 0;
    }

    private void updateResultsIndicator(long totalItems, boolean searching) {
        long safeTotal = Math.max(0, totalItems);
        resultsIndicator.setText(searching
                ? "SEARCHING..."
                : String.format(Locale.US, "%,d %s", safeTotal, safeTotal == 1 ? "RESULT" : "RESULTS"));
        pseudo(resultsIndicator, "searching", searching);
    }

    private void observeSceneResizes(Scene oldScene, Scene newScene) {
        if (oldScene != null) {
            oldScene.widthProperty().removeListener(layoutResizeListener);
            oldScene.heightProperty().removeListener(layoutResizeListener);
        }
        if (newScene != null) {
            newScene.widthProperty().addListener(layoutResizeListener);
            newScene.heightProperty().addListener(layoutResizeListener);
        }
    }

    private void scheduleLayoutRefresh(Number oldValue, Number newValue) {
        if (oldValue == null || newValue == null
                || Math.abs(newValue.doubleValue() - oldValue.doubleValue()) > 0.5) {
            scheduleLayoutRefresh();
        }
    }

    private void scheduleLayoutRefresh() {
        if (layoutRefreshScheduled) {
            return;
        }
        layoutRefreshScheduled = true;
        Platform.runLater(() -> {
            layoutRefreshScheduled = false;
            if (currentView.get() == LauncherView.DISCOVER
                    && !currentProjects.isEmpty()
                    && renderer.shouldRenderForLayout(viewStyles.style(), selectedPageSize())) {
                renderProjectsForLayoutChange();
            }
        });
    }

    private record PageToken(Integer page, boolean ellipsis) {

        static PageToken page(int page) {
            return new PageToken(page, false);
        }

        static PageToken gap() {
            return new PageToken(null, true);
        }
    }

}
