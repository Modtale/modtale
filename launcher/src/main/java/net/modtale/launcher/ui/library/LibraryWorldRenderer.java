package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.emptyState;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.styleCombo;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.input.MouseButton;
import net.modtale.launcher.ui.browse.card.ProjectCardInteraction;
import net.modtale.launcher.model.project.ProjectSummary;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.ui.common.LauncherSkeleton;
import net.modtale.launcher.ui.common.LauncherSkeletonContent;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.UpdateCandidate;
import net.modtale.launcher.model.project.ProjectClassification;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectMeta;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class LibraryWorldRenderer {

    private static final double PROJECT_ICON_SIZE = 80;
    private static final double CONTENT_ICON_SIZE = 34;
    private static final PseudoClass CONTENTS_HOVERED = PseudoClass.getPseudoClass("contents-hovered");

    private List<String> knownGameVersions = List.of();
    private String librarySearch = "";
    void setKnownGameVersions(List<String> versions) { knownGameVersions = List.copyOf(versions); }

    private Consumer<ProjectSummary> openProject = ignored -> {};
    private Consumer<ProjectSummary> openCreator = ignored -> {};

    void setNavigationActions(Consumer<ProjectSummary> openProject, Consumer<ProjectSummary> openCreator) {
        this.openProject = openProject;
        this.openCreator = openCreator;
    }

    private final CachedImageLoader imageLoader;
    private final Consumer<UpdateCandidate> updateProject;
    private final Consumer<InstalledProject> loadVersions;
    private final LibraryProjectRenderer.VersionSwitchHandler switchVersion;
    private final Consumer<InstalledProject> uninstallProject;
    private final LibraryProjectRenderer.UnlockHandler unlockProject;
    private final Consumer<InstalledProject> toggleModpackContents;
    private final LibraryProjectRenderer.WorldToggleHandler toggleWorldMods;
    private final Consumer<HytaleWorld> shareWorldSnapshot;
    private final Consumer<HytaleWorld> createModpackFromWorld;
    private final BiConsumer<String, List<ConfigFile>> editConfigs;
    private final Runnable refreshLibrary;
    private final Runnable checkUpdates;

    LibraryWorldRenderer(
            CachedImageLoader imageLoader,
            Consumer<UpdateCandidate> updateProject,
            Consumer<InstalledProject> loadVersions,
            LibraryProjectRenderer.VersionSwitchHandler switchVersion,
            Consumer<InstalledProject> uninstallProject,
            LibraryProjectRenderer.UnlockHandler unlockProject,
            Consumer<InstalledProject> toggleModpackContents,
            LibraryProjectRenderer.WorldToggleHandler toggleWorldMods,
            Consumer<HytaleWorld> shareWorldSnapshot,
            Consumer<HytaleWorld> createModpackFromWorld,
            BiConsumer<String, List<ConfigFile>> editConfigs,
            Runnable refreshLibrary,
            Runnable checkUpdates
    ) {
        this.imageLoader = imageLoader;
        this.updateProject = updateProject;
        this.loadVersions = loadVersions;
        this.switchVersion = switchVersion;
        this.uninstallProject = uninstallProject;
        this.unlockProject = unlockProject;
        this.toggleModpackContents = toggleModpackContents;
        this.toggleWorldMods = toggleWorldMods;
        this.shareWorldSnapshot = shareWorldSnapshot;
        this.createModpackFromWorld = createModpackFromWorld;
        this.editConfigs = editConfigs;
        this.refreshLibrary = refreshLibrary;
        this.checkUpdates = checkUpdates;
    }

    List<Node> worldDetail(LibraryWorldModel model) {
        return worldDetail(model, List.of());
    }

    List<Node> worldDetail(LibraryWorldModel model, List<ConfigFile> configs) {
        if (model == null || model.world() == null) {
            return List.of(emptyState("No world selected", "Create a Hytale world, then refresh the launcher."));
        }
        List<Node> sections = new ArrayList<>();
        sections.add(worldHeader(model));
        sections.add(installedProjectsSection(model, configs));
        return sections;
    }

    private Node worldHeader(LibraryWorldModel model) {
        VBox section = new VBox(12);
        section.getStyleClass().addAll("library-detail-hero", "library-world-detail-hero");

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("library-detail-heading");
        StackPane icon = imageIcon(
                model.world().previewImage(),
                model.world().name(),
                LauncherIcons.Glyph.GLOBE,
                88,
                "library-detail-icon",
                false
        );
        icon.getStyleClass().add("library-world-detail-icon");

        VBox copy = new VBox();
        copy.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(model.world().name());
        title.getStyleClass().addAll("library-detail-title", "library-world-detail-title");
        title.setTooltip(new Tooltip(model.world().name()));
        title.setMaxWidth(Double.MAX_VALUE);
        copy.setMinWidth(0);
        Label summary = new Label(model.enabledProjectCount() + " of " + model.totalProjectCount() + " projects enabled");
        summary.getStyleClass().addAll("library-project-meta", "library-world-detail-summary");
        copy.setSpacing(6);
        copy.getChildren().addAll(title, summary);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox actions = new HBox(8);
        actions.getStyleClass().add("library-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button refresh = secondaryButton("Refresh");
        refresh.getStyleClass().addAll("small", "library-compact-icon-action");
        refresh.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.REFRESH_CW, 14));
        refresh.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        refresh.setAccessibleText("Refresh library");
        refresh.setTooltip(new Tooltip("Rescan installed projects and worlds"));
        refresh.setOnAction(event -> refreshLibrary.run());
        Button updates = secondaryButton("Check for updates");
        updates.getStyleClass().addAll("small", "library-compact-icon-action");
        updates.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 14));
        updates.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        updates.setAccessibleText("Check for updates");
        updates.setTooltip(new Tooltip("Check for updates"));
        updates.setOnAction(event -> checkUpdates.run());
        Button share = secondaryButton("Share");
        share.getStyleClass().addAll("small", "library-compact-icon-action");
        share.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.SHARE_2, 14));
        share.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        share.setAccessibleText("Share world mod list");
        share.setTooltip(new Tooltip("Copy a share link for this world's enabled mods"));
        share.setOnAction(event -> shareWorldSnapshot.accept(model.world()));
        Button pack = secondaryButton("Create pack");
        pack.getStyleClass().addAll("small", "library-action-emphasis");
        pack.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.PACKAGE_PLUS, 14));
        pack.setMinWidth(Region.USE_PREF_SIZE);
        pack.setTooltip(new Tooltip("Start a Modtale modpack from this world's enabled mods"));
        pack.setOnAction(event -> createModpackFromWorld.accept(model.world()));
        HBox tools = new HBox(2, refresh, updates, share);
        tools.setAlignment(Pos.CENTER);
        tools.getStyleClass().add("library-world-tools");
        tools.setMinHeight(40);
        tools.setPrefHeight(40);
        tools.setMaxHeight(40);
        pack.setMinHeight(40);
        pack.setPrefHeight(40);
        pack.setMaxHeight(40);
        actions.getChildren().addAll(tools, pack);
        actions.setMinWidth(Region.USE_PREF_SIZE);

        row.getChildren().addAll(icon, copy);
        actions.setAlignment(Pos.CENTER_LEFT);
        section.getChildren().addAll(row, actions);
        section.widthProperty().addListener((observable, previous, width) -> {
            boolean inline = width.doubleValue() >= 720;
            if (inline && !row.getChildren().contains(actions)) {
                section.getChildren().remove(actions);
                row.getChildren().add(actions);
            } else if (!inline && row.getChildren().contains(actions)) {
                row.getChildren().remove(actions);
                section.getChildren().add(actions);
            }
        });
        return section;
    }

    private Node installedProjectsSection(LibraryWorldModel model, List<ConfigFile> configs) {
        TextField search = new TextField(librarySearch);
        search.setPromptText("Search installed mods...");
        search.setAccessibleText("Search installed mods");
        search.getStyleClass().addAll("input", "quick-search");
        search.setMaxWidth(Double.MAX_VALUE);
        StackPane searchShell = new StackPane(search);
        searchShell.getStyleClass().add("search-shell");
        Node searchIcon = LauncherIcons.icon(LauncherIcons.Glyph.SEARCH, 16);
        searchIcon.getStyleClass().add("search-icon");
        searchIcon.setMouseTransparent(true);
        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 13));
        Button clear = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 12));
        clear.getStyleClass().add("search-clear-button");
        clear.setAccessibleText("Clear library search");
        clear.setTooltip(new Tooltip("Clear search"));
        clear.visibleProperty().bind(search.textProperty().isNotEmpty());
        clear.managedProperty().bind(clear.visibleProperty());
        clear.setOnAction(event -> { search.clear(); search.requestFocus(); });
        StackPane.setAlignment(clear, Pos.CENTER_RIGHT);
        StackPane.setMargin(clear, new Insets(0, 9, 0, 0));
        searchShell.getChildren().addAll(searchIcon, clear);
        VBox results = new VBox(18);
        Runnable render = () -> {
            results.getChildren().clear();
            if (model.projects().isEmpty()) {
                results.getChildren().add(emptyState("No installed projects", "Install mods from Browse to manage them per world."));
                return;
            }
            List<LibraryWorldProjectModel> visible = LibraryProjectFilter.matching(model.projects(), librarySearch);
            for (boolean enabled : new boolean[] {true, false}) {
                List<LibraryWorldProjectModel> group = visible.stream()
                        .filter(project -> (project.enabledCount() > 0) == enabled).toList();
                Label subtitle = new Label(enabled ? "Enabled" : "Disabled");
                subtitle.getStyleClass().addAll("library-section-title", "library-group-title");
                VBox section = new VBox(10, subtitle);
                if (group.isEmpty()) {
                    Label empty = new Label(librarySearch.isBlank()
                            ? (enabled ? "No enabled mods" : "No disabled mods") : "No matching mods");
                    empty.getStyleClass().add("library-project-meta");
                    section.getChildren().add(empty);
                } else {
                    section.getChildren().add(installedProjectsSection(model.world(), group, "", "", configs));
                }
                results.getChildren().add(section);
            }
        };
        search.textProperty().addListener((observable, before, after) -> {
            librarySearch = after;
            render.run();
        });
        render.run();
        return new VBox(18, searchShell, results);
    }

    private Node installedProjectsSection(
            HytaleWorld world,
            List<LibraryWorldProjectModel> projects,
            String emptyTitle,
            String emptySubtitle, List<ConfigFile> configs
    ) {
        if (projects.isEmpty()) {
            return emptyState(emptyTitle, emptySubtitle);
        }
        VBox rows = new VBox(10);
        rows.getStyleClass().add("library-world-project-list");
        for (LibraryWorldProjectModel project : projects) {
            rows.getChildren().add(projectRow(world, project, configs));
        }
        return rows;
    }

    private Node projectRow(HytaleWorld world, LibraryWorldProjectModel model, List<ConfigFile> configs) {
        VBox shell = new VBox(10);
        shell.getStyleClass().add("library-world-project-row");
        // Keep the expensive shadow separate from the independently moving icon.
        Region surface = new Region();
        surface.getStyleClass().add("library-world-project-surface");
        surface.setManaged(false);
        surface.setMouseTransparent(true);
        surface.prefWidthProperty().bind(shell.widthProperty());
        surface.prefHeightProperty().bind(shell.heightProperty());
        surface.setCache(true);
        surface.setCacheHint(javafx.scene.CacheHint.SPEED);
        shell.widthProperty().addListener((o, before, after) -> surface.resize(shell.getWidth(), shell.getHeight()));
        shell.heightProperty().addListener((o, before, after) -> surface.resize(shell.getWidth(), shell.getHeight()));
        shell.getChildren().add(surface);

        StackPane icon = projectIcon(model, PROJECT_ICON_SIZE);
        VBox copy = projectCopy(model);
        HBox row = new HBox(12);
        row.getStyleClass().add("library-world-project-main");
        row.setAlignment(Pos.CENTER_LEFT);

        boolean hasWorld = world != null;
        LibraryToggleBox toggle = new LibraryToggleBox();
        toggle.setSelected(hasWorld && model.selected());
        toggle.setIndeterminate(hasWorld && model.indeterminate());
        toggle.setDisable(!hasWorld || !model.toggleable());
        toggle.setTooltip(new Tooltip(!hasWorld
                ? "Select or create a world to enable this install"
                : model.toggleable()
                ? "Enable or disable this installed project for " + world.name()
                : "No Hytale manifest id was found for this install record"));
        if (hasWorld) {
            toggle.setOnAction(() -> toggleWorldMods.setEnabled(world, model.modIds(), toggle.isSelected()));
        }

        var iconSize = new javafx.beans.property.SimpleDoubleProperty(PROJECT_ICON_SIZE);
        icon.minWidthProperty().bind(iconSize);
        icon.prefWidthProperty().bind(iconSize);
        icon.maxWidthProperty().bind(iconSize);
        icon.minHeightProperty().bind(iconSize);
        icon.prefHeightProperty().bind(iconSize);
        icon.maxHeightProperty().bind(iconSize);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox actions = projectActions(model, configs);
        row.getChildren().addAll(toggle, icon, copy, actions);
        shell.getChildren().add(row);

        Node versionControls = versionControls(model);
        if (versionControls != null) {
            shell.getChildren().add(versionControls);
        }

        Node contentsCard = contentsCard(model, configs);
        if (contentsCard != null) {
            contentsCard.hoverProperty().addListener((observable, previous, hovered) ->
                    shell.pseudoClassStateChanged(CONTENTS_HOVERED, hovered));
            shell.getChildren().add(contentsCard);
        }
        ProjectCardInteraction.addHoverAnimationWithIndependentContent(shell, icon);
        ProjectSummary target = navigationTarget(model);
        if (target != null) {
            shell.setCursor(Cursor.HAND);
            shell.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.isStillSincePress()
                        && !isNestedControl(event.getTarget(), shell)) {
                    openProject.accept(target);
                    event.consume();
                }
            });
        }
        return shell;
    }

    static boolean isNestedControl(Object target, Node card) {
        Node node = target instanceof Node picked ? picked : null;
        while (node != null && node != card) {
            if (node instanceof ButtonBase || node instanceof ComboBoxBase<?> || node instanceof LibraryToggleBox
                    || node.getStyleClass().contains("author-link")
                    || node.getStyleClass().contains("library-world-version-row")
                    || node.getStyleClass().contains("library-world-content-card")) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    static ProjectSummary navigationTarget(LibraryWorldProjectModel model) {
        InstalledProject installed = model.installed();
        boolean curseForge = InstalledProject.SOURCE_CURSEFORGE.equalsIgnoreCase(installed.source())
                || installed.projectId().startsWith("curseforge:");
        if (installed.projectId().isBlank() || installed.projectId().startsWith("local:")
                || InstalledProject.SOURCE_LOCAL.equalsIgnoreCase(installed.source())
                || (!curseForge && !LibraryProjectSupport.isModtaleProject(installed))) {
            return null;
        }
        String id = installed.projectId();
        if (curseForge && !id.startsWith("curseforge:")) id = "curseforge:" + id;
        ProjectDetail detail = model.detail();
        ProjectMeta meta = model.meta();
        return new ProjectSummary(id,
                curseForge ? id : first(detail == null ? "" : detail.slug(), meta == null ? "" : meta.slug(), installed.slug()),
                model.display().title(), meta == null ? "" : meta.description(),
                detail == null ? "" : detail.authorId(),
                first(detail == null ? "" : detail.author(), meta == null ? "" : meta.author(), model.display().author()),
                model.display().icon(), null, model.display().classification(), 0, 0, null, List.of(),
                curseForge ? InstalledProject.SOURCE_CURSEFORGE : InstalledProject.SOURCE_MODTALE, null, null);
    }

    private VBox projectCopy(LibraryWorldProjectModel model) {
        InstalledProject installed = model.installed();
        LibraryWorldProjectDisplay display = model.display();
        VBox copy = new VBox(5);
        copy.getStyleClass().add("library-world-project-copy");
        copy.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(display.title());
        title.getStyleClass().add("library-world-project-title");

        String subtitleText = projectMetaLine(model);
        HBox subtitle = new HBox(4);
        subtitle.setAlignment(Pos.CENTER_LEFT);
        Label by = new Label("by");
        by.getStyleClass().add("library-world-project-meta");
        Label author = new Label(display.author());
        author.getStyleClass().add("library-world-project-meta");
        ProjectSummary creatorTarget = navigationTarget(model);
        if (creatorTarget != null && !display.author().isBlank()) {
            author.getStyleClass().add("author-link");
            author.setCursor(Cursor.HAND);
            author.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.isStillSincePress()) {
                    openCreator.accept(creatorTarget);
                    event.consume();
                }
            });
        }
        subtitle.getChildren().addAll(by, author);

        HBox badges = new HBox(10);
        badges.setAlignment(Pos.CENTER_LEFT);
        badges.setMinWidth(0);
        badges.getStyleClass().add("library-badge-row");
        if (!display.version().isBlank()) {
            badges.getChildren().add(versionMetadata(display.version(), "Project version", "version"));
        }
        addCompatibilityMetadata(badges, display.hytaleCompatibility());
        if (model.update() != null) {
            badges.getChildren().add(badge("Update ready", "game"));
        }
        if (display.localFile()) {
            badges.getChildren().add(badge("Local file", "locked"));
        }
        if (installed.isModpack()) {
            badges.getChildren().add(badge("Modpack", "modpack"));
        }
        if (InstalledProject.SOURCE_CURSEFORGE.equalsIgnoreCase(installed.source())) {
            badges.getChildren().add(badge("CurseForge", "curseforge"));
        }
        copy.getChildren().add(title);
        if (!subtitleText.isBlank()) {
            copy.getChildren().add(subtitle);
        }
        copy.getChildren().add(badges);
        return copy;
    }

    private HBox projectActions(LibraryWorldProjectModel model, List<ConfigFile> configs) {
        InstalledProject installed = model.installed();
        boolean managedProject = LibraryProjectSupport.isManagedProject(installed);
        HBox actions = new HBox(6);
        actions.getStyleClass().add("library-world-project-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (!ProjectClassification.isModpack(model.display().classification())) {
            addConfigButton(actions, model.display().title(), model.modIds(), configs);
        }

        if (managedProject && model.update() != null) {
            Button update = primaryButton("Update");
            update.getStyleClass().add("small");
            update.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 13));
            update.setTooltip(new Tooltip("Install " + model.update().newestVersionNumber()));
            update.setOnAction(event -> updateProject.accept(model.update()));
            actions.getChildren().add(update);
        }

        Button versions = iconAction(
                LauncherIcons.Glyph.LAYERS,
                !managedProject
                        ? "Local files do not have provider version history"
                        : model.loading()
                        ? "Loading release metadata"
                        : model.detail() == null ? "Load available versions" : "Version controls are ready below",
                "neutral",
                () -> {
                    if (managedProject) {
                        loadVersions.accept(installed);
                    }
                }
        );
        versions.setDisable(!managedProject || model.detail() != null || model.loading());

        actions.getChildren().add(versions);
        if (model.display().unlockVisible()) {
            Button unlock = iconAction(
                    LauncherIcons.Glyph.EDIT,
                    "Unlock this pack into individual installed mods",
                    "neutral",
                    () -> unlockProject.unlock(installed)
            );
            actions.getChildren().add(unlock);
        }
        Button remove = iconAction(
                LauncherIcons.Glyph.TRASH,
                "Remove this project",
                "danger",
                () -> uninstallProject.accept(installed)
        );
        actions.getChildren().add(remove);
        return actions;
    }

    private Node versionControls(LibraryWorldProjectModel model) {
        InstalledProject installed = model.installed();
        ProjectDetail detail = model.detail();
        if (detail == null) {
            if (!model.loading()) {
                return null;
            }
            return LauncherSkeleton.of(versionControls(new LibraryWorldProjectModel(installed,
                    LauncherSkeletonContent.detail(), model.meta(), model.update(), false, model.modIds(),
                    model.enabledCount(), model.totalCount(), model.contents(), model.display(), model.contentsCollapsed())));
        }

        List<LibraryVersionChoice> choices = LibraryProjectSupport.versionChoices(
                detail.versions(),
                installed,
                installed.gameVersion()
        );
        if (choices.isEmpty()) {
            return null;
        }
        ComboBox<LibraryVersionChoice> versions = new ComboBox<>(FXCollections.observableArrayList(choices));
        styleCombo(versions);
        choices.stream()
                .filter(choice -> LibraryProjectSupport.sameVersion(installed, choice.version()))
                .findFirst()
                .ifPresentOrElse(versions::setValue, () -> versions.setValue(choices.getFirst()));

        Button switchButton = primaryButton("");
        switchButton.setAccessibleText("Switch version");
        switchButton.setTooltip(new Tooltip("Switch version"));
        switchButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 13));
        switchButton.setOnAction(event -> {
            LibraryVersionChoice choice = versions.getValue();
            if (choice != null) {
                switchVersion.switchVersion(installed, detail, choice.version());
            }
        });
        Runnable updateSwitchState = () -> {
            LibraryVersionChoice choice = versions.getValue();
            switchButton.setDisable(choice == null || LibraryProjectSupport.sameVersion(installed, choice.version()));
        };
        versions.valueProperty().addListener((observable, previous, next) -> updateSwitchState.run());
        updateSwitchState.run();

        HBox controls = new HBox(10, versions, switchButton);
        controls.getStyleClass().add("library-world-version-row");
        HBox.setHgrow(versions, Priority.ALWAYS);
        return controls;
    }

    private Node contentsCard(LibraryWorldProjectModel model, List<ConfigFile> configs) {
        if (!model.display().contentsVisible()) {
            return null;
        }
        int contentCount = model.contents().isEmpty()
                ? LibraryProjectSupport.contentCount(model.installed())
                : model.contents().size();
        Button toggle = new Button(null, LauncherIcons.icon(
                model.contentsCollapsed() ? LauncherIcons.Glyph.CHEVRON_DOWN : LauncherIcons.Glyph.CHEVRON_UP,
                13
        ));
        toggle.getStyleClass().addAll("library-icon-action", "library-icon-action-neutral");
        toggle.setTooltip(new Tooltip(model.contentsCollapsed() ? "Show included mods" : "Collapse included mods"));
        toggle.setAccessibleText(model.contentsCollapsed() ? "Show included mods" : "Collapse included mods");
        toggle.setOnAction(event -> toggleModpackContents.accept(model.installed()));

        Label title = new Label("Included mods");
        title.getStyleClass().add("library-child-title");
        Label count = new Label(contentCount + " item" + LibraryProjectSupport.plural(contentCount));
        count.getStyleClass().add("library-muted-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, title, count, spacer, toggle);
        header.getStyleClass().add("library-world-content-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPickOnBounds(true);
        header.setOnMouseClicked(event -> {
            Node target = event.getTarget() instanceof Node node ? node : null;
            while (target != null && target != header) {
                if (target == toggle) {
                    return;
                }
                target = target.getParent();
            }
            toggleModpackContents.accept(model.installed());
        });

        VBox card = new VBox(8);
        card.getStyleClass().add("library-world-content-card");
        card.getChildren().add(header);
        if (!model.contentsExpanded()) {
            return card;
        }

        VBox contents = new VBox(8);
        contents.getStyleClass().add("library-world-content-list");
        if (model.contents().isEmpty()) {
            contents.getChildren().add(emptyState("No individual manifests found", "This pack is installed, but its files did not expose separate mod ids."));
            card.getChildren().add(contents);
            return card;
        }
        for (LibraryWorldContentItem item : model.contents()) {
            contents.getChildren().add(compactContentRow(item, configs));
        }
        card.getChildren().add(contents);
        return card;
    }

    private Node compactContentRow(LibraryWorldContentItem item, List<ConfigFile> configs) {
        HBox row = new HBox(9);
        row.getStyleClass().addAll("library-world-content-row", "library-world-content-row-compact");
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = contentIcon(item);
        VBox copy = new VBox(2);
        Label title = new Label(item.title());
        title.getStyleClass().add("library-child-title");
        Label meta = new Label(item.meta().isBlank() ? "Included in modpack" : item.meta());
        meta.getStyleClass().add("library-child-meta");
        HBox metadata = new HBox(10, meta);
        metadata.setAlignment(Pos.CENTER_LEFT);
        addCompatibilityMetadata(metadata, item.hytaleCompatibility());
        copy.getChildren().addAll(title, metadata);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Label status = new Label("Included");
        status.getStyleClass().add("library-version-pill");
        row.getChildren().addAll(icon, copy);
        if (InstalledProject.SOURCE_CURSEFORGE.equalsIgnoreCase(item.source())) {
            row.getChildren().add(badge("CurseForge", "curseforge"));
        }
        addConfigButton(row, item.title(), item.modIds(), configs);
        row.getChildren().add(status);
        return row;
    }

    private void addConfigButton(HBox actions, String title, List<String> modIds, List<ConfigFile> configs) {
        List<ConfigFile> matching = configs.stream()
                .filter(file -> !file.pluginId().isBlank() && modIds.contains(file.pluginId())).toList();
        if (matching.isEmpty()) return;
        Button config = new Button("Config");
        config.getStyleClass().addAll("library-icon-action", "library-text-action");
        config.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.FILE_CODE, 14));
        config.setAccessibleText("Config for " + title);
        config.setTooltip(new Tooltip("Edit configs for " + title));
        config.setOnAction(event -> editConfigs.accept(title, matching));
        actions.getChildren().add(config);
    }

    private StackPane projectIcon(LibraryWorldProjectModel model, double size) {
        ProjectMeta meta = model.meta();
        InstalledProject installed = model.installed();
        LibraryWorldProjectDisplay display = model.display();
        String iconUrl = display.icon();
        String title = first(display.title(), meta == null ? "" : meta.title(), installed.title(), "M");
        LauncherIcons.Glyph glyph = ProjectClassification.isModpack(display.classification())
                ? LauncherIcons.Glyph.LAYERS
                : LauncherIcons.Glyph.BOX;
        return imageIcon(iconUrl, title, glyph, size, "library-project-icon", true);
    }

    private StackPane contentIcon(LibraryWorldContentItem item) {
        LauncherIcons.Glyph glyph = ProjectClassification.isModpack(item.classification())
                ? LauncherIcons.Glyph.LAYERS
                : LauncherIcons.Glyph.FILE_CODE;
        return imageIcon(item.icon(), item.title(), glyph, CONTENT_ICON_SIZE, "library-child-icon", false);
    }

    private StackPane imageIcon(
            String iconUrl,
            String title,
            LauncherIcons.Glyph fallbackGlyph,
            double size,
            String styleClass,
            boolean useProjectFallback
    ) {
        StackPane shell = new StackPane();
        shell.getStyleClass().add(styleClass);
        double borderWidth = styleClass.equals("library-project-icon") || styleClass.equals("library-detail-icon") ? 4 : 2;
        if (borderWidth == 4) {
            shell.getStyleClass().add("library-mod-icon");
        }
        shell.setMinSize(size, size);
        shell.setPrefSize(size, size);
        shell.setMaxSize(size, size);
        double mediaSize = Math.max(1, size - borderWidth * 2);
        double clipRadius = (borderWidth == 4 ? 16 : 8) - borderWidth / 2;

        Node fallback;
        if (imageLoader != null && useProjectFallback) {
            ImageView placeholder = new ImageView();
            placeholder.fitWidthProperty().bind(shell.widthProperty().subtract(borderWidth * 2));
            placeholder.fitHeightProperty().bind(shell.heightProperty().subtract(borderWidth * 2));
            placeholder.setPreserveRatio(true);
            placeholder.setSmooth(true);
            placeholder.setClip(roundedClip(placeholder, clipRadius));
            imageLoader.loadInto(placeholder, null, mediaSize * 3, mediaSize * 3, true);
            fallback = placeholder;
        } else {
            fallback = LauncherIcons.icon(fallbackGlyph, Math.max(15, size * 0.42));
        }
        shell.getChildren().add(fallback);

        if (imageLoader != null && iconUrl != null && !iconUrl.isBlank()) {
            ImageView image = new ImageView();
            image.fitWidthProperty().bind(shell.widthProperty().subtract(borderWidth * 2));
            image.fitHeightProperty().bind(shell.heightProperty().subtract(borderWidth * 2));
            image.setPreserveRatio(true);
            if (fallbackGlyph == LauncherIcons.Glyph.GLOBE) {
                LibraryWorldIcon.cropToSquare(image);
            }
            image.setSmooth(true);
            image.setMouseTransparent(true);
            image.setClip(roundedClip(image, clipRadius));
            double renderScale = fallbackGlyph == LauncherIcons.Glyph.GLOBE ? 6 : 3;
            imageLoader.loadInto(image, iconUrl, mediaSize * renderScale, mediaSize * renderScale, true);
            CachedImageLoader.showFallbackUntilLoaded(fallback, image);
            shell.getChildren().add(image);
            return shell;
        }

        if (fallbackGlyph == LauncherIcons.Glyph.BOX && title != null && !title.isBlank()) {
            shell.setAccessibleText(title.substring(0, 1).toUpperCase(Locale.ROOT));
        }
        return shell;
    }

    private Rectangle roundedClip(ImageView image, double radius) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> image.getLayoutBounds().getWidth(), image.layoutBoundsProperty()));
        clip.heightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> image.getLayoutBounds().getHeight(), image.layoutBoundsProperty()));
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        return clip;
    }

    private String projectMetaLine(LibraryWorldProjectModel model) {
        String author = model.display().author();
        return author.isBlank() ? "" : "by " + author;
    }

    private void addCompatibilityMetadata(HBox row, List<String> requirements) {
        for (String requirement : requirements) {
            if (requirement.isBlank()) continue;
            if (!row.getChildren().isEmpty()) {
                Label divider = new Label("·");
                divider.getStyleClass().add("library-version-divider");
                row.getChildren().add(divider);
            }
            Label compatibility = versionMetadata(ManifestVersionLabel.format(requirement, knownGameVersions), "Compatible versions", "build");
            compatibility.setTooltip(new Tooltip("Manifest ServerVersion: " + requirement));
            row.getChildren().add(compatibility);
        }
    }

    private Label versionMetadata(String value, String description, String tone) {
        Label label = new Label(value);
        label.getStyleClass().addAll("library-version-metadata", "library-version-metadata-" + tone);
        label.setMinWidth(0);
        label.setTooltip(new Tooltip(description + ": " + value));
        label.setAccessibleText(description + ": " + value);
        return label;
    }

    private Node badge(String text, String tone) {
        Label label = new Label(text);
        label.getStyleClass().addAll("library-badge", "library-badge-" + tone);
        return label;
    }

    private Button iconAction(LauncherIcons.Glyph glyph, String tooltip, String tone, Runnable action) {
        Button button = new Button(null, LauncherIcons.icon(glyph, 14));
        button.getStyleClass().addAll("library-icon-action", "library-icon-action-" + tone);
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }
        for (String candidate : values) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }
}
