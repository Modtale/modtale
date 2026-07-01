package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.classificationLabel;
import static net.modtale.launcher.ui.common.LauncherUi.dangerButton;
import static net.modtale.launcher.ui.common.LauncherUi.emptyState;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.styleCombo;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.install.UpdateCandidate;
import net.modtale.launcher.model.project.ProjectClassification;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.ui.common.LauncherExternalLinks;
import net.modtale.launcher.ui.common.LauncherIcons;

final class LibraryProjectRenderer {

    private final Consumer<String> selectProject;
    private final Consumer<UpdateCandidate> updateProject;
    private final VersionSwitchHandler switchVersion;
    private final Consumer<InstalledProject> uninstallProject;
    private final UnlockHandler unlockProject;
    private final WorldToggleHandler toggleWorldMods;
    private final Consumer<HytaleWorld> shareWorldSnapshot;
    private final Consumer<HytaleWorld> createModpackFromWorld;
    private final BiConsumer<String, String> toast;

    LibraryProjectRenderer(
            Consumer<String> selectProject,
            Consumer<UpdateCandidate> updateProject,
            VersionSwitchHandler switchVersion,
            Consumer<InstalledProject> uninstallProject,
            UnlockHandler unlockProject,
            WorldToggleHandler toggleWorldMods,
            Consumer<HytaleWorld> shareWorldSnapshot,
            Consumer<HytaleWorld> createModpackFromWorld,
            BiConsumer<String, String> toast
    ) {
        this.selectProject = selectProject;
        this.updateProject = updateProject;
        this.switchVersion = switchVersion;
        this.uninstallProject = uninstallProject;
        this.unlockProject = unlockProject;
        this.toggleWorldMods = toggleWorldMods;
        this.shareWorldSnapshot = shareWorldSnapshot;
        this.createModpackFromWorld = createModpackFromWorld;
        this.toast = toast;
    }

    List<Node> projectDetail(LibraryDetailModel model) {
        if (model == null || model.installed() == null) {
            return List.of(emptyState("No project selected", "Install a project from Browse."));
        }
        InstalledProject installed = model.installed();
        List<Node> sections = new ArrayList<>();
        sections.add(detailHeader(installed, model.update()));
        sections.add(versionSection(model));
        Node contents = bundledProjectsSection(model);
        if (contents != null) {
            sections.add(contents);
        }
        sections.add(worldsSection(model));
        sections.add(filesSection(installed));
        return sections;
    }

    private Node detailHeader(InstalledProject installed, UpdateCandidate update) {
        VBox section = new VBox(12);
        section.getStyleClass().add("library-detail-hero");

        HBox row = new HBox(14);
        row.getStyleClass().add("library-detail-heading");
        StackPane icon = projectIcon(installed.classification(), 22);
        icon.getStyleClass().add("library-detail-icon");
        VBox copy = new VBox(7);
        Label title = new Label(value(installed.title(), "Untitled Project"));
        title.getStyleClass().add("library-detail-title");
        HBox badges = new HBox(7);
        badges.getStyleClass().add("library-badge-row");
        badges.getChildren().add(badge(classificationLabel(installed.classification()), "type"));
        badges.getChildren().add(badge(value(installed.installedVersion(), "installed"), "version"));
        if (!installed.gameVersion().isBlank()) {
            badges.getChildren().add(badge(installed.gameVersion(), "game"));
        }
        if (installed.isModpack()) {
            badges.getChildren().add(badge("Grouped", "locked"));
        }
        copy.getChildren().addAll(title, badges);
        HBox.setHgrow(copy, Priority.ALWAYS);

        VBox actions = new VBox(8);
        actions.getStyleClass().add("library-actions");
        HBox mainActions = new HBox(8);
        if (update != null) {
            Button updateButton = primaryButton("Update");
            updateButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 14));
            updateButton.setTooltip(new Tooltip("Install " + update.newestVersionNumber()));
            updateButton.setOnAction(event -> updateProject.accept(update));
            mainActions.getChildren().add(updateButton);
        }
        Button uninstall = dangerButton("Remove");
        uninstall.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.TRASH, 14));
        uninstall.setMaxWidth(Double.MAX_VALUE);
        uninstall.setOnAction(event -> uninstallProject.accept(installed));
        if (!mainActions.getChildren().isEmpty()) {
            actions.getChildren().add(mainActions);
        }
        actions.getChildren().add(uninstall);
        row.getChildren().addAll(icon, copy, actions);

        HBox facts = new HBox(10);
        facts.getStyleClass().add("library-facts");
        facts.getChildren().addAll(
                fact("Installed", LibraryProjectSupport.dateLabel(installed.installedAt())),
                fact("Updated", LibraryProjectSupport.dateLabel(installed.updatedAt())),
                fact("Files", Integer.toString(installed.files().size())),
                fact("Contents", Integer.toString(LibraryProjectSupport.contentCount(installed)))
        );
        section.getChildren().addAll(row, facts);
        return section;
    }

    private Node versionSection(LibraryDetailModel model) {
        InstalledProject installed = model.installed();
        ProjectDetail detail = model.detail();
        VBox section = detailSection("Versions");
        if (detail == null) {
            HBox loading = new HBox(10);
            loading.getStyleClass().add("library-inline-panel");
            loading.setAlignment(Pos.CENTER_LEFT);
            Label meta = new Label(model.loading() ? "Loading release metadata" : "Release metadata not loaded");
            meta.getStyleClass().add("library-muted-text");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button load = secondaryButton(model.loading() ? "Loading" : "Load");
            load.setDisable(true);
            loading.getChildren().addAll(meta, spacer, load);
            section.getChildren().add(loading);
            return section;
        }

        List<LibraryVersionChoice> choices = LibraryProjectSupport.versionChoices(
                detail.versions(),
                installed,
                model.settings().getGameVersion()
        );
        ComboBox<LibraryVersionChoice> versions = new ComboBox<>(FXCollections.observableArrayList(choices));
        styleCombo(versions);
        choices.stream()
                .filter(choice -> LibraryProjectSupport.sameVersion(installed, choice.version()))
                .findFirst()
                .ifPresentOrElse(versions::setValue, () -> {
                    if (!choices.isEmpty()) {
                        versions.setValue(choices.getFirst());
                    }
                });

        Button switchButton = primaryButton("Switch");
        switchButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 14));
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

        HBox row = new HBox(10, versions, switchButton);
        row.getStyleClass().add("library-version-control");
        HBox.setHgrow(versions, Priority.ALWAYS);
        section.getChildren().add(row);
        return section;
    }

    private Node bundledProjectsSection(LibraryDetailModel model) {
        InstalledProject installed = model.installed();
        if (!installed.isModpack()
                && installed.bundledProjects().isEmpty()
                && installed.dependencyProjectIds().isEmpty()
                && installed.externalDependencies().isEmpty()) {
            return null;
        }
        VBox section = detailSection(installed.isModpack() ? "Modpack Contents" : "Bundled Dependencies");
        HBox header = new HBox(10);
        header.getStyleClass().add("library-inline-panel");
        header.setAlignment(Pos.CENTER_LEFT);
        Label status = new Label("Grouped");
        status.getStyleClass().add("library-status-locked");
        int contentCount = LibraryProjectSupport.contentCount(installed);
        Label count = new Label(contentCount + " item" + LibraryProjectSupport.plural(contentCount));
        count.getStyleClass().add("library-muted-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button lock = secondaryButton("Unlock");
        lock.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.EDIT, 14));
        lock.setOnAction(event -> unlockProject.unlock(installed));
        header.getChildren().addAll(status, count, spacer, lock);
        section.getChildren().add(header);

        VBox rows = new VBox(8);
        rows.getStyleClass().add("library-bundled-list");
        if (!installed.bundledProjects().isEmpty()) {
            installed.bundledProjects().forEach(reference ->
                    rows.getChildren().add(bundledProjectRow(model, reference)));
        } else if (!installed.dependencyProjectIds().isEmpty() || !installed.externalDependencies().isEmpty()) {
            installed.dependencyProjectIds().forEach(id -> rows.getChildren().add(legacyBundledRow(model, id, true)));
            installed.externalDependencies().forEach(id -> rows.getChildren().add(legacyBundledRow(model, id, false)));
        } else {
            installed.files().forEach(file -> rows.getChildren().add(fileContentRow(installed, file)));
        }
        section.getChildren().add(rows);
        return section;
    }

    private Node bundledProjectRow(LibraryDetailModel model, InstalledProjectReference reference) {
        InstalledProject parent = model.installed();
        HBox row = new HBox(10);
        row.getStyleClass().add("library-bundled-row-locked");
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = new StackPane(LauncherIcons.icon(reference.isModtaleProject()
                ? LauncherIcons.Glyph.BOX
                : LauncherIcons.Glyph.EXTERNAL_LINK, 15));
        icon.getStyleClass().add("library-child-icon");
        VBox copy = new VBox(3);
        Label title = new Label(reference.displayName());
        title.getStyleClass().add("library-child-title");
        Label meta = new Label(LibraryProjectSupport.childMeta(reference));
        meta.getStyleClass().add("library-child-meta");
        copy.getChildren().addAll(title, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);

        InstalledProject installedChild = reference.projectId().isBlank()
                ? null
                : installedByProjectId(model.installedProjects(), reference.projectId()).orElse(null);
        Button action = childActionButton(parent, reference, installedChild);
        row.getChildren().addAll(icon, copy, action);
        return row;
    }

    private Node legacyBundledRow(LibraryDetailModel model, String id, boolean modtale) {
        InstalledProjectReference reference = new InstalledProjectReference(
                id,
                modtale ? id : "",
                "",
                id,
                "",
                "",
                "",
                modtale ? "MODTALE" : "EXTERNAL",
                modtale ? "" : id,
                "",
                "",
                "",
                "",
                "",
                null,
                null
        );
        return bundledProjectRow(model, reference);
    }

    private Node fileContentRow(InstalledProject parent, String file) {
        HBox row = new HBox(10);
        row.getStyleClass().add("library-bundled-row-locked");
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.FILE_CODE, 15));
        icon.getStyleClass().add("library-child-icon");
        VBox copy = new VBox(3);
        Label title = new Label(Path.of(file).getFileName().toString());
        title.getStyleClass().add("library-child-title");
        Label meta = new Label("Installed file");
        meta.getStyleClass().add("library-child-meta");
        copy.getChildren().addAll(title, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Button action = secondaryButton("Grouped");
        action.setDisable(true);
        row.getChildren().addAll(icon, copy, action);
        return row;
    }

    private Button childActionButton(
            InstalledProject parent,
            InstalledProjectReference reference,
            InstalledProject installedChild
    ) {
        Button action;
        if (installedChild != null) {
            action = secondaryButton("Select");
            action.setOnAction(event -> selectProject.accept(installedChild.projectId()));
            return action;
        }
        if (reference.isModtaleProject()) {
            action = secondaryButton("Included");
            action.setDisable(true);
            return action;
        }
        action = secondaryButton("Open");
        action.setOnAction(event -> LauncherExternalLinks.open(
                value(reference.externalUrl(), reference.cachedFileUrl()),
                toast
        ));
        action.setDisable(reference.externalUrl().isBlank() && reference.cachedFileUrl().isBlank());
        return action;
    }

    private Node worldsSection(LibraryDetailModel model) {
        VBox section = detailSection("Worlds");
        if (model.worldToggles().isEmpty()) {
            section.getChildren().add(emptyState("No world controls", "Create a Hytale world or refresh this install record."));
            return section;
        }
        VBox rows = new VBox(8);
        rows.getStyleClass().add("library-world-list");
        for (LibraryWorldToggle toggle : model.worldToggles()) {
            rows.getChildren().add(worldToggleRow(toggle));
        }
        section.getChildren().add(rows);
        return section;
    }

    private Node worldToggleRow(LibraryWorldToggle worldToggle) {
        LibraryToggleBox toggle = new LibraryToggleBox();
        toggle.setSelected(worldToggle.selected());
        toggle.setIndeterminate(worldToggle.indeterminate());
        toggle.setOnAction(() -> toggleWorldMods.setEnabled(
                worldToggle.world(),
                worldToggle.modIds(),
                toggle.isSelected()
        ));

        VBox copy = new VBox(3);
        Label title = new Label(worldToggle.world().name());
        title.getStyleClass().add("library-world-title");
        Label meta = new Label(worldToggle.enabledCount() + "/" + worldToggle.totalCount() + " enabled - " + worldToggle.meta());
        meta.getStyleClass().add("library-world-meta");
        copy.getChildren().addAll(title, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Button share = secondaryButton("Share");
        share.getStyleClass().add("library-world-share");
        share.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.SHARE_2, 13));
        share.setTooltip(new Tooltip("Copy a share link for this world's enabled mods"));
        share.setOnAction(event -> shareWorldSnapshot.accept(worldToggle.world()));

        Button pack = secondaryButton("Make Pack");
        pack.getStyleClass().add("library-world-pack");
        pack.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.PACKAGE_PLUS, 13));
        pack.setTooltip(new Tooltip("Start a Modtale modpack from this world's enabled mods"));
        pack.setOnAction(event -> createModpackFromWorld.accept(worldToggle.world()));

        HBox row = new HBox(10, toggle, copy, share, pack);
        row.getStyleClass().add("library-world-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node filesSection(InstalledProject installed) {
        VBox section = detailSection("Files");
        if (installed.files().isEmpty()) {
            section.getChildren().add(emptyState("No files recorded", "Switching versions will refresh this install record."));
            return section;
        }
        VBox files = new VBox(6);
        files.getStyleClass().add("library-file-list");
        installed.files().stream()
                .limit(8)
                .forEach(file -> files.getChildren().add(fileRow(file)));
        if (installed.files().size() > 8) {
            Label remaining = new Label("+" + (installed.files().size() - 8) + " more");
            remaining.getStyleClass().add("library-muted-text");
            files.getChildren().add(remaining);
        }
        section.getChildren().add(files);
        return section;
    }

    private Node fileRow(String file) {
        HBox row = new HBox(8);
        row.getStyleClass().add("library-file-row");
        row.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(Path.of(file).getFileName().toString());
        name.getStyleClass().add("library-file-name");
        Label path = new Label(file);
        path.getStyleClass().add("library-file-path");
        HBox.setHgrow(path, Priority.ALWAYS);
        row.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.FILE_CODE, 14), name, path);
        return row;
    }

    private VBox detailSection(String title) {
        VBox section = new VBox(10);
        section.getStyleClass().add("library-detail-section");
        Label label = new Label(title);
        label.getStyleClass().add("library-section-title");
        section.getChildren().add(label);
        return section;
    }

    private Node fact(String label, String value) {
        VBox fact = new VBox(3);
        fact.getStyleClass().add("library-fact");
        Label title = new Label(label);
        title.getStyleClass().add("library-fact-label");
        Label body = new Label(value);
        body.getStyleClass().add("library-fact-value");
        fact.getChildren().addAll(title, body);
        HBox.setHgrow(fact, Priority.ALWAYS);
        return fact;
    }

    private Node badge(String text, String tone) {
        Label label = new Label(text);
        label.getStyleClass().addAll("library-badge", "library-badge-" + tone);
        return label;
    }

    private StackPane projectIcon(String classification, double size) {
        LauncherIcons.Glyph glyph = ProjectClassification.isModpack(classification)
                ? LauncherIcons.Glyph.LAYERS
                : LauncherIcons.Glyph.BOX;
        return new StackPane(LauncherIcons.icon(glyph, size));
    }

    private Optional<InstalledProject> installedByProjectId(List<InstalledProject> installedProjects, String projectId) {
        return installedProjects.stream()
                .filter(project -> project.projectId().equals(projectId))
                .findFirst();
    }

    interface VersionSwitchHandler {
        void switchVersion(InstalledProject installed, ProjectDetail detail, ProjectVersion version);
    }

    interface UnlockHandler {
        void unlock(InstalledProject installed);
    }

    interface WorldToggleHandler {
        void setEnabled(HytaleWorld world, List<String> modIds, boolean enabled);
    }
}
