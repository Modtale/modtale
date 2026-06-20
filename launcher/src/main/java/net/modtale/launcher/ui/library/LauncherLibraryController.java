package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.emptyState;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.hytale.HytaleWorldManager;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorldConfig;
import net.modtale.launcher.install.ModInstaller;
import net.modtale.launcher.install.UpdateService;
import net.modtale.launcher.install.WorldModListInstaller;
import net.modtale.launcher.model.install.InstallResult;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.install.UpdateCandidate;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectMeta;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.worldlist.CreateWorldModListRequest;
import net.modtale.launcher.model.worldlist.WorldModList;
import net.modtale.launcher.model.worldlist.WorldModListInstallResult;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherExternalLinks;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.common.StatusModal;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;

public final class LauncherLibraryController {

    private final ModtaleApiClient apiClient;
    private final ModInstaller installer;
    private final WorldModListInstaller worldModListInstaller;
    private final UpdateService updateService;
    private final LauncherSettingsController settingsController;
    private final LauncherAccountController accountController;
    private final LauncherFeedback feedback;
    private final Executor executor;
    private final Supplier<StackPane> overlayHost;
    private final HytaleWorldManager worldManager = new HytaleWorldManager();
    private final VBox projectList = new VBox(10);
    private final VBox projectDetail = new VBox(14);
    private final VBox updatesList = new VBox(12);
    private final Map<String, ProjectDetail> projectDetails = new LinkedHashMap<>();
    private final Map<String, ProjectMeta> projectMetadata = new LinkedHashMap<>();
    private final Map<String, UpdateCandidate> availableUpdates = new LinkedHashMap<>();
    private final Set<String> loadingProjectIds = new LinkedHashSet<>();
    private final Set<String> loadingMetadataIds = new LinkedHashSet<>();
    private final Set<String> expandedModpackContents = new LinkedHashSet<>();
    private final LibraryWorldRenderer worldRenderer;
    private final LibraryWorldListRenderer worldListRenderer;
    private final LibraryProjectListRenderer listRenderer;
    private final PostDownloadWorldModal postDownloadWorldModal;

    private List<UpdateCandidate> currentUpdates = List.of();
    private List<InstalledProject> installedProjects = List.of();
    private List<HytaleWorld> worlds = List.of();
    private List<HytaleInstalledMod> installedMods = List.of();
    private String selectedWorldKey = "";
    private Node libraryView;
    private Node updatesView;
    private StackPane installLoadingOverlay;
    private Label installLoadingTitle;
    private Label installLoadingSubtitle;

    public LauncherLibraryController(
            ModtaleApiClient apiClient,
            ModInstaller installer,
            WorldModListInstaller worldModListInstaller,
            UpdateService updateService,
            LauncherSettingsController settingsController,
            LauncherAccountController accountController,
            LauncherFeedback feedback,
            Executor executor,
            CachedImageLoader imageLoader,
            Supplier<StackPane> overlayHost
    ) {
        this.apiClient = apiClient;
        this.installer = installer;
        this.worldModListInstaller = worldModListInstaller;
        this.updateService = updateService;
        this.settingsController = settingsController;
        this.accountController = accountController;
        this.feedback = feedback;
        this.executor = executor;
        this.overlayHost = overlayHost == null ? () -> null : overlayHost;
        this.listRenderer = new LibraryProjectListRenderer(ignored -> {
        }, this::updateSelected);
        this.postDownloadWorldModal = new PostDownloadWorldModal(
                this.overlayHost,
                this::applyPostDownloadWorldSelection,
                imageLoader
        );
        this.worldListRenderer = new LibraryWorldListRenderer(imageLoader, this::selectWorld);
        this.worldRenderer = new LibraryWorldRenderer(
                imageLoader,
                this::updateSelected,
                this::ensureProjectDetailLoaded,
                this::switchSelectedVersion,
                this::uninstallSelected,
                this::unlockModpack,
                this::toggleModpackContents,
                this::setModsEnabled,
                this::shareWorldSnapshot,
                this::createModpackFromWorld
        );
    }

    public Node libraryView() {
        if (libraryView == null) {
            libraryView = buildLibraryView();
            renderLibrary();
        }
        return libraryView;
    }

    public Node updatesView() {
        if (updatesView == null) {
            VBox view = new VBox(12);
            view.setUserData(LauncherView.UPDATES);
            view.getStyleClass().addAll("view", "library-updates-view");
            view.getChildren().add(updatesList);
            updatesView = view;
            renderUpdates();
        }
        return updatesView;
    }

    public void refresh() {
        renderLibrary();
        renderUpdates();
    }

    public LauncherSettings settings() {
        return settingsController.settings();
    }

    public void installSelectedProject(ProjectSummary summary) {
        settingsController.saveFromFields(false);
        LauncherSettings settings = settingsController.settings();
        if (!confirmDuplicateInstall(InstallDuplicateWarning.forSummary(settings.getInstalledProjects(), summary))) {
            return;
        }
        String status = "Installing " + summary + "...";
        runInstallWithOverlay(status, () -> {
            accountController.ensureSignedIn();
            ProjectDetail project = detailWithVersions(summary.routeKey());
            return installer.installAndRecord(project, settings);
        }, this::finishInstall);
    }

    public void installSelectedProjectVersion(ProjectDetail project, ProjectVersion version, String gameVersion) {
        settingsController.saveFromFields(false);
        LauncherSettings settings = settingsController.settings();
        if (!confirmDuplicateInstall(InstallDuplicateWarning.forProject(
                settings.getInstalledProjects(),
                project,
                version,
                dependenciesForSettings(version, settings),
                settings.isIncludeOptionalDependencies()
        ))) {
            return;
        }
        String status = "Installing " + project.title() + " " + version.versionNumber() + "...";
        runInstallWithOverlay(status, () -> {
            accountController.ensureSignedIn();
            return installer.installAndRecord(project, version, settings, gameVersion);
        }, this::finishInstall);
    }

    public void installSelectedProjectVersion(
            ProjectDetail project,
            ProjectVersion version,
            String gameVersion,
            List<ProjectDependency> selectedDependencies
    ) {
        settingsController.saveFromFields(false);
        LauncherSettings settings = settingsController.settings();
        List<ProjectDependency> dependencies = selectedDependencies == null ? List.of() : List.copyOf(selectedDependencies);
        if (!confirmDuplicateInstall(InstallDuplicateWarning.forProject(
                settings.getInstalledProjects(),
                project,
                version,
                dependencies,
                true
        ))) {
            return;
        }
        String status = "Installing " + project.title() + " " + version.versionNumber() + "...";
        runInstallWithOverlay(status, () -> {
            accountController.ensureSignedIn();
            return installer.installAndRecord(project, version, settings, gameVersion, dependencies);
        }, this::finishInstall);
    }

    public void installWorldModList(String listId) {
        String normalizedListId = value(listId, "");
        if (normalizedListId.isBlank()) {
            feedback.showToast("Action failed", "The shared list link is missing its id.");
            return;
        }

        settingsController.saveFromFields(false);
        feedback.runAsync("Preparing shared mod list...", () -> apiClient.getWorldModListForInstall(normalizedListId), list -> {
            if (!confirmDuplicateInstall(InstallDuplicateWarning.forWorldModList(
                    settingsController.settings().getInstalledProjects(),
                    list
            ))) {
                return;
            }
            runInstallWithOverlay(
                    "Installing shared mod list...",
                    () -> worldModListInstaller.install(list, settingsController.settings()),
                    this::finishWorldModListInstall
            );
        });
    }

    public void checkUpdates() {
        settingsController.saveFromFields(false);
        feedback.runAsync("Checking updates...", () -> {
            accountController.ensureSignedIn();
            return updateService.checkForUpdates(settingsController.settings());
        }, updates -> {
            currentUpdates = List.copyOf(updates);
            availableUpdates.clear();
            updates.forEach(update -> availableUpdates.put(update.installedProject().projectId(), update));
            renderLibrary();
            renderUpdates();
            feedback.log(updates.isEmpty() ? "No updates available." : updates.size() + " updates available.");
        });
    }

    private Node buildLibraryView() {
        return new LibraryShellView(projectList, projectDetail, this::renderLibrary, this::checkUpdates)
                .build();
    }

    private <T> void runInstallWithOverlay(String status, Supplier<T> work, Consumer<T> onSuccess) {
        showInstallLoadingOverlay(
                value(status, "Installing...").replaceFirst("\\.\\.\\.$", ""),
                "Downloading and installing files. World selection will appear next."
        );
        feedback.runAsync(status, work, result -> {
            try {
                onSuccess.accept(result);
            } catch (RuntimeException ex) {
                hideInstallLoadingOverlay();
                throw ex;
            }
        }, ignored -> hideInstallLoadingOverlay());
    }

    private boolean confirmDuplicateInstall(InstallDuplicateWarning.Result warning) {
        if (warning == null || !warning.hasDuplicates()) {
            return true;
        }
        StatusModal.Result result = StatusModal.builder(overlayHost)
                .type(StatusModal.Type.WARNING)
                .title(warning.count() == 1 ? "Mod already installed" : "Mods already installed")
                .message("Already installed: " + warning.summary()
                        + ". Continuing may replace existing files or add another copy if this install comes from a bundle or modpack.")
                .actionLabel("Install Anyway")
                .actionIcon(LauncherIcons.Glyph.DOWNLOAD)
                .secondaryLabel("Cancel")
                .showAndWait();
        return result == StatusModal.Result.PRIMARY;
    }

    private List<ProjectDependency> dependenciesForSettings(ProjectVersion version, LauncherSettings settings) {
        if (version == null || settings == null || !settings.isIncludeDependencies()) {
            return List.of();
        }
        return version.dependencies();
    }

    private List<ProjectDependency> dependenciesForExistingInstall(
            InstalledProject installed,
            ProjectVersion version,
            LauncherSettings settings
    ) {
        if (installed != null && !installed.bundledProjects().isEmpty()) {
            return installed.bundledProjects().stream()
                    .map(InstalledProjectReference::toDependency)
                    .toList();
        }
        return dependenciesForSettings(version, settings);
    }

    private void showInstallLoadingOverlay(String title, String subtitle) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showInstallLoadingOverlay(title, subtitle));
            return;
        }
        StackPane hostPane = overlayHost.get();
        if (hostPane == null) {
            return;
        }
        if (installLoadingOverlay == null) {
            installLoadingOverlay = installLoadingShell();
            hostPane.getChildren().add(installLoadingOverlay);
        } else if (installLoadingOverlay.getParent() == null) {
            hostPane.getChildren().add(installLoadingOverlay);
        }
        installLoadingTitle.setText(value(title, "Installing"));
        installLoadingSubtitle.setText(value(subtitle, "Preparing the next step."));
        installLoadingOverlay.toFront();
        installLoadingOverlay.requestFocus();
    }

    private StackPane installLoadingShell() {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("install-loading-overlay");
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        shell.setFocusTraversable(true);
        shell.setOnMouseClicked(event -> event.consume());

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("install-loading-spinner");
        spinner.setMaxSize(54, 54);
        spinner.setMinSize(54, 54);

        installLoadingTitle = new Label("Installing");
        installLoadingTitle.getStyleClass().add("install-loading-title");
        installLoadingSubtitle = new Label("Downloading and installing files. World selection will appear next.");
        installLoadingSubtitle.getStyleClass().add("install-loading-subtitle");
        installLoadingSubtitle.setWrapText(true);

        VBox copy = new VBox(6, installLoadingTitle, installLoadingSubtitle);
        copy.getStyleClass().add("install-loading-copy");
        copy.setAlignment(Pos.CENTER);

        VBox card = new VBox(18, spinner, copy);
        card.getStyleClass().add("install-loading-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(420);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        shell.getChildren().add(card);
        return shell;
    }

    private void hideInstallLoadingOverlay() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::hideInstallLoadingOverlay);
            return;
        }
        if (installLoadingOverlay == null) {
            return;
        }
        Parent parent = installLoadingOverlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(installLoadingOverlay);
        }
        installLoadingOverlay = null;
        installLoadingTitle = null;
        installLoadingSubtitle = null;
    }

    private void renderLibrary() {
        try {
            worlds = worldManager.loadWorlds(settingsController.settings());
            installedMods = worldManager.loadInstalledMods(settingsController.settings());
        } catch (RuntimeException ex) {
            worlds = List.of();
            installedMods = List.of();
            feedback.log(ex.getMessage());
        }
        recoverLocalInstalls();
        normalizeBundledDependencyInstalls();
        installedProjects = settingsController.settings().getInstalledProjects().stream()
                .sorted(Comparator
                        .comparing(InstalledProject::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(project -> value(project.title(), project.projectId()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (selectedWorldKey.isBlank() && !worlds.isEmpty()) {
            selectedWorldKey = worldKey(worlds.getFirst());
        }
        if (!selectedWorldKey.isBlank()
                && worlds.stream().noneMatch(world -> worldKey(world).equals(selectedWorldKey))) {
            selectedWorldKey = worlds.isEmpty() ? "" : worldKey(worlds.getFirst());
        }

        renderWorldRows();
        renderWorldDetail();
        ensureProjectMetadataLoaded();
    }

    private void recoverLocalInstalls() {
        LibraryLocalInstallRecovery.RecoveryResult recovery = LibraryLocalInstallRecovery.recover(
                settingsController.settings(),
                settingsController.settings().getInstalledProjects(),
                installedMods
        );
        if (recovery.recoveredCount() <= 0) {
            return;
        }
        settingsController.settings().setInstalledProjects(recovery.projects());
        settingsController.saveCurrentSettings();
        feedback.log("Recovered " + recovery.recoveredCount() + " installed mod"
                + LibraryProjectSupport.plural(recovery.recoveredCount()) + " from the Hytale Mods folder.");
    }

    private void normalizeBundledDependencyInstalls() {
        LibraryBundledInstallNormalizer.Result result = LibraryBundledInstallNormalizer.normalize(
                settingsController.settings().getInstalledProjects(),
                installedMods
        );
        if (result.addedChildren() <= 0) {
            return;
        }
        settingsController.settings().setInstalledProjects(result.projects());
        settingsController.saveCurrentSettings();
        feedback.log("Promoted " + result.addedChildren() + " bundled dependenc"
                + (result.addedChildren() == 1 ? "y" : "ies") + " to installed mod records.");
    }

    private void ensureProjectMetadataLoaded() {
        if (executor == null || apiClient == null) {
            return;
        }
        List<String> missing = metadataProjectIds().stream()
                .filter(id -> !projectMetadata.containsKey(id))
                .filter(loadingMetadataIds::add)
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        CompletableFuture.supplyAsync(() -> apiClient.getProjectMetaBatch(missing), executor)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    Map<String, ProjectMeta> loaded = error == null && result != null ? result : Map.of();
                    for (String id : missing) {
                        projectMetadata.put(id, loaded.getOrDefault(id, fallbackMeta()));
                        loadingMetadataIds.remove(id);
                    }
                    renderWorldDetail();
                }));
    }

    private List<String> metadataProjectIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (InstalledProject project : installedProjects) {
            if (!project.projectId().isBlank()
                    && (project.source().isBlank() || InstalledProject.SOURCE_MODTALE.equalsIgnoreCase(project.source()))) {
                ids.add(project.projectId());
            }
            for (InstalledProjectReference reference : bundledReferences(project)) {
                if (reference.isModtaleProject()) {
                    ids.add(reference.projectId());
                }
            }
        }
        return List.copyOf(ids);
    }

    private ProjectMeta fallbackMeta() {
        return new ProjectMeta("", "", "", "", "", 0, "", "");
    }

    private void renderWorldRows() {
        projectList.getChildren().clear();
        if (worlds.isEmpty()) {
            projectList.getChildren().add(emptyState("No worlds found", "Create a Hytale world, then refresh."));
            return;
        }
        worlds.forEach(world -> projectList.getChildren().add(worldListRenderer.worldRow(
                worldListItem(world),
                worldKey(world).equals(selectedWorldKey)
        )));
    }

    private void selectWorld(HytaleWorld world) {
        selectedWorldKey = worldKey(world);
        renderWorldRows();
        renderWorldDetail();
    }

    private void renderWorldDetail() {
        Optional<HytaleWorld> selected = selectedWorld();
        if (selected.isEmpty()) {
            if (worlds.isEmpty()) {
                projectDetail.getChildren().setAll(emptyState("No worlds found", "Create a Hytale world, then refresh the launcher."));
            } else {
                projectDetail.getChildren().setAll(worldRenderer.worldDetail(null));
            }
            return;
        }
        projectDetail.getChildren().setAll(worldRenderer.worldDetail(worldModel(selected.get())));
    }

    private LibraryWorldModel worldModel(HytaleWorld world) {
        HytaleWorldConfig config = worldManager.loadConfig(world.configPath());
        List<LibraryWorldProjectModel> projects = installedProjects.stream()
                .flatMap(project -> worldProjectModels(project, config).stream())
                .toList();
        int enabledProjects = (int) projects.stream()
                .filter(project -> project.enabledCount() > 0)
                .count();
        return new LibraryWorldModel(
                world,
                LibraryProjectSupport.worldMeta(world),
                enabledProjects,
                projects.size(),
                projects
        );
    }

    private LibraryWorldListItem worldListItem(HytaleWorld world) {
        HytaleWorldConfig config = worldManager.loadConfig(world.configPath());
        int enabledProjects = 0;
        int totalProjects = 0;
        for (InstalledProject project : installedProjects) {
            for (LibraryWorldProjectModel model : worldProjectModels(project, config)) {
                totalProjects++;
                if (model.enabledCount() > 0) {
                    enabledProjects++;
                }
            }
        }
        return new LibraryWorldListItem(
                world,
                LibraryProjectSupport.worldMeta(world),
                enabledProjects,
                totalProjects
        );
    }

    private List<LibraryWorldProjectModel> worldProjectModels(InstalledProject installed, HytaleWorldConfig config) {
        return List.of(worldProjectModel(installed, config));
    }

    private LibraryWorldProjectModel worldProjectModel(InstalledProject installed, HytaleWorldConfig config) {
        List<String> modIds = worldModIds(installed);
        return worldProjectModel(
                installed,
                config,
                modIds,
                installed.isModpack() ? contentItems(installed, config) : List.of(),
                LibraryWorldProjectDisplay.root(installed, projectMetadata.get(installed.projectId()))
        );
    }

    private LibraryWorldProjectModel worldProjectModel(
            InstalledProject installed,
            HytaleWorldConfig config,
            List<String> modIds,
            List<LibraryWorldContentItem> contents,
            LibraryWorldProjectDisplay display
    ) {
        int enabled = enabledCount(config, modIds);
        return new LibraryWorldProjectModel(
                installed,
                projectDetails.get(installed.projectId()),
                projectMetadata.get(installed.projectId()),
                availableUpdates.get(installed.projectId()),
                loadingProjectIds.contains(installed.projectId()),
                modIds,
                enabled,
                modIds.size(),
                contents,
                display,
                isModpackContentsCollapsed(installed)
        );
    }

    private List<String> worldModIds(InstalledProject installed) {
        Set<String> manifestIds = new LinkedHashSet<>();
        Set<String> files = installed.files().stream()
                .map(file -> java.nio.file.Path.of(file).toAbsolutePath().normalize())
                .map(java.nio.file.Path::toString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (HytaleInstalledMod mod : installedMods) {
            String path = mod.file().toAbsolutePath().normalize().toString();
            if (files.contains(path) && mod.id() != null && !mod.id().isBlank()) {
                manifestIds.add(mod.id());
            }
        }
        return manifestIds.isEmpty()
                ? LibraryProjectSupport.projectWorldModIds(installed)
                : List.copyOf(manifestIds);
    }

    private Optional<HytaleWorld> selectedWorld() {
        if (selectedWorldKey == null || selectedWorldKey.isBlank()) {
            return Optional.empty();
        }
        return worlds.stream()
                .filter(world -> worldKey(world).equals(selectedWorldKey))
                .findFirst();
    }

    private List<LibraryWorldContentItem> contentItems(InstalledProject installed, HytaleWorldConfig config) {
        if (!hasUnlockableContents(installed)) {
            return List.of();
        }

        Map<String, HytaleInstalledMod> modsByFile = installedModsByFile(installedMods);
        List<InstalledProjectReference> references = bundledReferences(installed);
        List<LibraryWorldContentItem> items = new ArrayList<>();
        Set<String> seenModIds = new LinkedHashSet<>();
        for (String file : installed.files()) {
            HytaleInstalledMod mod = modsByFile.get(normalizedFileKey(file));
            if (mod == null || mod.id() == null || mod.id().isBlank() || !seenModIds.add(mod.id())) {
                continue;
            }
            items.add(manifestContentItem(mod, matchingReference(mod, file, references), config));
        }
        if (!items.isEmpty()) {
            return items;
        }

        for (InstalledProjectReference reference : references) {
            LibraryWorldContentItem item = referenceContentItem(reference, config);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private LibraryWorldContentItem manifestContentItem(
            HytaleInstalledMod mod,
            InstalledProjectReference reference,
            HytaleWorldConfig config
    ) {
        ProjectMeta meta = reference == null ? null : projectMetadata.get(reference.projectId());
        String author = value(meta == null ? "" : meta.author(), "");
        String title = first(
                meta == null ? "" : meta.title(),
                reference == null ? "" : reference.displayName(),
                mod.name(),
                mod.id()
        );
        String classification = first(
                meta == null ? "" : meta.classification(),
                reference == null ? "" : reference.classification(),
                "PLUGIN"
        );
        String icon = first(meta == null ? "" : meta.icon(), reference == null ? "" : reference.icon());
        List<String> modIds = List.of(mod.id());
        return new LibraryWorldContentItem(
                mod.id(),
                title,
                contentMeta(author, value(mod.version(), ""), mod.id()),
                classification,
                icon,
                author,
                modIds,
                enabledCount(config, modIds),
                modIds.size(),
                true
        );
    }

    private LibraryWorldContentItem referenceContentItem(InstalledProjectReference reference, HytaleWorldConfig config) {
        if (reference == null) {
            return null;
        }
        ProjectMeta meta = projectMetadata.get(reference.projectId());
        String author = value(meta == null ? "" : meta.author(), "");
        String title = first(meta == null ? "" : meta.title(), reference.displayName());
        String classification = first(meta == null ? "" : meta.classification(), reference.classification(), "PLUGIN");
        String icon = first(meta == null ? "" : meta.icon(), reference.icon());
        List<String> modIds = referenceModIds(reference, config);
        return new LibraryWorldContentItem(
                first(reference.projectId(), reference.externalId(), reference.id(), title),
                title,
                contentMeta(author, LibraryProjectSupport.childMeta(reference), modIds.isEmpty() ? "Included in pack" : ""),
                classification,
                icon,
                author,
                modIds,
                enabledCount(config, modIds),
                modIds.size(),
                !modIds.isEmpty()
        );
    }

    private List<String> referenceModIds(InstalledProjectReference reference, HytaleWorldConfig config) {
        if (reference.projectId() != null && !reference.projectId().isBlank()) {
            Optional<InstalledProject> installedChild = installedProjects.stream()
                    .filter(project -> reference.projectId().equals(project.projectId()))
                    .findFirst();
            if (installedChild.isPresent()) {
                return worldModIds(installedChild.get());
            }
        }
        String candidate = LibraryProjectSupport.referenceWorldModId(reference);
        if (candidate.isBlank()) {
            return List.of();
        }
        boolean installed = installedMods.stream()
                .anyMatch(mod -> candidate.equals(mod.id()));
        if (installed || config.enabledByMod().containsKey(candidate)) {
            return List.of(candidate);
        }
        return List.of();
    }

    private InstalledProjectReference matchingReference(
            HytaleInstalledMod mod,
            String file,
            List<InstalledProjectReference> references
    ) {
        if (mod == null || references == null || references.isEmpty()) {
            return null;
        }
        Set<String> modKeys = new LinkedHashSet<>();
        addNormalized(modKeys, mod.id());
        addNormalized(modKeys, mod.name());
        addNormalized(modKeys, fileName(file));
        for (InstalledProjectReference reference : references) {
            Set<String> referenceKeys = new LinkedHashSet<>();
            addNormalized(referenceKeys, LibraryProjectSupport.referenceWorldModId(reference));
            addNormalized(referenceKeys, reference.title());
            addNormalized(referenceKeys, reference.slug());
            addNormalized(referenceKeys, reference.projectId());
            addNormalized(referenceKeys, reference.externalId());
            addNormalized(referenceKeys, reference.externalFileName());
            addNormalized(referenceKeys, fileName(reference.externalFileUrl()));
            addNormalized(referenceKeys, fileName(reference.cachedFileUrl()));
            for (String key : modKeys) {
                if (referenceKeys.contains(key)) {
                    return reference;
                }
            }
        }
        return null;
    }

    private List<InstalledProjectReference> bundledReferences(InstalledProject installed) {
        if (!installed.bundledProjects().isEmpty()) {
            return installed.bundledProjects();
        }
        List<InstalledProjectReference> references = new ArrayList<>();
        installed.dependencyProjectIds().forEach(id -> references.add(new InstalledProjectReference(
                id, id, "", id, "", "", "", "MODTALE", "", "", "", "", "", "", null, null
        )));
        installed.externalDependencies().forEach(id -> references.add(new InstalledProjectReference(
                id, "", "", id, "", "", "", "EXTERNAL", id, "", "", "", "", "", null, null
        )));
        return references;
    }

    private int enabledCount(HytaleWorldConfig config, Collection<String> modIds) {
        if (config == null || modIds == null || modIds.isEmpty()) {
            return 0;
        }
        return (int) modIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .filter(id -> config.enabledByMod().getOrDefault(id, false))
                .count();
    }

    private Map<String, HytaleInstalledMod> installedModsByFile(List<HytaleInstalledMod> mods) {
        Map<String, HytaleInstalledMod> byFile = new LinkedHashMap<>();
        for (HytaleInstalledMod mod : mods) {
            String key = normalizedFileKey(mod.file());
            if (!key.isBlank()) {
                byFile.putIfAbsent(key, mod);
            }
        }
        return byFile;
    }

    private boolean hasUnlockableContents(InstalledProject installed) {
        return installed.isModpack();
    }

    private void updateSelected(UpdateCandidate update) {
        settingsController.saveFromFields(false);
        LauncherSettings settings = settingsController.settings();
        if (!confirmDuplicateInstall(InstallDuplicateWarning.forProject(
                installedProjectsExcept(settings.getInstalledProjects(), update.installedProject()),
                update.project(),
                update.newestVersion(),
                dependenciesForExistingInstall(update.installedProject(), update.newestVersion(), settings),
                true
        ))) {
            return;
        }
        runInstallWithOverlay("Updating " + update.title() + "...", () -> {
            accountController.ensureSignedIn();
            return installer.switchVersionAndRecord(
                    update.installedProject(),
                    update.project(),
                    update.newestVersion(),
                    settings
            );
        }, result -> {
            availableUpdates.remove(update.installedProject().projectId());
            currentUpdates = List.copyOf(availableUpdates.values());
            finishInstall(result);
        });
    }

    private void switchSelectedVersion(InstalledProject installed, ProjectDetail detail, ProjectVersion version) {
        settingsController.saveFromFields(false);
        LauncherSettings settings = settingsController.settings();
        String gameVersion = LibraryProjectSupport.installGameVersion(version, installed, settings.getGameVersion());
        if (!confirmDuplicateInstall(InstallDuplicateWarning.forProject(
                installedProjectsExcept(settings.getInstalledProjects(), installed),
                detail,
                version,
                dependenciesForExistingInstall(installed, version, settings),
                true
        ))) {
            return;
        }
        runInstallWithOverlay("Switching " + installed.title() + " to " + version.versionNumber() + "...", () -> {
            accountController.ensureSignedIn();
            return installer.switchVersionAndRecord(installed, detail, version, settings, gameVersion);
        }, result -> {
            availableUpdates.remove(installed.projectId());
            currentUpdates = List.copyOf(availableUpdates.values());
            finishInstall(result);
        });
    }

    private void uninstallSelected(InstalledProject installed) {
        StatusModal.Result result = StatusModal.builder(overlayHost)
                .type(StatusModal.Type.WARNING)
                .title("Remove Project")
                .message("Remove " + value(installed.title(), "this project")
                        + " from the library and delete its recorded files?")
                .actionLabel("Remove")
                .actionIcon(LauncherIcons.Glyph.TRASH)
                .secondaryLabel("Cancel")
                .showAndWait();
        if (result != StatusModal.Result.PRIMARY) {
            return;
        }
        feedback.runAsync("Removing " + installed.title() + "...", () -> {
            installer.uninstallAndRecord(installed, settingsController.settings());
            return installed;
        }, removed -> {
            availableUpdates.remove(removed.projectId());
            projectDetails.remove(removed.projectId());
            projectMetadata.remove(removed.projectId());
            expandedModpackContents.remove(installedProjectKey(removed));
            settingsController.reloadFromStore();
            renderLibrary();
            renderUpdates();
            accountController.syncLocalSettings();
            feedback.showToast("Removed", removed.title() + " was removed from your library.");
        });
    }

    private void unlockModpack(InstalledProject installed) {
        if (installed == null || !installed.isModpack()) {
            return;
        }
        int contentCount = Math.max(1, LibraryProjectSupport.contentCount(installed));
        StatusModal.Result confirmation = StatusModal.builder(overlayHost)
                .type(StatusModal.Type.WARNING)
                .title("Unlock Modpack")
                .message("Split " + value(installed.title(), "this modpack") + " into "
                        + contentCount + " individually installed mod"
                        + LibraryProjectSupport.plural(contentCount)
                        + "? Updates and version changes will be per mod, not the pack.")
                .actionLabel("Unlock")
                .actionIcon(LauncherIcons.Glyph.EDIT)
                .secondaryLabel("Cancel")
                .showAndWait();
        if (confirmation != StatusModal.Result.PRIMARY) {
            return;
        }

        LibraryModpackUnlockConverter.Result result = LibraryModpackUnlockConverter.convert(
                settingsController.settings().getInstalledProjects(),
                installed,
                installedMods
        );
        if (result.convertedCount() <= 0) {
            StatusModal.builder(overlayHost)
                    .type(StatusModal.Type.ERROR)
                    .title("Could Not Unlock")
                    .message("No installed mod files or modpack contents were found for "
                            + value(installed.title(), "this modpack") + ".")
                    .actionLabel("Close")
                    .showAndWait();
            return;
        }

        settingsController.settings().setInstalledProjects(result.projects());
        settingsController.removeInstalledProjectRecord(installed.projectId());
        settingsController.saveCurrentSettings();
        availableUpdates.remove(installed.projectId());
        currentUpdates = List.copyOf(availableUpdates.values());
        projectDetails.remove(installed.projectId());
        projectMetadata.remove(installed.projectId());
        expandedModpackContents.remove(installedProjectKey(installed));
        accountController.syncLocalSettings();
        feedback.log("Unlocked " + installed.title() + " into " + result.convertedCount()
                + " individual installed mod" + LibraryProjectSupport.plural(result.convertedCount()) + ".");
        feedback.showToast("Modpack unlocked", "Converted " + value(installed.title(), "the modpack")
                + " into individual installed mods.");
        renderLibrary();
        renderUpdates();
    }

    private void toggleModpackContents(InstalledProject installed) {
        if (installed == null || !installed.isModpack()) {
            return;
        }
        String key = installedProjectKey(installed);
        if (key.isBlank()) {
            return;
        }
        if (!expandedModpackContents.add(key)) {
            expandedModpackContents.remove(key);
        }
        renderWorldDetail();
    }

    private boolean isModpackContentsCollapsed(InstalledProject installed) {
        return installed != null && !expandedModpackContents.contains(installedProjectKey(installed));
    }

    private void shareWorldSnapshot(HytaleWorld world) {
        settingsController.saveFromFields(false);
        feedback.runAsync("Creating " + world.name() + " share link...", () -> {
            accountController.ensureSignedIn();
            return apiClient.createWorldModList(snapshotRequest(world));
        }, list -> {
            copyShareUrl(list.shareUrl());
            String message = "Copied share link for " + list.title() + ".";
            feedback.log(message + " " + list.shareUrl());
            feedback.showToast("Share link copied", message);
        });
    }

    private void createModpackFromWorld(HytaleWorld world) {
        settingsController.saveFromFields(false);
        feedback.runAsync("Preparing " + world.name() + " modpack starter...", () -> {
            accountController.ensureSignedIn();
            return apiClient.createWorldModList(snapshotRequest(world));
        }, list -> {
            String target = "/upload?type=MODPACK&fromList=" + encodeQuery(list.id());
            LauncherExternalLinks.open(target, feedback::showToast);
            feedback.log("Opening Modtale to start a modpack from " + world.name() + ".");
        });
    }

    private void finishInstall(InstallResult result) {
        settingsController.reloadFromStore();
        String message = "Installed " + result.installedProject().title() + " "
                + result.installedProject().installedVersion() + " (" + result.installedFiles().size() + " files).";
        feedback.log(message);
        feedback.showToast("Installed", result.warnings().isEmpty()
                ? message
                : message + " Warnings: " + String.join(" ", result.warnings()));
        renderLibrary();
        renderUpdates();
        accountController.syncLocalSettings();
        showPostDownloadWorldModal(
                value(result.installedProject().title(), "Installed project"),
                modIdsForFiles(result.installedFiles())
        );
    }

    private void finishWorldModListInstall(WorldModListInstallResult result) {
        WorldModList list = result.list();
        renderLibrary();
        String title = list.title().isBlank() ? "shared mod list" : list.title();
        String message = "Installed " + result.installedFiles().size() + " file"
                + LibraryProjectSupport.plural(result.installedFiles().size()) + " from " + title + ".";
        feedback.log(message);
        feedback.showToast("Installed", message);
        showPostDownloadWorldModal(title, modIdsForFiles(result.installedFiles()));
    }

    private boolean showPostDownloadWorldModal(String title, List<String> modIds) {
        List<String> ids = modIds == null
                ? List.of()
                : modIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (ids.isEmpty() || worlds.isEmpty()) {
            hideInstallLoadingOverlay();
            return false;
        }
        hideInstallLoadingOverlay();
        return postDownloadWorldModal.show(title, ids, postDownloadWorldOptions(ids));
    }

    private List<PostDownloadWorldModal.WorldOption> postDownloadWorldOptions(List<String> modIds) {
        return worlds.stream()
                .map(world -> {
                    HytaleWorldConfig config = worldManager.loadConfig(world.configPath());
                    int enabled = enabledCount(config, modIds);
                    return new PostDownloadWorldModal.WorldOption(
                            world,
                            LibraryProjectSupport.worldMeta(world),
                            enabled,
                            modIds.size(),
                            enabled > 0,
                            enabled > 0 && enabled < modIds.size()
                    );
                })
                .toList();
    }

    private void applyPostDownloadWorldSelection(PostDownloadWorldModal.Selection selection) {
        if (selection == null || selection.worlds().isEmpty() || selection.modIds().isEmpty()) {
            return;
        }
        feedback.runAsync("Enabling install in selected worlds...", () -> {
            for (HytaleWorld world : selection.worlds()) {
                worldManager.setModsEnabled(world.configPath(), selection.modIds(), true);
            }
            return worldManager.loadWorlds(settingsController.settings());
        }, loadedWorlds -> {
            worlds = loadedWorlds;
            renderWorldRows();
            renderWorldDetail();
            feedback.log("Enabled " + selection.modIds().size() + " mod" + LibraryProjectSupport.plural(selection.modIds().size())
                    + " in " + selection.worlds().size() + " world" + LibraryProjectSupport.plural(selection.worlds().size()) + ".");
            feedback.showToast("Worlds updated", "Enabled the install in selected worlds.");
        });
    }

    private List<String> modIdsForFiles(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        Set<String> fileKeys = files.stream()
                .map(LauncherLibraryController::normalizedFileKey)
                .filter(key -> !key.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (fileKeys.isEmpty()) {
            return List.of();
        }
        List<HytaleInstalledMod> mods = installedMods.isEmpty()
                ? worldManager.loadInstalledMods(settingsController.settings())
                : installedMods;
        return mods.stream()
                .filter(mod -> fileKeys.contains(normalizedFileKey(mod.file())))
                .map(HytaleInstalledMod::id)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private void ensureProjectDetailLoaded(InstalledProject installed) {
        if (installed == null
                || !LibraryProjectSupport.isModtaleProject(installed)
                || projectDetails.containsKey(installed.projectId())
                || loadingProjectIds.contains(installed.projectId())) {
            return;
        }
        loadingProjectIds.add(installed.projectId());
        renderWorldDetail();
        feedback.runAsync("Loading " + installed.title() + " releases...", () -> {
            accountController.ensureSignedIn();
            return detailWithVersions(LibraryProjectSupport.routeKey(installed));
        }, detail -> {
            loadingProjectIds.remove(installed.projectId());
            projectDetails.put(installed.projectId(), detail);
            renderWorldDetail();
        }, error -> {
            loadingProjectIds.remove(installed.projectId());
            renderWorldDetail();
        });
    }

    private ProjectDetail detailWithVersions(String routeKey) {
        ProjectDetail project = apiClient.getProject(routeKey);
        try {
            List<ProjectVersion> versions = apiClient.getProjectVersions(project.routeKey());
            if (!versions.isEmpty() || project.versions().isEmpty()) {
                project = project.withVersions(versions);
            }
        } catch (RuntimeException error) {
            if (project.versions().isEmpty()) {
                throw error;
            }
        }
        return project;
    }

    private void setModsEnabled(HytaleWorld world, Collection<String> modIds, boolean enabled) {
        List<String> ids = modIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        feedback.runAsync("Updating " + world.name() + "...", () -> {
            worldManager.setModsEnabled(world.configPath(), ids, enabled);
            return worldManager.loadWorlds(settingsController.settings());
        }, loadedWorlds -> {
            worlds = loadedWorlds;
            renderWorldRows();
            renderWorldDetail();
            feedback.log((enabled ? "Enabled " : "Disabled ") + ids.size() + " mod" + LibraryProjectSupport.plural(ids.size())
                    + " for " + world.name() + ".");
        });
    }

    private CreateWorldModListRequest snapshotRequest(HytaleWorld world) {
        HytaleWorldConfig config = worldManager.loadConfig(world.configPath());
        Set<String> enabledModIds = config.enabledByMod().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (enabledModIds.isEmpty()) {
            throw new ModtaleApiException(world.name() + " does not have any enabled mods to share.");
        }

        List<HytaleInstalledMod> availableMods = installedMods.isEmpty()
                ? worldManager.loadInstalledMods(settingsController.settings())
                : installedMods;
        List<CreateWorldModListRequest.Item> items = LibraryWorldSnapshotMapper.itemsFor(
                enabledModIds,
                installedProjects,
                availableMods
        );

        return new CreateWorldModListRequest(
                world.name() + " mod list",
                world.name(),
                settingsController.settings().getGameVersion(),
                items
        );
    }

    private void copyShareUrl(String shareUrl) {
        if (shareUrl == null || shareUrl.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(shareUrl);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static String worldKey(HytaleWorld world) {
        return world == null || world.directory() == null
                ? ""
                : world.directory().toAbsolutePath().normalize().toString();
    }

    private static String installedProjectKey(InstalledProject installed) {
        return installed == null
                ? ""
                : first(installed.projectId(), installed.slug(), installed.title());
    }

    static List<InstalledProject> installedProjectsExcept(
            List<InstalledProject> projects,
            InstalledProject excluded
    ) {
        if (projects == null || projects.isEmpty() || excluded == null) {
            return projects == null ? List.of() : projects;
        }
        return projects.stream()
                .filter(project -> !sameInstalledProject(project, excluded))
                .toList();
    }

    private static boolean sameInstalledProject(InstalledProject left, InstalledProject right) {
        if (left == null || right == null) {
            return false;
        }
        if (!left.projectId().isBlank() && left.projectId().equals(right.projectId())) {
            return true;
        }
        if (!left.slug().isBlank() && left.slug().equals(right.slug())) {
            return true;
        }
        return left.equals(right);
    }

    private static String contentMeta(String author, String... values) {
        List<String> parts = new ArrayList<>();
        if (author != null && !author.isBlank()) {
            parts.add("by " + author.trim());
        }
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    String normalized = value.trim();
                    if (!parts.contains(normalized)) {
                        parts.add(normalized);
                    }
                }
            }
        }
        return parts.isEmpty() ? "Included in pack" : String.join(" - ", parts);
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static void addNormalized(Set<String> keys, String value) {
        String normalized = normalizedNameKey(value);
        if (!normalized.isBlank()) {
            keys.add(normalized);
        }
    }

    private static String fileName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        int query = normalized.indexOf('?');
        if (query >= 0) {
            normalized = normalized.substring(0, query);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String normalizedNameKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceFirst("(?i)\\.(jar|zip|hytale)$", "");
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private static String normalizedFileKey(String file) {
        if (file == null || file.isBlank()) {
            return "";
        }
        return normalizedFileKey(Path.of(file));
    }

    private static String normalizedFileKey(Path file) {
        if (file == null) {
            return "";
        }
        return file.toAbsolutePath().normalize().toString();
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void renderUpdates() {
        updatesList.getChildren().clear();
        if (currentUpdates.isEmpty()) {
            updatesList.getChildren().add(emptyState("No updates queued", "Run an update check to compare your library with the catalog."));
            return;
        }
        currentUpdates.forEach(update -> updatesList.getChildren().add(listRenderer.updateRow(update)));
    }
}
