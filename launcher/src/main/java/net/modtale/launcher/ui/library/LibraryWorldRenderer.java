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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
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
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class LibraryWorldRenderer {

    private static final double PROJECT_ICON_SIZE = 46;
    private static final double CONTENT_ICON_SIZE = 34;
    private static final PseudoClass CONTENTS_HOVERED = PseudoClass.getPseudoClass("contents-hovered");

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

        HBox row = new HBox(12);
        row.getStyleClass().add("library-detail-heading");
        StackPane icon = imageIcon(
                model.world().previewImage(),
                model.world().name(),
                LauncherIcons.Glyph.GLOBE,
                44,
                "library-detail-icon",
                false
        );
        icon.getStyleClass().add("library-world-detail-icon");

        VBox copy = new VBox();
        copy.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(model.world().name());
        title.getStyleClass().addAll("library-detail-title", "library-world-detail-title");
        title.setMaxWidth(Double.MAX_VALUE);
        copy.getChildren().add(title);
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
        Region actionDivider = new Region();
        actionDivider.getStyleClass().add("library-action-divider");
        actionDivider.setMouseTransparent(true);
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
        actions.getChildren().addAll(refresh, updates, actionDivider, share, pack);

        row.getChildren().addAll(icon, copy, actions);
        section.getChildren().add(row);
        return section;
    }

    private Node installedProjectsSection(LibraryWorldModel model, List<ConfigFile> configs) {
        return installedProjectsSection(
                model.world(),
                model.projects(),
                "No installed projects",
                "Install mods from Browse to manage them per world.", configs
        );
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

        StackPane icon = projectIcon(model, PROJECT_ICON_SIZE);
        VBox copy = projectCopy(model);
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
        return shell;
    }

    private VBox projectCopy(LibraryWorldProjectModel model) {
        InstalledProject installed = model.installed();
        LibraryWorldProjectDisplay display = model.display();
        VBox copy = new VBox(5);
        Label title = new Label(display.title());
        title.getStyleClass().add("library-world-project-title");

        String subtitleText = projectMetaLine(model);
        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("library-world-project-meta");

        HBox badges = new HBox(7);
        badges.getStyleClass().add("library-badge-row");
        if (!display.version().isBlank()) {
            badges.getChildren().add(badge(display.version(), "version"));
        }
        if (!installed.gameVersion().isBlank()) {
            badges.getChildren().add(badge(installed.gameVersion(), "game"));
        }
        if (model.update() != null) {
            badges.getChildren().add(badge("Update ready", "game"));
        }
        if (display.localFile()) {
            badges.getChildren().add(badge("Local file", "locked"));
        }
        if (installed.isModpack()) {
            badges.getChildren().add(badge("Modpack", "modpack"));
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
        boolean modtaleProject = LibraryProjectSupport.isModtaleProject(installed);
        HBox actions = new HBox(6);
        actions.getStyleClass().add("library-world-project-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (!ProjectClassification.isModpack(model.display().classification())) {
            addConfigButton(actions, model.display().title(), model.modIds(), configs);
        }

        if (modtaleProject && model.update() != null) {
            Button update = primaryButton("Update");
            update.getStyleClass().add("small");
            update.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 13));
            update.setTooltip(new Tooltip("Install " + model.update().newestVersionNumber()));
            update.setOnAction(event -> updateProject.accept(model.update()));
            actions.getChildren().add(update);
        }

        Button versions = iconAction(
                LauncherIcons.Glyph.LAYERS,
                !modtaleProject
                        ? "Local files do not have Modtale version history"
                        : model.loading()
                        ? "Loading release metadata"
                        : model.detail() == null ? "Load available versions" : "Version controls are ready below",
                "neutral",
                () -> {
                    if (modtaleProject) {
                        loadVersions.accept(installed);
                    }
                }
        );
        versions.setDisable(!modtaleProject || model.detail() != null || model.loading());

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

        Button switchButton = primaryButton("Switch");
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
        copy.getChildren().addAll(title, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Label status = new Label("Included");
        status.getStyleClass().add("library-version-pill");
        row.getChildren().addAll(icon, copy);
        addConfigButton(row, item.title(), item.modIds(), configs);
        row.getChildren().add(status);
        return row;
    }

    private void addConfigButton(HBox actions, String title, List<String> modIds, List<ConfigFile> configs) {
        List<ConfigFile> matching = configs.stream()
                .filter(file -> !file.pluginId().isBlank() && modIds.contains(file.pluginId())).toList();
        if (matching.isEmpty()) return;
        Button config = secondaryButton("Config");
        config.getStyleClass().add("small");
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
        shell.setMinSize(size, size);
        shell.setPrefSize(size, size);
        shell.setMaxSize(size, size);
        double mediaSize = Math.max(1, size - 4);
        double clipRadius = 6;

        if (imageLoader != null && useProjectFallback) {
            ImageView fallback = new ImageView();
            fallback.fitWidthProperty().bind(shell.widthProperty().subtract(4));
            fallback.fitHeightProperty().bind(shell.heightProperty().subtract(4));
            fallback.setPreserveRatio(false);
            fallback.setSmooth(true);
            fallback.setClip(roundedClip(shell, clipRadius));
            imageLoader.loadInto(fallback, null, mediaSize * 2, mediaSize * 2);
            shell.getChildren().add(fallback);
        }

        if (imageLoader != null && iconUrl != null && !iconUrl.isBlank()) {
            ImageView image = new ImageView();
            image.fitWidthProperty().bind(shell.widthProperty().subtract(4));
            image.fitHeightProperty().bind(shell.heightProperty().subtract(4));
            image.setSmooth(true);
            image.setMouseTransparent(true);
            image.setClip(roundedClip(shell, clipRadius));
            imageLoader.loadInto(image, iconUrl, mediaSize, mediaSize);
            shell.getChildren().add(image);
            return shell;
        }

        Node fallback = LauncherIcons.icon(fallbackGlyph, Math.max(15, size * 0.42));
        shell.getChildren().add(fallback);
        if (fallbackGlyph == LauncherIcons.Glyph.BOX && title != null && !title.isBlank()) {
            shell.setAccessibleText(title.substring(0, 1).toUpperCase(Locale.ROOT));
        }
        return shell;
    }

    private Rectangle roundedClip(StackPane shell, double radius) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(shell.widthProperty().subtract(4));
        clip.heightProperty().bind(shell.heightProperty().subtract(4));
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        return clip;
    }

    private String projectMetaLine(LibraryWorldProjectModel model) {
        String author = model.display().author();
        return author.isBlank() ? "" : "by " + author;
    }

    private boolean hasUnlockableContents(InstalledProject installed) {
        return installed.isModpack()
                || !installed.bundledProjects().isEmpty()
                || !installed.dependencyProjectIds().isEmpty()
                || !installed.externalDependencies().isEmpty();
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
