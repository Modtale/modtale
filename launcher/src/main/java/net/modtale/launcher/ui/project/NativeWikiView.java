package net.modtale.launcher.ui.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.WikiBundle;
import net.modtale.launcher.model.project.WikiBundle.WikiNode;
import net.modtale.launcher.model.project.WikiBundle.WikiPage;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeWikiView {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final NativeMarkdownRenderer markdownRenderer;
    private final Consumer<String> openUrl;

    NativeWikiView(NativeMarkdownRenderer markdownRenderer, Consumer<String> openUrl) {
        this.markdownRenderer = markdownRenderer;
        this.openUrl = openUrl;
    }

    VBox main(
            WikiBundle bundle,
            boolean loading,
            boolean error,
            ProjectDetail project,
            String activeSlug
    ) {
        VBox main = new VBox(0);
        main.getStyleClass().addAll("project-detail-main", "project-wiki-main");
        main.setMinWidth(0);
        main.setMaxWidth(Double.MAX_VALUE);

        if (bundle == null && loading) {
            VBox state = state("Loading wiki…", "Fetching this project's HytaleModding documentation.", true);
            main.getChildren().add(state);
            return main;
        }
        if (bundle == null || error) {
            main.getChildren().add(state(
                    "No Wiki Available",
                    "This project does not have a valid HytaleModding wiki set up.",
                    false
            ));
            return main;
        }

        WikiPage page = bundle.content();
        VBox content = new VBox(0);
        content.getStyleClass().add("project-wiki-content");
        if (loading) content.getStyleClass().add("loading");

        if (page.empty()) {
            Label empty = new Label("Page content is empty.");
            empty.getStyleClass().add("project-wiki-empty-page");
            content.getChildren().add(empty);
        } else {
            Label title = new Label(first(page.title(), bundle.wikiName(), project == null ? null : project.title(), "Wiki"));
            title.getStyleClass().add("project-wiki-title");
            title.setWrapText(true);
            title.setMaxWidth(Double.MAX_VALUE);
            content.getChildren().addAll(title, markdownRenderer.render(page.content()));
        }
        Node footer = footer(bundle, project, activeSlug);
        VBox.setMargin(footer, new Insets(40, 0, 0, 0));
        content.getChildren().add(footer);
        main.getChildren().add(content);
        return main;
    }

    VBox sidebar(
            WikiBundle bundle,
            String activeSlug,
            Map<String, WikiPage> pageCache,
            Consumer<String> navigate,
            Consumer<String> prefetch,
            Runnable backToProject,
            boolean compact
    ) {
        VBox sidebar = new VBox(compact ? 12 : 24);
        sidebar.getStyleClass().addAll("project-detail-sidebar", "project-wiki-sidebar");
        if (compact) sidebar.getStyleClass().add("project-detail-sidebar-compact");

        List<WikiNode> pages = bundle == null ? List.of() : bundle.pages();
        VBox tree = new VBox(4);
        tree.getStyleClass().add("project-wiki-tree");
        String selectedSlug = first(activeSlug, bundle == null ? null : bundle.indexSlug(), firstSlug(pages));
        populateTree(tree, pages, selectedSlug, pageCache, navigate, prefetch);

        Node navigationContent = tree;
        if (compact) {
            TextField search = new TextField();
            search.setPromptText("Search pages");
            search.getStyleClass().add("project-wiki-search");
            List<FlatPage> flatPages = flatten(pages, List.of());
            search.textProperty().addListener((observable, previous, query) -> {
                String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
                tree.getChildren().clear();
                if (normalized.isEmpty()) {
                    populateTree(tree, pages, selectedSlug, pageCache, navigate, prefetch);
                    return;
                }
                flatPages.stream()
                        .filter(page -> page.searchText().contains(normalized))
                        .forEach(page -> tree.getChildren().add(searchResult(page, navigate, prefetch)));
                if (tree.getChildren().isEmpty()) {
                    Label empty = new Label("No wiki pages match your search.");
                    empty.getStyleClass().add("project-wiki-search-empty");
                    tree.getChildren().add(empty);
                }
            });
            VBox compactNavigation = new VBox(10, search, tree);
            compactNavigation.getStyleClass().add("project-wiki-compact-navigation");
            navigationContent = compactNavigation;
        }

        HBox heading = new HBox(8, LauncherIcons.icon(LauncherIcons.Glyph.BOOK_OPEN, 13), new Label("WIKI NAVIGATION"));
        heading.getStyleClass().add("project-detail-sidebar-heading");
        heading.setAlignment(Pos.CENTER_LEFT);
        TitledPane navigation = new TitledPane(null, navigationContent);
        navigation.getStyleClass().addAll("project-detail-sidebar-section", "project-wiki-navigation");
        navigation.setGraphic(heading);
        navigation.setExpanded(!compact);
        navigation.setAnimated(false);

        Button back = new Button("Back to Project", LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_LEFT, 15));
        back.getStyleClass().add("project-wiki-back");
        back.setOnAction(event -> backToProject.run());
        sidebar.getChildren().addAll(navigation, back);
        return sidebar;
    }

    private void populateTree(
            VBox tree,
            List<WikiNode> pages,
            String selectedSlug,
            Map<String, WikiPage> pageCache,
            Consumer<String> navigate,
            Consumer<String> prefetch
    ) {
        for (WikiNode node : pages) {
            tree.getChildren().add(treeNode(node, selectedSlug, pageCache, navigate, prefetch, 0));
        }
    }

    private Node searchResult(FlatPage result, Consumer<String> navigate, Consumer<String> prefetch) {
        VBox copy = new VBox(2);
        Label title = new Label(result.title());
        title.getStyleClass().add("project-wiki-search-title");
        copy.getChildren().add(title);
        if (!result.parents().isEmpty()) {
            Label parents = new Label(String.join(" / ", result.parents()));
            parents.getStyleClass().add("project-wiki-search-parents");
            copy.getChildren().add(parents);
        }
        Button button = new Button();
        button.setGraphic(copy);
        button.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        button.getStyleClass().add("project-wiki-search-result");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> navigate.accept(result.slug()));
        button.setOnMouseEntered(event -> prefetch.accept(result.slug()));
        button.focusedProperty().addListener((observable, previous, focused) -> {
            if (focused) prefetch.accept(result.slug());
        });
        return button;
    }

    private Node treeNode(
            WikiNode node,
            String activeSlug,
            Map<String, WikiPage> pageCache,
            Consumer<String> navigate,
            Consumer<String> prefetch,
            int depth
    ) {
        VBox item = new VBox(4);
        item.getStyleClass().add("project-wiki-tree-item");
        boolean hasChildren = !node.children().isEmpty();
        boolean active = node.slug() != null && node.slug().equals(activeSlug);
        boolean navigable = node.slug() != null && !(hasChildren && knownEmpty(node.slug(), pageCache));

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        Button page = new Button(node.title());
        page.getStyleClass().add("project-wiki-page-link");
        page.setMaxWidth(Double.MAX_VALUE);
        page.setAlignment(Pos.CENTER_LEFT);
        page.setPadding(new Insets(8, 12, 8, 12 + depth * 12));
        page.pseudoClassStateChanged(SELECTED, active);
        page.setDisable(!navigable);
        if (navigable) {
            page.setOnAction(event -> navigate.accept(node.slug()));
            page.setOnMouseEntered(event -> prefetch.accept(node.slug()));
            page.focusedProperty().addListener((observable, previous, focused) -> {
                if (focused) prefetch.accept(node.slug());
            });
        }
        HBox.setHgrow(page, Priority.ALWAYS);
        row.getChildren().add(page);

        VBox children = new VBox(4);
        children.getStyleClass().add("project-wiki-tree-children");
        for (WikiNode child : node.children()) {
            children.getChildren().add(treeNode(child, activeSlug, pageCache, navigate, prefetch, depth + 1));
        }
        if (hasChildren) {
            boolean initiallyOpen = node.contains(activeSlug) || depth == 0;
            children.setVisible(initiallyOpen);
            children.setManaged(initiallyOpen);
            Button toggle = new Button(null, LauncherIcons.icon(
                    initiallyOpen ? LauncherIcons.Glyph.CHEVRON_DOWN : LauncherIcons.Glyph.CHEVRON_RIGHT, 14));
            toggle.getStyleClass().add("project-wiki-tree-toggle");
            toggle.setAccessibleText((initiallyOpen ? "Collapse " : "Expand ") + node.title());
            toggle.setOnAction(event -> {
                boolean show = !children.isManaged();
                children.setManaged(show);
                children.setVisible(show);
                toggle.setGraphic(LauncherIcons.icon(
                        show ? LauncherIcons.Glyph.CHEVRON_DOWN : LauncherIcons.Glyph.CHEVRON_RIGHT, 14));
                toggle.setAccessibleText((show ? "Collapse " : "Expand ") + node.title());
            });
            row.getChildren().add(toggle);
        }
        item.getChildren().add(row);
        if (hasChildren) item.getChildren().add(children);
        return item;
    }

    private Node footer(WikiBundle bundle, ProjectDetail project, String activeSlug) {
        HBox footer = new HBox(16);
        footer.getStyleClass().add("project-wiki-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        Label powered = new Label("Powered by HytaleModding");
        powered.getStyleClass().add("project-wiki-powered");
        powered.setOnMouseClicked(event -> openUrl.accept("https://wiki.hytalemodding.dev"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button external = new Button("View on HytaleModding", LauncherIcons.icon(LauncherIcons.Glyph.EXTERNAL_LINK, 14));
        external.getStyleClass().add("project-wiki-external");
        external.setOnAction(event -> openUrl.accept(hytaleModdingUrl(bundle, project, activeSlug)));
        footer.getChildren().addAll(powered, spacer, external);
        return footer;
    }

    private VBox state(String titleText, String bodyText, boolean loading) {
        VBox state = new VBox(10);
        state.getStyleClass().add("project-wiki-state");
        state.setAlignment(Pos.CENTER);
        state.getChildren().add(loading
                ? NativeSpinner.inline(36)
                : LauncherIcons.icon(LauncherIcons.Glyph.BOOK_OPEN, 48));
        Label title = new Label(titleText);
        title.getStyleClass().add("project-wiki-state-title");
        Label body = new Label(bodyText);
        body.getStyleClass().add("project-wiki-state-copy");
        body.setWrapText(true);
        state.getChildren().addAll(title, body);
        return state;
    }

    static String hytaleModdingUrl(WikiBundle bundle, ProjectDetail project, String activeSlug) {
        String projectSlug = project == null ? null : project.hmWikiSlug();
        String base = "https://wiki.hytalemodding.dev/mod/" + first(projectSlug, "");
        String indexSlug = bundle == null ? null : bundle.indexSlug();
        return activeSlug == null || activeSlug.isBlank() || activeSlug.equals(indexSlug)
                ? base
                : base + "/" + activeSlug;
    }

    private static boolean knownEmpty(String slug, Map<String, WikiPage> pageCache) {
        return pageCache != null && pageCache.containsKey(slug)
                && (pageCache.get(slug) == null || pageCache.get(slug).empty());
    }

    private static String firstSlug(List<WikiNode> pages) {
        for (WikiNode page : pages) {
            if (page.slug() != null && !page.slug().isBlank()) return page.slug();
        }
        return null;
    }

    private static List<FlatPage> flatten(List<WikiNode> nodes, List<String> parents) {
        List<FlatPage> result = new ArrayList<>();
        for (WikiNode node : nodes) {
            if (node.slug() != null && !node.slug().isBlank()) {
                result.add(new FlatPage(node.slug(), node.title(), parents));
            }
            List<String> childParents = new ArrayList<>(parents);
            childParents.add(node.title());
            result.addAll(flatten(node.children(), List.copyOf(childParents)));
        }
        return List.copyOf(result);
    }

    private record FlatPage(String slug, String title, List<String> parents) {
        private String searchText() {
            return (title + " " + slug + " " + String.join(" ", parents)).toLowerCase(Locale.ROOT);
        }
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
