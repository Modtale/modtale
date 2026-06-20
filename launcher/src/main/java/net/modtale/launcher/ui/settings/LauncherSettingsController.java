package net.modtale.launcher.ui.settings;

import static net.modtale.launcher.ui.common.LauncherUi.addField;
import static net.modtale.launcher.ui.common.LauncherUi.formGrid;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.toggleCard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.cache.LauncherCacheService;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;

public final class LauncherSettingsController {

    private final SettingsStore settingsStore;
    private final ModtaleApiClient apiClient;
    private final Supplier<Stage> stage;
    private final Supplier<LauncherView> currentView;
    private final LauncherSettingsForm form = new LauncherSettingsForm();
    private final List<Runnable> refreshListeners = new ArrayList<>();
    private final List<Runnable> saveListeners = new ArrayList<>();

    private LauncherFeedback feedback;
    private Runnable launcherUpdateCheckAction;
    private Supplier<LauncherCacheService.ClearResult> clearCacheAction;
    private LauncherSettings settings;
    private Node view;

    public LauncherSettingsController(
            SettingsStore settingsStore,
            ModtaleApiClient apiClient,
            Supplier<Stage> stage,
            Supplier<LauncherView> currentView
    ) {
        this.settingsStore = settingsStore;
        this.apiClient = apiClient;
        this.stage = stage;
        this.currentView = currentView;
        settings = settingsStore.load();
    }

    public void attachFeedback(LauncherFeedback feedback) {
        this.feedback = feedback;
    }

    public void addRefreshListener(Runnable listener) {
        refreshListeners.add(listener);
    }

    public void addSaveListener(Runnable listener) {
        if (listener != null) {
            saveListeners.add(listener);
        }
    }

    public void setLauncherUpdateCheckAction(Runnable launcherUpdateCheckAction) {
        this.launcherUpdateCheckAction = launcherUpdateCheckAction;
    }

    public void setClearCacheAction(Supplier<LauncherCacheService.ClearResult> clearCacheAction) {
        this.clearCacheAction = clearCacheAction;
    }

    public LauncherSettings settings() {
        return settings;
    }

    public LauncherSettingsForm form() {
        return form;
    }

    public String gameVersion() {
        return settings.getGameVersion();
    }

    public Node view() {
        if (view == null) {
            view = buildView();
        }
        return view;
    }

    public void applyFromFields() {
        form.applyTo(settings, currentView.get(), apiClient);
    }

    public void saveFromFields(boolean announce) {
        applyFromFields();
        settingsStore.save(settings);
        notifySaveListeners();
        if (announce && feedback != null) {
            feedback.log("Settings saved.");
            feedback.showToast("Settings saved", "Launcher preferences were updated.");
        }
        if (announce) {
            notifyRefreshListeners();
        }
    }

    public void saveCurrentSettings() {
        settingsStore.save(settings);
        notifySaveListeners();
    }

    public void removeInstalledProjectRecord(String projectId) {
        settingsStore.removeInstalledProject(projectId);
    }

    public void reloadFromStore() {
        settings = settingsStore.load();
        reloadControls();
    }

    public void reloadControls() {
        form.reloadFrom(settings);
        notifyRefreshListeners();
    }

    public void selectConfiguredHytaleBuild() {
        form.selectConfiguredHytaleBuild(settings);
    }

    private Node buildView() {
        VBox root = new VBox(18);
        root.setUserData(LauncherView.SETTINGS);
        root.getStyleClass().addAll("view", "settings-view");
        root.getChildren().addAll(runtimePathsSection(), libraryDefaultsSection(), maintenanceSection(), saveActions());
        return root;
    }

    private Node saveActions() {
        HBox actions = new HBox();
        actions.getStyleClass().add("settings-save-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button save = primaryButton("Save Settings");
        save.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.SAVE, 14));
        save.setOnAction(event -> saveFromFields(true));
        actions.getChildren().add(save);
        return actions;
    }

    private Node runtimePathsSection() {
        VBox runtime = settingsSection("Runtime & Mod Paths", "Folders used by Play, Library, and mod installs.",
                LauncherIcons.Glyph.GEAR);
        GridPane runtimeGrid = settingsGrid();
        addField(runtimeGrid, 0, "Installed mods",
                LauncherPathControls.pathRow(stage, form.modsPathField(), "hytaleModsPath", true, true));
        addField(runtimeGrid, 1, "Game install",
                LauncherPathControls.pathRow(stage, form.hytaleGamePathField(), "hytaleGamePath", true, false));
        addField(runtimeGrid, 2, "User data",
                LauncherPathControls.pathRow(stage, form.hytaleUserDataPathField(), "hytaleUserDataPath", true, false));
        addField(runtimeGrid, 3, "Java executable",
                LauncherPathControls.pathRow(stage, form.hytaleJavaPathField(), "hytaleJavaPath", false, false));
        runtime.getChildren().add(runtimeGrid);
        return runtime;
    }

    private Node libraryDefaultsSection() {
        VBox defaults = settingsSection("Library Defaults", "Version target and dependency behavior for new downloads.",
                LauncherIcons.Glyph.BOX);
        GridPane grid = settingsGrid();
        addField(grid, 0, "Game version", form.gameVersionField());
        HBox toggles = new HBox(12,
                toggleCard(form.includeDependenciesCheck()),
                toggleCard(form.includeOptionalCheck()),
                toggleCard(form.autoUpdatesCheck()));
        toggles.getStyleClass().add("settings-toggle-row");
        addField(grid, 1, "Project defaults", toggles);
        defaults.getChildren().add(grid);
        return defaults;
    }

    private Node maintenanceSection() {
        VBox maintenance = settingsSection("Launcher Maintenance", "Updates, cached artwork, and local launcher data.",
                LauncherIcons.Glyph.DATABASE);
        HBox cards = new HBox(12);
        cards.getStyleClass().add("settings-card-row");

        VBox launcherUpdates = settingsActionCard("Launcher updates", "Native launcher version checks.",
                LauncherIcons.Glyph.DOWNLOAD);
        HBox launcherToggles = new HBox(12, toggleCard(form.launcherAutoUpdatesCheck()));
        launcherToggles.getStyleClass().add("settings-toggle-row");
        Button checkLauncher = secondaryButton("Check Now");
        checkLauncher.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.REFRESH_CW, 14));
        checkLauncher.setOnAction(event -> {
            saveFromFields(false);
            if (launcherUpdateCheckAction != null) {
                launcherUpdateCheckAction.run();
            }
        });
        launcherUpdates.getChildren().addAll(launcherToggles, checkLauncher);

        VBox cache = settingsActionCard("Cache", "API responses and project artwork.",
                LauncherIcons.Glyph.DATABASE);
        Button clearCache = secondaryButton("Clear Cache");
        clearCache.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DATABASE, 14));
        clearCache.setOnAction(event -> clearCache(clearCache));
        cache.getChildren().add(clearCache);

        cards.getChildren().addAll(launcherUpdates, cache);
        HBox.setHgrow(launcherUpdates, Priority.ALWAYS);
        HBox.setHgrow(cache, Priority.ALWAYS);
        maintenance.getChildren().add(cards);
        return maintenance;
    }

    private VBox settingsSection(String title, String subtitle, LauncherIcons.Glyph glyph) {
        VBox section = new VBox(16);
        section.getStyleClass().add("settings-section");
        HBox header = new HBox(12);
        header.getStyleClass().add("settings-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = settingsIcon(glyph);
        VBox copy = new VBox(4);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-section-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("settings-section-subtitle");
        copy.getChildren().addAll(titleLabel, subtitleLabel);
        header.getChildren().addAll(icon, copy);
        section.getChildren().add(header);
        return section;
    }

    private VBox settingsActionCard(String title, String subtitle, LauncherIcons.Glyph glyph) {
        VBox card = new VBox(12);
        card.getStyleClass().add("settings-action-card");
        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = settingsIcon(glyph);
        VBox copy = new VBox(3);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-card-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("settings-card-subtitle");
        copy.getChildren().addAll(titleLabel, subtitleLabel);
        heading.getChildren().addAll(icon, copy);
        card.getChildren().add(heading);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private GridPane settingsGrid() {
        GridPane grid = formGrid();
        grid.getStyleClass().add("settings-form-grid");
        return grid;
    }

    private StackPane settingsIcon(LauncherIcons.Glyph glyph) {
        StackPane icon = new StackPane(LauncherIcons.icon(glyph, 17));
        icon.getStyleClass().add("settings-icon");
        return icon;
    }

    private void clearCache(Button button) {
        if (clearCacheAction == null) {
            return;
        }
        button.setDisable(true);
        if (feedback == null) {
            try {
                clearCacheAction.get();
            } finally {
                button.setDisable(false);
            }
            return;
        }
        feedback.runAsync("Clearing launcher cache...", clearCacheAction, result -> {
            button.setDisable(false);
            String message = cacheClearedMessage(result);
            feedback.log(message);
            feedback.showToast("Cache cleared", message);
            notifyRefreshListeners();
        }, error -> button.setDisable(false));
    }

    private String cacheClearedMessage(LauncherCacheService.ClearResult result) {
        int count = result == null ? 0 : result.deletedEntries();
        if (count == 0) {
            return "Launcher cache is clear.";
        }
        return "Cleared " + count + " cached launcher item" + (count == 1 ? "" : "s") + ".";
    }

    private void notifyRefreshListeners() {
        refreshListeners.forEach(Runnable::run);
    }

    private void notifySaveListeners() {
        saveListeners.forEach(Runnable::run);
    }
}
