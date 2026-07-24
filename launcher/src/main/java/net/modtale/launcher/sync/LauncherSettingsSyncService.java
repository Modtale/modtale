package net.modtale.launcher.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.install.ModInstaller;
import net.modtale.launcher.install.VersionSelector;
import net.modtale.launcher.model.install.InstallOptions;
import net.modtale.launcher.model.install.InstallResult;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.sync.LauncherSettingsSnapshot;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LauncherSettingsSyncService {

    private static final Logger LOG = LogManager.getLogger(LauncherSettingsSyncService.class);

    private final ModtaleApiClient apiClient;
    private final SettingsStore settingsStore;
    private final LauncherSettingsController settingsController;
    private final ModInstaller installer;
    private final LauncherFeedback feedback;
    private final BooleanSupplier signedIn;
    private final Supplier<StackPane> overlayHost;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean uploading = new AtomicBoolean();

    private volatile String lastKnownRemoteHash = "";
    private volatile String lastKnownRemoteInstalledProjectsHash = "";

    public LauncherSettingsSyncService(
            ModtaleApiClient apiClient,
            SettingsStore settingsStore,
            LauncherSettingsController settingsController,
            ModInstaller installer,
            LauncherFeedback feedback,
            BooleanSupplier signedIn,
            Supplier<StackPane> overlayHost
    ) {
        this.apiClient = apiClient;
        this.settingsStore = settingsStore;
        this.settingsController = settingsController;
        this.installer = installer;
        this.feedback = feedback;
        this.signedIn = signedIn == null ? () -> false : signedIn;
        this.overlayHost = overlayHost == null ? () -> null : overlayHost;
    }

    public void checkOnSignIn() {
        if (!signedIn.getAsBoolean() || !checking.compareAndSet(false, true)) {
            return;
        }
        feedback.runAsync("Checking launcher preferences...",
                apiClient::getLauncherSettings,
                remote -> {
                    checking.set(false);
                    handleRemoteSnapshot(remote);
                },
                error -> checking.set(false));
    }

    public void syncAfterLocalChange() {
        if (!signedIn.getAsBoolean() || checking.get()) {
            return;
        }
        LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settingsController.settings());
        String localHash = local.computeHash();
        if (localHash.equals(lastKnownRemoteHash)) {
            return;
        }
        if (canUploadPreferencesOnly(local)) {
            uploadPreferences(local, false);
        } else {
            uploadSnapshot(local, false);
        }
    }

    private void handleRemoteSnapshot(LauncherSettingsSnapshot remote) {
        LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settingsController.settings());
        if (remote == null || !remote.hasSyncedContent()) {
            uploadSnapshot(local, false);
            return;
        }

        String localHash = local.computeHash();
        if (hashMatches(remote, localHash)) {
            lastKnownRemoteHash = localHash;
            lastKnownRemoteInstalledProjectsHash = local.installedProjectsHash();
            return;
        }

        lastKnownRemoteHash = remote.effectiveHash();
        lastKnownRemoteInstalledProjectsHash = remote.installedProjectsHash();
        if (promptLoadRemote(remote, local)) {
            restoreSnapshot(remote);
        } else {
            uploadSnapshot(local, true);
        }
    }

    private boolean promptLoadRemote(LauncherSettingsSnapshot remote, LauncherSettingsSnapshot local) {
        return LauncherPreferenceSyncDialog.showAndWait(
                overlayHost,
                remote.installedProjects().size(),
                local.installedProjects().size(),
                remote.getUpdatedAt()
        );
    }

    private void restoreSnapshot(LauncherSettingsSnapshot snapshot) {
        feedback.runAsync("Loading launcher preferences from Modtale...",
                () -> restore(snapshot),
                result -> {
                    LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settingsController.settings());
                    lastKnownRemoteHash = local.computeHash();
                    lastKnownRemoteInstalledProjectsHash = local.installedProjectsHash();
                    settingsController.reloadFromStore();
                    feedback.log("Loaded launcher preferences from Modtale.");
                    feedback.showToast("Preferences loaded", result.message());
                });
    }

    private RestoreResult restore(LauncherSettingsSnapshot snapshot) {
        LauncherSettings settings = settingsController.settings();
        List<InstalledProject> previousInstalls = new ArrayList<>(settings.getInstalledProjects());
        Set<String> remoteProjectIds = remoteProjectIds(snapshot);
        List<InstalledProject> preservedLocalInstalls = previousInstalls.stream()
                .filter(project -> !remoteProjectIds.contains(project.projectId()))
                .toList();
        deleteRecordedFiles(previousInstalls.stream()
                .filter(project -> remoteProjectIds.contains(project.projectId()))
                .toList());
        remoteProjectIds.forEach(settingsStore::removeInstalledProject);

        snapshot.applyPreferencesTo(settings);
        settings.setInstalledProjects(preservedLocalInstalls);
        settingsStore.save(settings);

        int installed = 0;
        List<String> warnings = new ArrayList<>();
        for (LauncherSettingsSnapshot.InstalledProjectSnapshot projectSnapshot : snapshot.installedProjects()) {
            if (projectSnapshot.getProjectId() == null || projectSnapshot.getProjectId().isBlank()) {
                continue;
            }
            try {
                ProjectDetail project = apiClient.getProject(projectSnapshot.getProjectId());
                ProjectVersion version = resolveVersion(project, projectSnapshot, settings);
                InstallResult result = installer.install(project, version, installOptions(settings, projectSnapshot));
                settings.upsertInstalledProject(result.installedProject().withModpackUnlocked(projectSnapshot.isModpackUnlocked()));
                settingsStore.save(settings);
                installed++;
                warnings.addAll(result.warnings());
            } catch (RuntimeException ex) {
                LOG.warn("Could not restore installed project {}", projectSnapshot.getProjectId(), ex);
                warnings.add(projectSnapshot.getProjectId() + ": " + ex.getMessage());
            }
        }

        if (!warnings.isEmpty()) {
            feedback.log("Launcher preference restore warnings: " + String.join(" ", warnings));
        }
        return new RestoreResult("Restored " + installed + " installed project" + plural(installed)
                + preservedMessage(preservedLocalInstalls.size()) + " and saved preferences.");
    }

    private Set<String> remoteProjectIds(LauncherSettingsSnapshot snapshot) {
        Set<String> ids = new LinkedHashSet<>();
        for (LauncherSettingsSnapshot.InstalledProjectSnapshot installed : snapshot.installedProjects()) {
            if (installed.getProjectId() != null && !installed.getProjectId().isBlank()) {
                ids.add(installed.getProjectId().trim());
            }
        }
        return ids;
    }

    private String preservedMessage(int preserved) {
        if (preserved <= 0) {
            return "";
        }
        return " and kept " + preserved + " local install" + plural(preserved);
    }

    private ProjectVersion resolveVersion(
            ProjectDetail project,
            LauncherSettingsSnapshot.InstalledProjectSnapshot installed,
            LauncherSettings settings
    ) {
        if (project == null) {
            throw new ModtaleApiException("Project is no longer available.");
        }
        if (project.versions().isEmpty()) {
            project = project.withVersions(apiClient.getProjectVersions(project.routeKey()));
        }
        if (installed.getInstalledVersionId() != null && !installed.getInstalledVersionId().isBlank()) {
            for (ProjectVersion version : project.versions()) {
                if (installed.getInstalledVersionId().equals(version.id())) {
                    return version;
                }
            }
        }
        if (installed.getInstalledVersion() != null && !installed.getInstalledVersion().isBlank()) {
            for (ProjectVersion version : project.versions()) {
                if (installed.getInstalledVersion().equals(version.versionNumber())) {
                    return version;
                }
            }
        }
        String projectTitle = project.title();
        return VersionSelector.latestCompatible(project, effectiveGameVersion(settings, installed))
                .orElseThrow(() -> new ModtaleApiException("No compatible version was found for " + projectTitle));
    }

    private InstallOptions installOptions(
            LauncherSettings settings,
            LauncherSettingsSnapshot.InstalledProjectSnapshot installed
    ) {
        if (installed.getBundledProjects() != null && !installed.getBundledProjects().isEmpty()) {
            return new InstallOptions(
                    settings.hytaleModsDirectory(),
                    effectiveGameVersion(settings, installed),
                    true,
                    true,
                    installed.getBundledProjects().stream()
                            .map(net.modtale.launcher.model.install.InstalledProjectReference::toDependency)
                            .toList()
            );
        }
        return new InstallOptions(
                settings.hytaleModsDirectory(),
                effectiveGameVersion(settings, installed),
                settings.isIncludeDependencies(),
                settings.isIncludeOptionalDependencies()
        );
    }

    private String effectiveGameVersion(
            LauncherSettings settings,
            LauncherSettingsSnapshot.InstalledProjectSnapshot installed
    ) {
        if (installed.getGameVersion() != null && !installed.getGameVersion().isBlank()) {
            return installed.getGameVersion();
        }
        return settings.getGameVersion();
    }

    private void deleteRecordedFiles(List<InstalledProject> installedProjects) {
        for (InstalledProject installed : installedProjects) {
            if (installed == null || installed.files() == null) {
                continue;
            }
            for (String file : installed.files()) {
                if (file == null || file.isBlank()) {
                    continue;
                }
                try {
                    Files.deleteIfExists(Path.of(file));
                } catch (IOException ex) {
                    LOG.warn("Could not delete stale installed file while restoring snapshot: {}", file, ex);
                    // A stale file should not block restoring the account snapshot.
                }
            }
        }
    }

    private void uploadSnapshot(LauncherSettingsSnapshot snapshot, boolean announce) {
        uploadSnapshot(snapshot, announce, false);
    }

    private void uploadPreferences(LauncherSettingsSnapshot snapshot, boolean announce) {
        uploadSnapshot(snapshot, announce, true);
    }

    private void uploadSnapshot(LauncherSettingsSnapshot snapshot, boolean announce, boolean preferencesOnly) {
        if (!signedIn.getAsBoolean()) {
            return;
        }
        snapshot.refreshHash();
        String snapshotHash = snapshot.computeHash();
        if (snapshotHash.equals(lastKnownRemoteHash)) {
            return;
        }
        if (!uploading.compareAndSet(false, true)) {
            return;
        }
        feedback.runAsync("Saving launcher preferences to Modtale...",
                () -> preferencesOnly
                        ? apiClient.updateLauncherSettingsPreferences(snapshot)
                        : apiClient.updateLauncherSettings(snapshot),
                saved -> {
                    uploading.set(false);
                    lastKnownRemoteHash = saved == null || hashMatches(saved, snapshotHash)
                            ? snapshotHash
                            : saved.effectiveHash();
                    lastKnownRemoteInstalledProjectsHash = saved == null || hashMatches(saved, snapshotHash)
                            ? snapshot.installedProjectsHash()
                            : saved.installedProjectsHash();
                    if (announce) {
                        feedback.log("Saved this device's launcher preferences to Modtale.");
                        feedback.showToast("Preferences saved", "This device is now the account snapshot.");
                    }
                },
                error -> uploading.set(false));
    }

    private boolean canUploadPreferencesOnly(LauncherSettingsSnapshot snapshot) {
        String installedProjectsHash = snapshot.installedProjectsHash();
        return !lastKnownRemoteInstalledProjectsHash.isBlank()
                && installedProjectsHash.equals(lastKnownRemoteInstalledProjectsHash);
    }

    private boolean hashMatches(LauncherSettingsSnapshot remote, String localHash) {
        if (localHash == null || localHash.isBlank()) {
            return false;
        }
        return localHash.equals(remote.effectiveHash()) || localHash.equals(remote.computeHash());
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }

    private record RestoreResult(String message) {
    }
}
