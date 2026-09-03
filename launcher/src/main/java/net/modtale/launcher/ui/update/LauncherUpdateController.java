package net.modtale.launcher.ui.update;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import net.modtale.launcher.update.LauncherUpdateCandidate;
import net.modtale.launcher.update.LauncherUpdateService;
import net.modtale.launcher.update.LauncherVersion;

public final class LauncherUpdateController {

    private final LauncherUpdateService updateService;
    private final LauncherSettingsController settingsController;
    private final LauncherFeedback feedback;
    private final Executor executor;
    private final Supplier<Stage> stage;

    private boolean checkInFlight;

    public LauncherUpdateController(
            LauncherUpdateService updateService,
            LauncherSettingsController settingsController,
            LauncherFeedback feedback,
            Executor executor,
            Supplier<Stage> stage
    ) {
        this.updateService = updateService;
        this.settingsController = settingsController;
        this.feedback = feedback;
        this.executor = executor;
        this.stage = stage;
    }

    public void checkOnStartup() {
        checkForUpdates(false);
    }

    public void checkManually() {
        settingsController.saveFromFields(false);
        checkForUpdates(true);
    }

    private void checkForUpdates(boolean manual) {
        if (checkInFlight) {
            if (manual) {
                feedback.showToast("Launcher update check", "A launcher update check is already running.");
            }
            return;
        }
        checkInFlight = true;
        if (manual) {
            feedback.log("Checking launcher updates...");
        }

        String currentVersion = LauncherVersion.current();
        CompletableFuture.supplyAsync(() -> updateService.latestUpdate(currentVersion), executor)
                .whenComplete((update, error) -> Platform.runLater(() -> {
                    checkInFlight = false;
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        feedback.log("Launcher update check failed: " + cause.getMessage());
                        if (manual) {
                            feedback.showToast("Launcher update check failed", cause.getMessage());
                        }
                        return;
                    }

                    if (update.isEmpty()) {
                        if (manual) {
                            feedback.log("Launcher is up to date.");
                            feedback.showToast("Launcher is up to date", "You are running Modtale Launcher " + currentVersion + ".");
                        }
                        return;
                    }

                    handleUpdate(update.get(), currentVersion);
                }));
    }

    private void handleUpdate(LauncherUpdateCandidate update, String currentVersion) {
        if (settingsController.settings().isLauncherAutoUpdates() && update.hasInstallerAsset()) {
            feedback.log("Launcher " + update.displayVersion() + " is available; auto-update is enabled.");
            installUpdate(update);
            return;
        }
        promptForUpdate(update, currentVersion);
    }

    private void promptForUpdate(LauncherUpdateCandidate update, String currentVersion) {
        Dialog<ButtonType> dialog = new Dialog<>();
        Stage owner = stage.get();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Launcher Update Available");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("launcher-update-dialog");
        if (owner != null && owner.getScene() != null) {
            pane.getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        ButtonType updateButton = new ButtonType(
                update.hasInstallerAsset() ? "Update Launcher" : "Open Release",
                ButtonBar.ButtonData.OK_DONE
        );
        ButtonType laterButton = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(updateButton, laterButton);

        Label title = new Label("Modtale Launcher " + update.displayVersion() + " is available.");
        title.getStyleClass().add("dialog-title");
        title.setWrapText(true);
        Label body = new Label("Current version: " + currentVersion + ". "
                + (update.hasInstallerAsset()
                ? "The matching installer can be downloaded and opened now."
                : "No installer asset matched this OS, but the release page can be opened."));
        body.getStyleClass().add("dialog-body");
        body.setWrapText(true);

        CheckBox autoUpdates = new CheckBox("Enable launcher auto-updates");
        autoUpdates.getStyleClass().add("native-check");
        autoUpdates.setSelected(settingsController.settings().isLauncherAutoUpdates());

        VBox content = new VBox(12, title, body, autoUpdates);
        content.setMaxWidth(460);
        pane.setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();
        boolean autoUpdatesChanged = settingsController.settings().isLauncherAutoUpdates() != autoUpdates.isSelected();
        if (autoUpdatesChanged) {
            settingsController.settings().setLauncherAutoUpdates(autoUpdates.isSelected());
            settingsController.saveCurrentSettings();
            settingsController.reloadControls();
        }
        if (result.isPresent() && result.get() == updateButton) {
            installUpdate(update);
        }
    }

    private void installUpdate(LauncherUpdateCandidate update) {
        if (!update.hasInstallerAsset()) {
            feedback.runAsync("Opening launcher release page...", () -> {
                updateService.openReleasePage(update);
                return update;
            }, opened -> feedback.log("Opened launcher release page for " + opened.displayVersion() + "."));
            return;
        }

        feedback.runAsync("Downloading launcher " + update.displayVersion() + "...", () -> {
            Path installer = updateService.downloadInstaller(update);
            updateService.openInstaller(installer);
            return installer;
        }, installer -> {
            String filename = installer.getFileName().toString();
            feedback.log("Launcher installer opened: " + filename + ".");
            feedback.showToast("Launcher update ready", "Close Modtale Launcher after the installer opens to finish updating.");
        });
    }
}
