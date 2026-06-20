package net.modtale.launcher.ui.project;

import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.timeAgo;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.project.GameVersionCatalog;
import net.modtale.launcher.model.project.ProjectClassification;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.ui.common.GameVersionDropdown;
import net.modtale.launcher.ui.common.GameVersionGroups;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeDownloadModal {

    private static final double MODAL_WIDTH = 672;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Supplier<StackPane> host;
    private final Supplier<String> preferredGameVersion;
    private final Consumer<DownloadSelection> install;
    private final Consumer<ProjectDetail> viewHistory;

    private StackPane overlay;
    private ProjectDetail project;
    private GameVersionCatalog catalog;
    private List<String> selectedGameVersions = List.of();
    private boolean showExperimental;
    private boolean showPreReleaseGameVersions;
    private boolean listExpanded;
    private boolean loading;
    private boolean gameVersionDropdownOpen;

    NativeDownloadModal(
            Supplier<StackPane> host,
            Supplier<String> preferredGameVersion,
            Consumer<DownloadSelection> install,
            Consumer<ProjectDetail> viewHistory
    ) {
        this.host = host == null ? () -> null : host;
        this.preferredGameVersion = preferredGameVersion == null ? () -> "" : preferredGameVersion;
        this.install = install == null ? selection -> {
        } : install;
        this.viewHistory = viewHistory == null ? ignored -> {
        } : viewHistory;
    }

    void show(ProjectDetail project, GameVersionCatalog catalog) {
        this.project = project;
        this.catalog = catalog == null ? GameVersionCatalog.fromVersions(List.of()) : catalog;
        this.showExperimental = false;
        this.showPreReleaseGameVersions = forceShowPreReleaseGameVersions();
        this.listExpanded = false;
        this.loading = false;
        this.gameVersionDropdownOpen = false;
        this.selectedGameVersions = preferredVisibleGameVersions();
        rebuildOverlay();
    }

    void showLoading(ProjectDetail project, GameVersionCatalog catalog) {
        this.project = project;
        this.catalog = catalog == null ? GameVersionCatalog.fromVersions(List.of()) : catalog;
        this.showExperimental = false;
        this.showPreReleaseGameVersions = forceShowPreReleaseGameVersions();
        this.listExpanded = false;
        this.loading = true;
        this.gameVersionDropdownOpen = false;
        this.selectedGameVersions = preferredVisibleGameVersions();
        rebuildOverlay();
    }

    void refresh(ProjectDetail project, GameVersionCatalog catalog) {
        if (overlay == null || project == null || !sameProject(this.project, project)) {
            return;
        }
        this.project = project;
        this.catalog = catalog == null ? GameVersionCatalog.fromVersions(List.of()) : catalog;
        this.loading = false;
        if (!selectedGameVersions.isEmpty() && !gameVersions().containsAll(selectedGameVersions)) {
            List<String> validSelections = selectedGameVersions.stream()
                    .filter(gameVersions()::contains)
                    .toList();
            selectedGameVersions = validSelections.isEmpty() ? preferredVisibleGameVersions() : validSelections;
            listExpanded = false;
        }
        rebuildOverlay();
    }

    boolean isShowing(ProjectDetail project) {
        return overlay != null && project != null && sameProject(this.project, project);
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
    }

    private void rebuildOverlay() {
        StackPane hostPane = host.get();
        if (hostPane == null || project == null) {
            return;
        }
        if (overlay == null) {
            overlay = overlayShell();
            hostPane.getChildren().add(overlay);
        }
        overlay.getChildren().setAll(modal(hostPane));
        Platform.runLater(overlay::requestFocus);
    }

    private StackPane overlayShell() {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("download-modal-overlay");
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

    private VBox modal(StackPane hostPane) {
        VBox modal = new VBox(0);
        modal.getStyleClass().add("download-modal");
        modal.setMaxWidth(MODAL_WIDTH);
        modal.setPrefWidth(MODAL_WIDTH);
        modal.setMaxHeight(Region.USE_PREF_SIZE);
        modal.setOnMouseClicked(event -> event.consume());

        ScrollPane bodyScroll = new ScrollPane(body());
        bodyScroll.getStyleClass().add("download-modal-scroll");
        bodyScroll.setFitToWidth(true);
        bodyScroll.setFitToHeight(false);
        bodyScroll.setMaxHeight(Region.USE_PREF_SIZE);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(bodyScroll, Priority.NEVER);

        modal.getChildren().addAll(header(), bodyScroll, footer());
        return modal;
    }

    private HBox header() {
        HBox header = new HBox(16);
        header.getStyleClass().add("download-modal-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox copy = new VBox(4);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox title = new HBox(9);
        title.getStyleClass().add("download-modal-title-row");
        title.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label("Download");
        label.getStyleClass().add("download-modal-title");
        title.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 20), label);
        copy.getChildren().add(title);

        if (showPreReleaseToggle()) {
            copy.getChildren().add(toggleRow(
                    "Show Pre-Release Game Versions",
                    effectiveShowPreReleaseGameVersions(),
                    () -> {
                        showPreReleaseGameVersions = !showPreReleaseGameVersions;
                        selectedGameVersions = preferredVisibleGameVersions();
                        listExpanded = false;
                        gameVersionDropdownOpen = false;
                        rebuildOverlay();
                    }
            ));
        }
        if (showAlphaBetaToggle()) {
            copy.getChildren().add(toggleRow(
                    "Show Beta/Alpha",
                    effectiveShowExperimental(),
                    () -> {
                        showExperimental = !showExperimental;
                        selectedGameVersions = preferredVisibleGameVersions();
                        listExpanded = false;
                        gameVersionDropdownOpen = false;
                        rebuildOverlay();
                    }
            ));
        }

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 18));
        close.getStyleClass().add("download-modal-close");
        close.setOnAction(event -> hide());
        header.getChildren().addAll(copy, close);
        return header;
    }

    private HBox toggleRow(String text, boolean selected, Runnable action) {
        HBox row = new HBox(8);
        row.getStyleClass().add("download-modal-toggle-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOnMouseClicked(event -> action.run());
        StackPane switchTrack = new StackPane();
        switchTrack.getStyleClass().add("download-modal-toggle");
        switchTrack.pseudoClassStateChanged(SELECTED, selected);
        Region knob = new Region();
        knob.getStyleClass().add("download-modal-toggle-knob");
        switchTrack.getChildren().add(knob);
        StackPane.setAlignment(knob, selected ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Label label = new Label(text.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("download-modal-toggle-label");
        row.getChildren().addAll(switchTrack, label);
        return row;
    }

    private VBox body() {
        VBox body = new VBox(0);
        body.getStyleClass().add("download-modal-body");

        if (loading && project.versions().isEmpty()) {
            body.getChildren().add(loadingState());
            return body;
        }

        VBox versionBlock = new VBox(8);
        versionBlock.getStyleClass().add("download-modal-version-block");
        Label versionLabel = new Label("GAME VERSION");
        versionLabel.getStyleClass().add("download-modal-field-label");
        GameVersionDropdown versions = GameVersionDropdown.multiSelect();
        versions.getStyleClass().add("download-game-version-dropdown");
        versions.setAnyLabel("Any");
        versions.setEmptyText("No compatible game versions");
        versions.setMaxListHeight(224);
        versions.setVersions(gameVersions());
        versions.setSelectedVersions(selectedGameVersions);
        versions.setOnOpenChange(open -> gameVersionDropdownOpen = open);
        versions.setOnSelectionChange(next -> {
            selectedGameVersions = List.copyOf(next);
            listExpanded = false;
            rebuildOverlay();
        });
        versions.setOpen(gameVersionDropdownOpen);
        versionBlock.getChildren().addAll(versionLabel, versions);
        body.getChildren().add(versionBlock);

        List<VersionEntry> sortedVersions = sortedVisibleVersions();
        VersionEntry latest = sortedVersions.isEmpty() ? null : sortedVersions.getFirst();
        if (latest == null) {
            body.getChildren().add(emptyState());
            return body;
        }

        Button latestButton = latestButton(latest);
        Node latestExternalNotice = externalDependencyNotice(latest.version());
        VBox.setMargin(latestButton, new Insets(0, 0, latestExternalNotice == null ? 24 : 12, 0));
        body.getChildren().add(latestButton);
        if (latestExternalNotice != null) {
            VBox.setMargin(latestExternalNotice, new Insets(0, 0, 24, 0));
            body.getChildren().add(latestExternalNotice);
        }

        body.getChildren().add(otherVersionsDivider());
        body.getChildren().add(expandVersionsButton());
        if (listExpanded) {
            VBox list = new VBox(8);
            list.getStyleClass().add("download-modal-version-list");
            for (VersionEntry entry : sortedVersions) {
                list.getChildren().add(versionRow(entry));
            }
            body.getChildren().add(list);
        }
        return body;
    }

    private Node loadingState() {
        VBox state = new VBox(12);
        state.getStyleClass().add("download-modal-empty");
        state.setAlignment(Pos.CENTER);
        state.getChildren().addAll(
                NativeSpinner.inline("Loading download options", 20),
                new Label("Fetching files and compatible game versions.")
        );
        return state;
    }

    private Button latestButton(VersionEntry entry) {
        Button button = new Button();
        button.getStyleClass().addAll("download-modal-latest", channelStyle(entry.version().channel()));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setGraphic(latestButtonContent(entry));
        button.setOnAction(event -> install(entry));
        return button;
    }

    private VBox latestButtonContent(VersionEntry entry) {
        ProjectVersion version = entry.version();
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.setFillWidth(false);
        HBox headline = new HBox(8);
        headline.setAlignment(Pos.CENTER);
        Label text = new Label("Download Latest");
        text.getStyleClass().add("download-modal-latest-title");
        headline.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 24), text);
        HBox badge = versionBadge(version);
        badge.setMaxWidth(Region.USE_PREF_SIZE);
        box.getChildren().addAll(headline, badge);
        if (shouldShowEntryGameVersion()) {
            Label forVersion = new Label("For " + entry.gameVersion());
            forVersion.getStyleClass().add("download-modal-file-date");
            box.getChildren().add(forVersion);
        }
        List<String> others = otherCompatibleVersions(version, entry.gameVersion());
        if (!others.isEmpty()) {
            Label supports = new Label("Also supports: " + String.join(", ", others));
            supports.getStyleClass().add("download-modal-also-supports");
            box.getChildren().add(supports);
        }
        return box;
    }

    private HBox versionBadge(ProjectVersion version) {
        HBox badge = new HBox(8);
        badge.getStyleClass().addAll("download-modal-version-badge", channelStyle(version.channel()));
        badge.setAlignment(Pos.CENTER);
        Label number = new Label("v" + value(version.versionNumber(), "unknown"));
        number.getStyleClass().add("download-modal-version-number");
        badge.getChildren().add(number);
        if (!isRelease(version.channel())) {
            Label channel = new Label(value(version.channel(), "RELEASE").toUpperCase(Locale.ROOT));
            channel.getStyleClass().add("download-modal-version-channel");
            badge.getChildren().add(channel);
        }
        return badge;
    }

    private Node otherVersionsDivider() {
        StackPane divider = new StackPane();
        divider.getStyleClass().add("download-modal-divider-wrap");
        Region line = new Region();
        line.getStyleClass().add("download-modal-divider-line");
        Label label = new Label("OTHER VERSIONS");
        label.getStyleClass().add("download-modal-divider-label");
        divider.getChildren().addAll(line, label);
        return divider;
    }

    private Button expandVersionsButton() {
        Button button = new Button();
        button.getStyleClass().add("download-modal-expand");
        button.setMaxWidth(Double.MAX_VALUE);

        HBox content = new HBox(8);
        content.getStyleClass().add("download-modal-expand-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMinWidth(0);
        content.prefWidthProperty().bind(button.widthProperty().subtract(24));
        Label text = new Label("View all files for " + selectedGameVersionLabel());
        text.getStyleClass().add("download-modal-expand-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Node icon = LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 16);
        icon.getStyleClass().add("download-modal-expand-icon");
        icon.setRotate(listExpanded ? 180 : 0);
        content.getChildren().addAll(text, spacer, icon);

        button.setGraphic(content);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setOnAction(event -> {
            listExpanded = !listExpanded;
            rebuildOverlay();
        });
        return button;
    }

    private Node versionRow(VersionEntry entry) {
        ProjectVersion version = entry.version();
        VBox row = new VBox(10);
        row.getStyleClass().add("download-modal-file-row");
        HBox main = new HBox(14);
        main.getStyleClass().add("download-modal-file-row-main");
        main.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.FILE_CODE, 20));
        icon.getStyleClass().add("download-modal-file-icon");
        VBox copy = new VBox(3);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox title = new HBox(8);
        title.setAlignment(Pos.CENTER_LEFT);
        Label versionText = new Label("v" + value(version.versionNumber(), "unknown"));
        versionText.getStyleClass().add("download-modal-file-version");
        title.getChildren().add(versionText);
        if (!isRelease(version.channel())) {
            title.getChildren().add(channelBadge(version.channel()));
        }
        Label date = new Label(timeAgo(version.releaseDate()));
        date.getStyleClass().add("download-modal-file-date");
        if (shouldShowEntryGameVersion()) {
            date.setText(date.getText() + " · " + entry.gameVersion());
        }
        copy.getChildren().addAll(title, date);
        List<String> others = otherCompatibleVersions(version, entry.gameVersion());
        if (!others.isEmpty()) {
            Label supports = new Label("Also supports: " + String.join(", ", others));
            supports.getStyleClass().add("download-modal-file-supports");
            copy.getChildren().add(supports);
        }
        if (!externalDependencies(version).isEmpty()) {
            HBox external = new HBox(4, LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 12), new Label("EXTERNAL MODS"));
            external.getStyleClass().add("download-modal-file-external-badge");
            external.setAlignment(Pos.CENTER_LEFT);
            copy.getChildren().add(external);
        }

        Button download = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 16));
        download.getStyleClass().add("download-modal-file-download");
        download.setOnAction(event -> install(entry));

        main.getChildren().addAll(icon, copy, download);
        row.getChildren().add(main);
        return row;
    }

    private Label channelBadge(String channel) {
        Label badge = new Label(value(channel, "RELEASE").toUpperCase(Locale.ROOT));
        badge.getStyleClass().addAll("download-modal-channel-badge", channelStyle(channel));
        return badge;
    }

    private Node externalDependencyNotice(ProjectVersion version) {
        if (!ProjectClassification.isModpack(project.classification())) {
            return null;
        }
        List<ProjectDependency> external = externalDependencies(version);
        if (external.isEmpty()) {
            return null;
        }
        VBox notice = new VBox(4);
        notice.getStyleClass().add("download-modal-external-dependency-notice");
        HBox title = new HBox(7, LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 14), new Label("This modpack uses external mods"));
        title.getStyleClass().add("download-modal-external-dependency-title");
        title.setAlignment(Pos.CENTER_LEFT);
        Label copy = new Label("%s %s from outside Modtale. Check the linked source pages if the download or install flow asks for them separately.".formatted(
                externalDependencyNames(external),
                external.size() == 1 ? "comes" : "come"
        ));
        copy.getStyleClass().add("download-modal-external-dependency-copy");
        copy.setWrapText(true);
        notice.getChildren().addAll(title, copy);
        return notice;
    }

    private String externalDependencyNames(List<ProjectDependency> dependencies) {
        List<String> names = dependencies.stream()
                .map(this::dependencyTitle)
                .filter(name -> !isBlank(name))
                .limit(3)
                .toList();
        int remaining = dependencies.size() - names.size();
        return String.join(", ", names) + (remaining > 0 ? ", +" + remaining + " more" : "");
    }

    private List<ProjectDependency> externalDependencies(ProjectVersion version) {
        if (version == null || version.dependencies() == null) {
            return List.of();
        }
        return version.dependencies().stream()
                .filter(dependency -> dependency != null && dependency.isExternal() && !dependency.isEmbedded())
                .toList();
    }

    private String dependencyTitle(ProjectDependency dependency) {
        return firstNonBlank(
                dependency.title(),
                dependency.projectTitle(),
                dependency.projectId(),
                dependency.externalId(),
                dependency.id(),
                "Dependency"
        );
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

    private static boolean sameProject(ProjectDetail left, ProjectDetail right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        String leftId = value(left.id(), "");
        String rightId = value(right.id(), "");
        if (!leftId.isBlank() && !rightId.isBlank()) {
            return leftId.equals(rightId);
        }
        return value(left.routeKey(), "").equals(value(right.routeKey(), ""));
    }

    private Node emptyState() {
        VBox empty = new VBox(10);
        empty.getStyleClass().add("download-modal-empty");
        empty.setAlignment(Pos.CENTER);
        empty.getChildren().addAll(
                LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 30),
                new Label("No compatible versions.")
        );
        if (!effectiveShowExperimental() && !currentVersions().isEmpty()) {
            Button show = new Button("Show experimental");
            show.getStyleClass().add("download-modal-show-experimental");
            show.setOnAction(event -> {
                showExperimental = true;
                rebuildOverlay();
            });
            empty.getChildren().add(show);
        }
        return empty;
    }

    private HBox footer() {
        HBox footer = new HBox();
        footer.getStyleClass().add("download-modal-footer");
        footer.setAlignment(Pos.CENTER);
        Button history = new Button("VIEW FULL CHANGELOG", LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_RIGHT, 13));
        history.getStyleClass().add("download-modal-history");
        history.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        history.setOnAction(event -> {
            ProjectDetail selectedProject = project;
            hide();
            viewHistory.accept(selectedProject);
        });
        footer.getChildren().add(history);
        return footer;
    }

    private void install(VersionEntry entry) {
        ProjectDetail selectedProject = project;
        ProjectVersion version = entry.version();
        String gameVersion = entry.gameVersion();
        hide();
        install.accept(new DownloadSelection(selectedProject, version, gameVersion, null));
    }

    private List<String> gameVersions() {
        Map<String, List<ProjectVersion>> byGame = versionsByGame();
        Set<String> preRelease = preReleaseGameVersionSet();
        boolean effectivePreRelease = effectiveShowPreReleaseGameVersions();
        return orderedGameVersions().stream()
                .filter(byGame::containsKey)
                .filter(version -> effectivePreRelease || !preRelease.contains(version))
                .toList();
    }

    private List<String> activeSelectedGameVersions() {
        return selectedGameVersions.isEmpty() ? gameVersions() : selectedGameVersions;
    }

    private List<VersionEntry> selectedVersionEntries() {
        Map<String, VersionEntry> entries = new LinkedHashMap<>();
        Map<String, List<ProjectVersion>> byGame = versionsByGame();
        for (String gameVersion : activeSelectedGameVersions()) {
            for (ProjectVersion version : byGame.getOrDefault(gameVersion, List.of())) {
                entries.putIfAbsent(versionKey(version), new VersionEntry(version, gameVersion));
            }
        }
        return List.copyOf(entries.values());
    }

    private List<ProjectVersion> currentVersions() {
        return selectedVersionEntries().stream()
                .map(VersionEntry::version)
                .toList();
    }

    private List<VersionEntry> sortedVisibleVersions() {
        boolean effectiveExperimental = effectiveShowExperimental();
        return selectedVersionEntries().stream()
                .filter(entry -> effectiveExperimental || isRelease(entry.version().channel()))
                .sorted(versionEntryComparator())
                .toList();
    }

    private List<String> preferredVisibleGameVersions() {
        List<String> versions = gameVersions();
        String preferred = preferredGameVersion.get();
        if (preferred != null && versions.contains(preferred)) {
            return List.of(preferred);
        }
        if (!selectedGameVersions.isEmpty()) {
            List<String> validSelections = selectedGameVersions.stream()
                    .filter(versions::contains)
                    .toList();
            if (!validSelections.isEmpty()) {
                return validSelections;
            }
        }
        List<GameVersionGroups.Group> groups = GameVersionGroups.build(versions);
        return groups.isEmpty() ? List.of() : groups.getFirst().versions();
    }

    private Map<String, List<ProjectVersion>> versionsByGame() {
        Map<String, List<ProjectVersion>> grouped = new LinkedHashMap<>();
        for (String gameVersion : orderedGameVersions()) {
            grouped.put(gameVersion, new ArrayList<>());
        }
        for (ProjectVersion version : project.versions()) {
            List<String> gameVersions = version.gameVersions().isEmpty()
                    ? List.of(value(preferredGameVersion.get(), ""))
                    : version.gameVersions();
            for (String gameVersion : gameVersions) {
                if (gameVersion == null || gameVersion.isBlank()) {
                    continue;
                }
                grouped.computeIfAbsent(gameVersion, ignored -> new ArrayList<>()).add(version);
            }
        }
        grouped.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return grouped;
    }

    private List<String> orderedGameVersions() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (catalog != null) {
            ordered.addAll(catalog.allVersions());
            if (ordered.isEmpty()) {
                ordered.addAll(catalog.releaseVersions());
                ordered.addAll(catalog.preReleaseVersions());
            }
        }
        project.versions().stream()
                .flatMap(version -> version.gameVersions().stream())
                .filter(version -> version != null && !version.isBlank())
                .sorted((left, right) -> compareSemver(right, left))
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private Set<String> preReleaseGameVersionSet() {
        return catalog == null ? Set.of() : Set.copyOf(catalog.preReleaseVersions());
    }

    private boolean effectiveShowPreReleaseGameVersions() {
        return showPreReleaseGameVersions || forceShowPreReleaseGameVersions();
    }

    private boolean forceShowPreReleaseGameVersions() {
        Map<String, List<ProjectVersion>> byGame = versionsByGame();
        Set<String> preRelease = preReleaseGameVersionSet();
        boolean hasPreRelease = byGame.keySet().stream().anyMatch(preRelease::contains);
        boolean hasRelease = byGame.keySet().stream().anyMatch(version -> !preRelease.contains(version));
        return hasPreRelease && !hasRelease;
    }

    private boolean showPreReleaseToggle() {
        Map<String, List<ProjectVersion>> byGame = versionsByGame();
        Set<String> preRelease = preReleaseGameVersionSet();
        boolean hasPreRelease = byGame.keySet().stream().anyMatch(preRelease::contains);
        boolean hasRelease = byGame.keySet().stream().anyMatch(version -> !preRelease.contains(version));
        return hasPreRelease && hasRelease;
    }

    private boolean effectiveShowExperimental() {
        List<ProjectVersion> versions = currentVersions();
        boolean hasRelease = versions.stream().anyMatch(version -> isRelease(version.channel()));
        boolean hasExperimental = versions.stream().anyMatch(version -> !isRelease(version.channel()));
        return showExperimental || (hasExperimental && !hasRelease);
    }

    private boolean showAlphaBetaToggle() {
        boolean hasExperimental = project.versions().stream().anyMatch(version -> !isRelease(version.channel()));
        boolean hasRelease = project.versions().stream().anyMatch(version -> isRelease(version.channel()));
        return hasExperimental && hasRelease;
    }

    private boolean shouldShowEntryGameVersion() {
        return activeSelectedGameVersions().size() > 1;
    }

    private String selectedGameVersionLabel() {
        return GameVersionGroups.displayLabel(selectedGameVersions, gameVersions(), "any version");
    }

    private List<String> otherCompatibleVersions(ProjectVersion version, String selectedGameVersion) {
        return version.gameVersions().stream()
                .filter(gameVersion -> !gameVersion.equals(selectedGameVersion))
                .toList();
    }

    private static boolean isRelease(String channel) {
        return channel == null || channel.isBlank() || "RELEASE".equalsIgnoreCase(channel);
    }

    private static String channelStyle(String channel) {
        if ("ALPHA".equalsIgnoreCase(channel)) {
            return "alpha";
        }
        if ("BETA".equalsIgnoreCase(channel)) {
            return "beta";
        }
        return "release";
    }

    private static Comparator<ProjectVersion> versionComparator() {
        return (left, right) -> {
            int date = compareReleaseDate(right.releaseDate(), left.releaseDate());
            if (date != 0) {
                return date;
            }
            return compareSemver(right.versionNumber(), left.versionNumber());
        };
    }

    private static Comparator<VersionEntry> versionEntryComparator() {
        return (left, right) -> versionComparator().compare(left.version(), right.version());
    }

    private static String versionKey(ProjectVersion version) {
        return firstNonBlank(
                version.id(),
                value(version.versionNumber(), "unknown") + "-" + value(version.fileUrl(), "") + "-" + value(version.releaseDate(), "")
        );
    }

    private static int compareReleaseDate(String left, String right) {
        Instant leftInstant = parseInstant(left);
        Instant rightInstant = parseInstant(right);
        if (leftInstant == null && rightInstant == null) {
            return 0;
        }
        if (leftInstant == null) {
            return -1;
        }
        if (rightInstant == null) {
            return 1;
        }
        return leftInstant.compareTo(rightInstant);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static int compareSemver(String left, String right) {
        List<Integer> leftParts = semverParts(left);
        List<Integer> rightParts = semverParts(right);
        int length = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < length; i++) {
            int leftPart = i < leftParts.size() ? leftParts.get(i) : 0;
            int rightPart = i < rightParts.size() ? rightParts.get(i) : 0;
            int comparison = Integer.compare(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return value(left, "").compareToIgnoreCase(value(right, ""));
    }

    private static List<Integer> semverParts(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Integer> parts = new ArrayList<>();
        for (String part : value.split("[^0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                parts.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts;
    }

    record DownloadSelection(
            ProjectDetail project,
            ProjectVersion version,
            String gameVersion,
            List<ProjectDependency> selectedDependencies
    ) {
    }

    private record VersionEntry(ProjectVersion version, String gameVersion) {
    }
}
