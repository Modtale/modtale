package net.modtale.launcher.ui.browse.render;

import static net.modtale.launcher.ui.common.LauncherUi.emptyState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.LauncherPerformanceProbe;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;

public final class ProjectBrowserRenderer {

    private static final int MAX_CACHED_PROJECT_CARDS = 256;
    private static final int LIST_PAGE_SIZE = 12;
    private static final int CARD_PAGE_SIZE = 12;
    private static final int COMPACT_PAGE_SIZE = 45;
    private static final double GRID_MIN_CARD_BODY_HEIGHT = 216;
    private static final double GRID_MAX_CARD_BODY_HEIGHT = 228;
    private static final double GRID_FALLBACK_WIDTH = 936;
    private static final double GRID_THREE_COLUMN_WIDTH = 1320;
    private static final double GRID_HORIZONTAL_GAP = 27;
    private static final double GRID_VERTICAL_GAP = 30;
    private static final double COMPACT_FALLBACK_WIDTH = 936;
    private static final double COMPACT_CARD_HEIGHT = 90;
    private static final double COMPACT_THREE_COLUMN_WIDTH = 1120;
    private static final double COMPACT_HORIZONTAL_GAP = 19.5;
    private static final double COMPACT_VERTICAL_GAP = 19.5;

    private final StackPane projectResults;
    private final StackPane viewDeck;
    private final Supplier<VBox> contentBody;
    private final ProjectCardFactory projectCardFactory;
    private final Function<String, Boolean> favoriteResolver;
    private final Supplier<String> gameVersion;
    private final Consumer<ProjectSummary> onInstall;
    private final Consumer<ProjectSummary> onOpenPage;
    private final Consumer<ProjectSummary> onOpenCreator;
    private final Consumer<ProjectSummary> onToggleFavorite;
    private final Map<ProjectCardKey, Node> projectCardCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ProjectCardKey, Node> eldest) {
            return size() > MAX_CACHED_PROJECT_CARDS;
        }
    };

    private LayoutMetrics lastRenderedLayout = LayoutMetrics.unset();

    public ProjectBrowserRenderer(
            StackPane projectResults,
            StackPane viewDeck,
            Supplier<VBox> contentBody,
            ProjectCardFactory projectCardFactory,
            Function<String, Boolean> favoriteResolver,
            Supplier<String> gameVersion,
            Consumer<ProjectSummary> onInstall,
            Consumer<ProjectSummary> onOpenPage,
            Consumer<ProjectSummary> onOpenCreator,
            Consumer<ProjectSummary> onToggleFavorite
    ) {
        this.projectResults = projectResults;
        this.viewDeck = viewDeck;
        this.contentBody = contentBody;
        this.projectCardFactory = projectCardFactory;
        this.favoriteResolver = favoriteResolver;
        this.gameVersion = gameVersion;
        this.onInstall = onInstall;
        this.onOpenPage = onOpenPage;
        this.onOpenCreator = onOpenCreator;
        this.onToggleFavorite = onToggleFavorite;
    }

    public void render(List<ProjectSummary> projects, ProjectCardViewStyle cardViewStyle) {
        render(projects, cardViewStyle, pageSizeForView(cardViewStyle));
    }

    public void render(List<ProjectSummary> projects, ProjectCardViewStyle cardViewStyle, int pageSize) {
        long operationStart = LauncherPerformanceProbe.operationStartNanos();
        try {
            if (projects.isEmpty()) {
                lastRenderedLayout = LayoutMetrics.unset();
                projectResults.getChildren().setAll(emptyState("No matches found", "Try another search term or category."));
                return;
            }
            LayoutMetrics layout = layoutMetricsFor(cardViewStyle, pageSize);
            Node container = resultsContainer(cardViewStyle, layout);
            int cardCount = Math.min(projects.size(), layout.pageSize());
            List<Node> cards = new ArrayList<>(cardCount);
            String selectedGameVersion = gameVersion.get();
            for (int i = 0; i < cardCount; i++) {
                cards.add(projectCard(projects.get(i), cardViewStyle, selectedGameVersion,
                        layout.cardWidth(), layout.cardHeight()));
            }
            if (container instanceof GridPane gridPane) {
                gridPane.getChildren().clear();
                for (int i = 0; i < cards.size(); i++) {
                    gridPane.add(cards.get(i), i % layout.columns(), i / layout.columns());
                }
            } else if (container instanceof VBox vBox) {
                vBox.getChildren().setAll(cards);
            }
            projectResults.getChildren().setAll(container);
            lastRenderedLayout = layout;
        } finally {
            LauncherPerformanceProbe.recordOperation("browse.render", operationStart);
        }
    }

    public boolean shouldRenderForLayout(ProjectCardViewStyle cardViewStyle) {
        return shouldRenderForLayout(cardViewStyle, pageSizeForView(cardViewStyle));
    }

    public boolean shouldRenderForLayout(ProjectCardViewStyle cardViewStyle, int pageSize) {
        if (cardViewStyle == ProjectCardViewStyle.LIST) {
            return false;
        }
        LayoutMetrics nextLayout = layoutMetricsFor(cardViewStyle, pageSize);
        return !nextLayout.sameGeometry(lastRenderedLayout);
    }

    public int pageSizeForView(ProjectCardViewStyle cardViewStyle) {
        return layoutMetricsFor(cardViewStyle).pageSize();
    }

    int columnsForView(ProjectCardViewStyle cardViewStyle) {
        return layoutMetricsFor(cardViewStyle).columns();
    }

    int pageSizeForView(ProjectCardViewStyle cardViewStyle, int requestedPageSize) {
        return layoutMetricsFor(cardViewStyle, requestedPageSize).pageSize();
    }

    private Node projectCard(
            ProjectSummary project,
            ProjectCardViewStyle cardViewStyle,
            String selectedGameVersion,
            double cardWidth,
            double cardHeight
    ) {
        boolean favorite = Boolean.TRUE.equals(favoriteResolver.apply(project.id()));
        ProjectCardKey key = new ProjectCardKey(project.routeKey(), cardViewStyle, selectedGameVersion, favorite,
                cardWidthKey(cardViewStyle, cardWidth), cardHeightKey(cardViewStyle, cardHeight), projectSignature(project));
        Node card = projectCardCache.get(key);
        if (card == null) {
            card = projectCardFactory.create(project, cardViewStyle, selectedGameVersion, favorite,
                    onInstall, onOpenPage, onOpenCreator, onToggleFavorite, cardWidth, cardHeight);
            projectCardCache.put(key, card);
        }
        detachFromParent(card);
        return card;
    }

    private Node resultsContainer(ProjectCardViewStyle cardViewStyle, LayoutMetrics layout) {
        if (cardViewStyle == ProjectCardViewStyle.LIST) {
            VBox list = new VBox(21);
            list.getStyleClass().add("project-list");
            list.setFillWidth(true);
            return list;
        }

        GridPane grid = new GridPane();
        grid.getStyleClass().add(cardViewStyle == ProjectCardViewStyle.COMPACT ? "compact-grid" : "project-grid");
        grid.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        grid.setHgap(layout.horizontalGap());
        grid.setVgap(layout.verticalGap());
        grid.setMinWidth(0);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setPrefWidth(layout.availableWidth());
        return grid;
    }

    private void detachFromParent(Node node) {
        if (node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    private int cardWidthKey(ProjectCardViewStyle cardViewStyle, double cardWidth) {
        return cardViewStyle == ProjectCardViewStyle.LIST ? 0 : (int) Math.round(cardWidth);
    }

    private int cardHeightKey(ProjectCardViewStyle cardViewStyle, double cardHeight) {
        return cardViewStyle == ProjectCardViewStyle.LIST ? 0 : (int) Math.round(cardHeight);
    }

    private int projectSignature(ProjectSummary project) {
        return Objects.hash(project.slug(), project.title(), project.description(), project.authorId(), project.author(),
                project.imageUrl(), project.bannerUrl(), project.classification(), project.downloadCount(),
                project.favoriteCount(), project.updatedAt(), project.versions());
    }

    private LayoutMetrics layoutMetricsFor(ProjectCardViewStyle cardViewStyle) {
        return layoutMetricsFor(cardViewStyle, defaultPageSizeForView(cardViewStyle));
    }

    private LayoutMetrics layoutMetricsFor(ProjectCardViewStyle cardViewStyle, int requestedPageSize) {
        int pageSize = sanitizePageSize(requestedPageSize);
        return switch (cardViewStyle) {
            case LIST -> LayoutMetrics.list(pageSize);
            case COMPACT -> compactLayoutMetrics(pageSize);
            case GRID -> gridLayoutMetrics(pageSize);
        };
    }

    private int defaultPageSizeForView(ProjectCardViewStyle cardViewStyle) {
        return switch (cardViewStyle) {
            case LIST -> LIST_PAGE_SIZE;
            case COMPACT -> COMPACT_PAGE_SIZE;
            case GRID -> CARD_PAGE_SIZE;
        };
    }

    private int sanitizePageSize(int requestedPageSize) {
        return Math.max(1, Math.min(100, requestedPageSize));
    }

    private LayoutMetrics gridLayoutMetrics(int pageSize) {
        double availableWidth = measuredOrFallback(browseResultsWidth(), GRID_FALLBACK_WIDTH);
        int columns = breakpointColumns(availableWidth, GRID_THREE_COLUMN_WIDTH);
        double cardWidth = cellSize(availableWidth, columns, GRID_HORIZONTAL_GAP);
        double bannerHeight = Math.round(cardWidth / 3.0);
        double bodyHeight = Math.round(clamp(cardWidth * 0.52, GRID_MIN_CARD_BODY_HEIGHT, GRID_MAX_CARD_BODY_HEIGHT));
        double cardHeight = bannerHeight + bodyHeight;
        return new LayoutMetrics(
                columns,
                rowsForPageSize(columns, pageSize),
                pageSize,
                cardWidth,
                cardHeight,
                availableWidth,
                GRID_HORIZONTAL_GAP,
                GRID_VERTICAL_GAP
        );
    }

    private LayoutMetrics compactLayoutMetrics(int pageSize) {
        double availableWidth = measuredOrFallback(browseResultsWidth(), COMPACT_FALLBACK_WIDTH);
        int columns = breakpointColumns(availableWidth, COMPACT_THREE_COLUMN_WIDTH);
        double cardWidth = cellSize(availableWidth, columns, COMPACT_HORIZONTAL_GAP);
        return new LayoutMetrics(
                columns,
                rowsForPageSize(columns, pageSize),
                pageSize,
                cardWidth,
                COMPACT_CARD_HEIGHT,
                availableWidth,
                COMPACT_HORIZONTAL_GAP,
                COMPACT_VERTICAL_GAP
        );
    }

    private int breakpointColumns(double availableWidth, double threeColumnWidth) {
        if (availableWidth >= threeColumnWidth) {
            return 3;
        }
        return 2;
    }

    private int rowsForPageSize(int columns, int pageSize) {
        return Math.max(1, (int) Math.ceil((double) pageSize / Math.max(1, columns)));
    }

    private double cellSize(double available, int count, double gap) {
        return Math.max(1, (available - gap * (count - 1)) / count);
    }

    private double measuredOrFallback(double measured, double fallback) {
        return Double.isFinite(measured) && measured > 0 ? measured : fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double browseResultsWidth() {
        double viewportWidth = enclosingScrollViewportWidth();
        if (viewportWidth > 0) {
            return viewportWidth;
        }
        double parentWidth = nodeWidth(projectResults.getParent());
        if (parentWidth > 0) {
            return parentWidth;
        }
        double deckWidth = nodeWidth(viewDeck);
        if (deckWidth > 0) {
            return deckWidth;
        }
        VBox body = contentBody.get();
        double bodyWidth = nodeWidth(body);
        if (bodyWidth > 0) {
            return bodyWidth;
        }
        return nodeWidth(projectResults);
    }

    private double enclosingScrollViewportWidth() {
        double width = scrollViewportWidth(projectResults);
        if (width > 0) {
            return width;
        }
        return scrollViewportWidth(contentBody.get());
    }

    private double scrollViewportWidth(Node start) {
        for (Node current = start; current != null; current = current.getParent()) {
            if (current instanceof ScrollPane scrollPane) {
                Bounds viewport = scrollPane.getViewportBounds();
                return viewport == null ? 0 : usableWidth(viewport.getWidth());
            }
        }
        return 0;
    }

    private double nodeWidth(Node node) {
        if (node == null) {
            return 0;
        }
        if (node instanceof Region region) {
            double regionWidth = usableWidth(region.getWidth());
            if (regionWidth > 0) {
                return regionWidth;
            }
        }
        return usableWidth(node.getLayoutBounds().getWidth());
    }

    private double usableWidth(double width) {
        return Double.isFinite(width) && width > 0 ? width : 0;
    }

    private record ProjectCardKey(
            String projectId,
            ProjectCardViewStyle viewStyle,
            String gameVersion,
            boolean favorite,
            int cardWidth,
            int cardHeight,
            int projectSignature
    ) {
    }

    private record LayoutMetrics(
            int columns,
            int rows,
            int pageSize,
            double cardWidth,
            double cardHeight,
            double availableWidth,
            double horizontalGap,
            double verticalGap
    ) {

        static LayoutMetrics list(int pageSize) {
            return new LayoutMetrics(1, pageSize, pageSize, 0, 0, 0, 0, 0);
        }

        static LayoutMetrics unset() {
            return new LayoutMetrics(0, 0, 0, -1, -1, -1, 0, 0);
        }

        boolean sameGeometry(LayoutMetrics other) {
            return other != null
                    && columns == other.columns
                    && rows == other.rows
                    && pageSize == other.pageSize
                    && Math.abs(cardWidth - other.cardWidth) <= 1
                    && Math.abs(cardHeight - other.cardHeight) <= 1
                    && Math.abs(availableWidth - other.availableWidth) <= 1;
        }
    }
}
