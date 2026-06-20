package net.modtale.launcher.ui.project;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectMeta;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeDependencyModal {

    private static final double MODAL_WIDTH = 512;
    private static final double MODAL_MAX_HEIGHT = 720;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass MISSING = PseudoClass.getPseudoClass("missing");

    private final Supplier<StackPane> host;
    private final ModtaleApiClient apiClient;
    private final Executor executor;
    private final CachedImageLoader imageLoader;
    private final Consumer<DependencySelection> install;
    private final Map<Node, Effect> backdropEffects = new IdentityHashMap<>();
    private final Map<String, ProjectMeta> metadata = new ConcurrentHashMap<>();
    private final Set<String> requestedMetadataIds = ConcurrentHashMap.newKeySet();
    private final Set<String> selectedDependencyIds = new LinkedHashSet<>();

    private StackPane overlay;
    private ProjectDetail project;
    private ProjectVersion version;
    private String gameVersion;
    private List<ProjectDependency> dependencies = List.of();

    NativeDependencyModal(
            Supplier<StackPane> host,
            ModtaleApiClient apiClient,
            Executor executor,
            CachedImageLoader imageLoader,
            Consumer<DependencySelection> install
    ) {
        this.host = host == null ? () -> null : host;
        this.apiClient = apiClient;
        this.executor = executor;
        this.imageLoader = imageLoader;
        this.install = install == null ? ignored -> {
        } : install;
    }

    void show(ProjectDetail project, ProjectVersion version, String gameVersion) {
        this.project = project;
        this.version = version;
        this.gameVersion = gameVersion;
        this.dependencies = selectableDependencies(version);
        this.selectedDependencyIds.clear();
        this.dependencies.stream()
                .map(ProjectDependency::projectId)
                .filter(id -> id != null && !id.isBlank())
                .forEach(selectedDependencyIds::add);
        rebuildOverlay();
        requestDependencyMetadata();
    }

    void hide() {
        if (overlay == null) {
            return;
        }
        Parent parent = overlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(overlay);
        }
        overlay = null;
        restoreBackdrop();
    }

    private void rebuildOverlay() {
        StackPane hostPane = host.get();
        if (hostPane == null || project == null || version == null) {
            return;
        }
        if (overlay == null) {
            overlay = overlayShell();
            blurBackdrop(hostPane);
            hostPane.getChildren().add(overlay);
        }
        overlay.getChildren().setAll(modal());
        Platform.runLater(overlay::requestFocus);
    }

    private StackPane overlayShell() {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("dependency-modal-overlay");
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        shell.setFocusTraversable(true);
        shell.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });
        shell.setOnMouseClicked(event -> {
            if (event.getTarget() == shell) {
                hide();
            }
        });
        return shell;
    }

    private VBox modal() {
        VBox modal = new VBox(0);
        modal.getStyleClass().add("dependency-modal");
        modal.setMaxWidth(MODAL_WIDTH);
        modal.setPrefWidth(MODAL_WIDTH);
        modal.setMaxHeight(MODAL_MAX_HEIGHT);
        modal.setOnMouseClicked(event -> event.consume());

        ScrollPane bodyScroll = new ScrollPane(body());
        bodyScroll.getStyleClass().add("dependency-modal-scroll");
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);

        modal.getChildren().addAll(header(), bodyScroll, footer());
        return modal;
    }

    private HBox header() {
        HBox header = new HBox(16);
        header.getStyleClass().add("dependency-modal-header");
        header.setAlignment(Pos.CENTER_LEFT);

        HBox title = new HBox(8, LauncherIcons.icon(LauncherIcons.Glyph.LINK, 20), new Label("Dependencies"));
        title.getStyleClass().add("dependency-modal-title");
        title.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 18));
        close.getStyleClass().add("dependency-modal-close");
        close.setOnAction(event -> hide());
        header.getChildren().addAll(title, close);
        return header;
    }

    private VBox body() {
        VBox body = new VBox(16);
        body.getStyleClass().add("dependency-modal-body");
        body.getChildren().addAll(summaryRow(), dependencyList());
        Node warning = missingRequiredWarning();
        if (warning != null) {
            body.getChildren().add(warning);
        }
        return body;
    }

    private HBox summaryRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.TOP_LEFT);
        VBox copy = new VBox(2);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label description = new Label("Select dependencies to include in your bundle download.");
        description.getStyleClass().add("dependency-modal-description");
        description.setWrapText(true);
        copy.getChildren().add(description);

        Button toggle = new Button(selectedDependencyIds.size() == dependencies.size() ? "Deselect All" : "Select All");
        toggle.getStyleClass().add("dependency-modal-toggle-all");
        toggle.setOnAction(event -> toggleAll());
        row.getChildren().addAll(copy, toggle);
        return row;
    }

    private VBox dependencyList() {
        VBox list = new VBox(8);
        list.getStyleClass().add("dependency-modal-list");
        for (ProjectDependency dependency : dependencies) {
            list.getChildren().add(dependencyRow(dependency));
        }
        return list;
    }

    private Node dependencyRow(ProjectDependency dependency) {
        boolean selected = selectedDependencyIds.contains(dependency.projectId());
        boolean missingRequired = !dependency.isOptional() && !selected;

        HBox row = new HBox(16);
        row.getStyleClass().add("dependency-modal-row");
        row.pseudoClassStateChanged(SELECTED, selected);
        row.pseudoClassStateChanged(MISSING, missingRequired);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOnMouseClicked(event -> toggleDependency(dependency));

        HBox left = new HBox(16);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setMinWidth(0);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.getChildren().addAll(selectionState(selected, missingRequired), dependencyIcon(dependency), dependencyCopy(dependency));

        Label badge = dependencyBadge(dependency, missingRequired);
        row.getChildren().addAll(left, badge);
        return row;
    }

    private StackPane selectionState(boolean selected, boolean missingRequired) {
        StackPane state = new StackPane();
        state.getStyleClass().add("dependency-modal-state");
        if (selected) {
            state.getStyleClass().add("selected");
            state.getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 13));
        } else if (missingRequired) {
            state.getStyleClass().add("missing");
            Label bang = new Label("!");
            bang.getStyleClass().add("dependency-modal-state-warning");
            state.getChildren().add(bang);
        }
        return state;
    }

    private StackPane dependencyIcon(ProjectDependency dependency) {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("dependency-modal-icon");
        String iconUrl = dependencyIconUrl(dependency);
        if (imageLoader != null && iconUrl != null && !iconUrl.isBlank()) {
            ImageView image = new ImageView();
            image.setFitWidth(44);
            image.setFitHeight(44);
            image.setPreserveRatio(false);
            image.setSmooth(true);
            Rectangle clip = new Rectangle(44, 44);
            clip.setArcWidth(8);
            clip.setArcHeight(8);
            image.setClip(clip);
            imageLoader.loadInto(image, iconUrl, 88, 88);
            icon.getChildren().add(image);
        } else {
            icon.getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.BOX, 20));
        }
        return icon;
    }

    private VBox dependencyCopy(ProjectDependency dependency) {
        VBox copy = new VBox(4);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Label title = new Label(dependencyTitle(dependency));
        title.getStyleClass().add("dependency-modal-dependency-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMaxWidth(Double.MAX_VALUE);

        HBox meta = new HBox(7);
        meta.getStyleClass().add("dependency-modal-meta");
        meta.setAlignment(Pos.CENTER_LEFT);
        Label author = new Label("by " + dependencyAuthor(dependency));
        author.getStyleClass().add("dependency-modal-author");
        meta.getChildren().add(author);
        if (!isBlank(dependency.versionNumber())) {
            Region dot = new Region();
            dot.getStyleClass().add("dependency-modal-dot");
            Label versionLabel = new Label("v" + dependency.versionNumber());
            versionLabel.getStyleClass().add("dependency-modal-version");
            meta.getChildren().addAll(dot, versionLabel);
        }

        copy.getChildren().addAll(title, meta);
        return copy;
    }

    private Label dependencyBadge(ProjectDependency dependency, boolean missingRequired) {
        Label badge = new Label(dependency.isOptional() ? "OPTIONAL" : "REQUIRED");
        badge.getStyleClass().add("dependency-modal-badge");
        if (missingRequired) {
            badge.getStyleClass().add("missing");
            badge.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 12));
        } else if (dependency.isOptional()) {
            badge.getStyleClass().add("optional");
        } else {
            badge.getStyleClass().add("required");
        }
        return badge;
    }

    private Node missingRequiredWarning() {
        if (!missingRequired()) {
            return null;
        }
        HBox warning = new HBox(8);
        warning.getStyleClass().add("dependency-modal-warning");
        warning.setAlignment(Pos.TOP_LEFT);
        Label copy = new Label("You have unselected Required dependencies. The project may not function correctly without them.");
        copy.getStyleClass().add("dependency-modal-warning-copy");
        copy.setWrapText(true);
        HBox.setHgrow(copy, Priority.ALWAYS);
        warning.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 16), copy);
        return warning;
    }

    private HBox footer() {
        HBox footer = new HBox();
        footer.getStyleClass().add("dependency-modal-footer");
        footer.setAlignment(Pos.CENTER);
        Button download = new Button();
        download.getStyleClass().add("dependency-modal-download");
        download.setMaxWidth(Double.MAX_VALUE);
        download.setAlignment(Pos.CENTER);
        download.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        download.setGraphic(downloadButtonContent());
        download.setOnAction(event -> installSelected());
        HBox.setHgrow(download, Priority.ALWAYS);
        footer.getChildren().add(download);
        return footer;
    }

    private VBox downloadButtonContent() {
        VBox content = new VBox(3);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(Double.MAX_VALUE);
        HBox title = new HBox(8, LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 20),
                new Label(selectedDependencyIds.isEmpty() ? "Download Project Only" : "Download Bundle"));
        title.getStyleClass().add("dependency-modal-download-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(title);
        if (!selectedDependencyIds.isEmpty()) {
            Label subtitle = new Label("Includes project + " + selectedDependencyIds.size()
                    + " dependenc" + (selectedDependencyIds.size() == 1 ? "y" : "ies"));
            subtitle.getStyleClass().add("dependency-modal-download-subtitle");
            subtitle.setAlignment(Pos.CENTER);
            subtitle.setMaxWidth(Double.MAX_VALUE);
            content.getChildren().add(subtitle);
        }
        return content;
    }

    private void toggleDependency(ProjectDependency dependency) {
        String id = dependency.projectId();
        if (id == null || id.isBlank()) {
            return;
        }
        if (selectedDependencyIds.contains(id)) {
            selectedDependencyIds.remove(id);
        } else {
            selectedDependencyIds.add(id);
        }
        rebuildOverlay();
    }

    private void toggleAll() {
        if (selectedDependencyIds.size() == dependencies.size()) {
            selectedDependencyIds.clear();
        } else {
            selectedDependencyIds.clear();
            dependencies.stream()
                    .map(ProjectDependency::projectId)
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(selectedDependencyIds::add);
        }
        rebuildOverlay();
    }

    private boolean missingRequired() {
        return dependencies.stream()
                .anyMatch(dependency -> !dependency.isOptional() && !selectedDependencyIds.contains(dependency.projectId()));
    }

    private void installSelected() {
        ProjectDetail selectedProject = project;
        ProjectVersion selectedVersion = version;
        String selectedGameVersion = gameVersion;
        List<ProjectDependency> selectedDependencies = dependencies.stream()
                .filter(dependency -> selectedDependencyIds.contains(dependency.projectId()))
                .toList();
        hide();
        install.accept(new DependencySelection(selectedProject, selectedVersion, selectedGameVersion, selectedDependencies));
    }

    private void requestDependencyMetadata() {
        if (apiClient == null || executor == null || dependencies.isEmpty()) {
            return;
        }
        List<String> missing = dependencies.stream()
                .map(ProjectDependency::projectId)
                .filter(id -> id != null && !id.isBlank())
                .filter(id -> !metadata.containsKey(id) && requestedMetadataIds.add(id))
                .distinct()
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        ProjectDetail expectedProject = project;
        ProjectVersion expectedVersion = version;
        CompletableFuture.supplyAsync(() -> apiClient.getProjectMetaBatch(missing), executor)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    Map<String, ProjectMeta> next = error == null && result != null ? result : Map.of();
                    for (String id : missing) {
                        metadata.put(id, next.getOrDefault(id, fallbackMeta(id)));
                    }
                    if (project == expectedProject && version == expectedVersion && overlay != null) {
                        rebuildOverlay();
                    }
                }));
    }

    private void blurBackdrop(StackPane hostPane) {
        restoreBackdrop();
        for (Node child : hostPane.getChildren()) {
            backdropEffects.put(child, child.getEffect());
            child.setEffect(new GaussianBlur(6));
        }
    }

    private void restoreBackdrop() {
        backdropEffects.forEach(Node::setEffect);
        backdropEffects.clear();
    }

    static List<ProjectDependency> selectableDependencies(ProjectVersion version) {
        if (version == null || version.dependencies() == null) {
            return List.of();
        }
        return version.dependencies().stream()
                .filter(dependency -> dependency != null
                        && !dependency.isEmbedded()
                        && !dependency.isExternal()
                        && !isBlank(dependency.projectId()))
                .toList();
    }

    private String dependencyTitle(ProjectDependency dependency) {
        ProjectMeta meta = dependencyMeta(dependency);
        return firstNonBlank(
                meta == null ? null : meta.title(),
                dependency.title(),
                dependency.projectTitle(),
                dependency.projectId(),
                dependency.id(),
                "Dependency"
        );
    }

    private String dependencyAuthor(ProjectDependency dependency) {
        ProjectMeta meta = dependencyMeta(dependency);
        return firstNonBlank(meta == null ? null : meta.author(), "...");
    }

    private String dependencyIconUrl(ProjectDependency dependency) {
        ProjectMeta meta = dependencyMeta(dependency);
        return firstNonBlank(meta == null ? null : meta.icon(), dependency.icon());
    }

    private ProjectMeta dependencyMeta(ProjectDependency dependency) {
        if (dependency == null || isBlank(dependency.projectId())) {
            return null;
        }
        return metadata.get(dependency.projectId());
    }

    private static ProjectMeta fallbackMeta(String projectId) {
        return new ProjectMeta("", "", "", "...", "", 0, "", "");
    }

    private static String firstNonBlank(String... values) {
        for (String candidate : values) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record DependencySelection(
            ProjectDetail project,
            ProjectVersion version,
            String gameVersion,
            List<ProjectDependency> selectedDependencies
    ) {
    }
}
