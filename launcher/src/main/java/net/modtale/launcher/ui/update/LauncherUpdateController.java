package net.modtale.launcher.ui.update;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.common.StatusModal;
import net.modtale.launcher.ui.common.TransferLoadingModal;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.library.LibraryToggleBox;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import net.modtale.launcher.update.LauncherUpdateCandidate;
import net.modtale.launcher.update.LauncherUpdateService;
import net.modtale.launcher.update.LauncherVersion;

public final class LauncherUpdateController {

    private final LauncherUpdateService updateService;
    private final LauncherSettingsController settingsController;
    private final LauncherFeedback feedback;
    private final Executor executor;
    private final Supplier<StackPane> overlayHost;
    private final Runnable exitLauncher;

    private boolean checkInFlight;
    private boolean updateInFlight;
    private String observedChannel;
    private boolean pendingManualCheck;

    public LauncherUpdateController(
            LauncherUpdateService updateService,
            LauncherSettingsController settingsController,
            LauncherFeedback feedback,
            Executor executor,
            Supplier<StackPane> overlayHost
    ) {
        this(updateService, settingsController, feedback, executor, overlayHost, Platform::exit);
    }

    LauncherUpdateController(LauncherUpdateService updateService, LauncherSettingsController settingsController,
            LauncherFeedback feedback, Executor executor, Supplier<StackPane> overlayHost, Runnable exitLauncher) {
        this.exitLauncher = exitLauncher;
        this.updateService = updateService;
        this.settingsController = settingsController;
        this.feedback = feedback;
        this.executor = executor;
        this.overlayHost = overlayHost;
        observedChannel = settingsController.settings().getLauncherChannel();
        settingsController.addSaveListener(() -> {
            String channel = settingsController.settings().getLauncherChannel();
            if (!channel.equals(observedChannel)) {
                observedChannel = channel;
                settingsController.setLauncherUpdateStatus("Checking " + channel + " channel...");
                checkForUpdates(false);
            }
        });
    }

    public void checkOnStartup() {
        var failure = updateService.consumeUpdateFailure();
        if (failure.isPresent()) {
            settingsController.setLauncherUpdateStatus("Update failed. The previous version has been restored.");
            feedback.showToast("Launcher update failed", failure.get());
            return;
        }
        checkForUpdates(false);
    }

    public void checkManually() {
        settingsController.saveFromFields(false);
        checkForUpdates(true);
    }

    private void checkForUpdates(boolean manual) {
        if (updateInFlight) return;
        if (!updateService.canInstallUpdates()) {
            String message = "Updates are available in the installed launcher. This development session is updated by rebuilding the project.";
            settingsController.setLauncherUpdateStatus(message);
            if (manual) feedback.showToast("Development launcher", message);
            return;
        }
        if (checkInFlight) {
            pendingManualCheck |= manual;
            return;
        }
        checkInFlight = true;
        if (manual) {
            feedback.log("Checking launcher updates...");
        }

        String currentVersion = LauncherVersion.current();
        String channel = settingsController.settings().getLauncherChannel();
        settingsController.setLauncherUpdateStatus("Checking " + channel + " channel...");
        CompletableFuture.supplyAsync(() -> updateService.latestUpdate(currentVersion, channel), executor)
                .whenComplete((update, error) -> Platform.runLater(() -> {
                    checkInFlight = false;
                    if (!channel.equals(settingsController.settings().getLauncherChannel())) {
                        boolean retryManually = pendingManualCheck;
                        pendingManualCheck = false;
                        checkForUpdates(retryManually);
                        return;
                    }
                    boolean announce = manual || pendingManualCheck;
                    pendingManualCheck = false;
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        settingsController.setLauncherUpdateStatus("Could not check " + channel + " updates. Try again.");
                        feedback.log("Launcher update check failed: " + cause.getMessage());
                        if (announce) {
                            feedback.showToast("Launcher update check failed", cause.getMessage());
                        }
                        return;
                    }

                    if (update.isEmpty() || !update.get().hasInstallerAsset()) {
                        settingsController.setLauncherUpdateStatus("No automatic update available on " + channel + ". Current version: " + currentVersion + ".");
                        if (announce) {
                            feedback.log("No automatic launcher update available on " + channel + ".");
                            feedback.showToast("No automatic launcher update available", "Channel: " + channel + ". Current version: " + currentVersion + ".");
                        }
                        return;
                    }

                    settingsController.setLauncherUpdateStatus("Update available on " + channel + ": " + update.get().displayVersion());
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
        CheckBox autoUpdates = new LibraryToggleBox();
        autoUpdates.setAccessibleText("Enable launcher auto-updates");
        autoUpdates.setSelected(settingsController.settings().isLauncherAutoUpdates());

        Label toggleTitle = new Label("Automatic updates");
        toggleTitle.getStyleClass().add("launcher-update-toggle-title");
        toggleTitle.setLabelFor(autoUpdates);
        Label toggleDescription = new Label("Install future launcher updates automatically.");
        toggleDescription.getStyleClass().add("launcher-update-toggle-description");
        toggleDescription.setWrapText(true);
        VBox toggleCopy = new VBox(3, toggleTitle, toggleDescription);
        HBox.setHgrow(toggleCopy, Priority.ALWAYS);

        HBox toggleRow = new HBox(16, toggleCopy, autoUpdates);
        toggleRow.getStyleClass().add("launcher-update-toggle-row");
        toggleRow.setAlignment(Pos.CENTER_LEFT);
        toggleRow.setPrefWidth(400);
        toggleRow.setOnMouseClicked(event -> {
            if (event.getTarget() != autoUpdates) autoUpdates.fire();
        });

        StatusModal.Result result = StatusModal.builder(overlayHost)
                .type(StatusModal.Type.INFO)
                .title("Launcher Update Available")
                .message("Modtale Launcher " + update.displayVersion() + " is available.\n"
                        + "Current version: " + currentVersion + ".\n\n"
                        + updateService.installationMessage())
                .actionLabel("Update Launcher")
                .secondaryLabel("Later")
                .content(toggleRow)
                .showAndWait();
        boolean autoUpdatesChanged = settingsController.settings().isLauncherAutoUpdates() != autoUpdates.isSelected();
        if (autoUpdatesChanged) {
            settingsController.settings().setLauncherAutoUpdates(autoUpdates.isSelected());
            settingsController.saveCurrentSettings();
            settingsController.reloadControls();
        }
        if (result == StatusModal.Result.PRIMARY) {
            installUpdate(update);
        }
    }

    private void installUpdate(LauncherUpdateCandidate update) {
        if (updateInFlight) return;
        updateInFlight = true;
        var progress = new TransferLoadingModal("Updating Modtale Launcher",
                "Downloading version " + update.displayVersion() + "...");
        StackPane host = overlayHost.get();
        if (host != null) host.getChildren().add(progress);
        settingsController.setLauncherUpdateStatus("Downloading " + update.displayVersion() + "...");
        feedback.runAsync("Downloading launcher " + update.displayVersion() + "...", () -> {
            Path installer = updateService.downloadInstaller(update,
                    fraction -> progress.update("Updating Modtale Launcher", "Downloading launcher update", fraction));
            progress.update("Updating Modtale Launcher", "Installing launcher update");
            updateService.installUpdate(installer, update);
            return installer;
        }, installer -> {
            progress.dismiss();
            exitLauncher.run();
        }, error -> {
            progress.dismiss();
            updateInFlight = false;
            settingsController.setLauncherUpdateStatus("Update failed. Check for updates to try again.");
        });
    }
}
