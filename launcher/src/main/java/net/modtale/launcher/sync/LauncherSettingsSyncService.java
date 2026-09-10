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
import net.modtale.launcher.logging.LauncherLog;
import net.modtale.launcher.logging.LauncherLogger;

public final class LauncherSettingsSyncService {

    private static final LauncherLogger LOG = LauncherLog.getLogger(LauncherSettingsSyncService.class);

    private final ModtaleApiClient apiClient;
    private final SettingsStore settingsStore;
    private final LauncherSettingsController settingsController;
    private final ModInstaller installer;
    private final LauncherFeedback feedback;
    private final BooleanSupplier signedIn;
    private final Supplier<StackPane> overlayHost;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean uploading = new AtomicBoolean();
    private final AtomicBoolean capturing = new AtomicBoolean();
    private final AtomicBoolean pendingLocalChange = new AtomicBoolean();
    private final AtomicBoolean pendingRemoteCheck = new AtomicBoolean();
    private final net.modtale.launcher.config.LauncherConfigStore configStore = new net.modtale.launcher.config.LauncherConfigStore();

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
        if (capturing.get() || uploading.get()) {
            pendingRemoteCheck.set(true);
            return;
        }
        if (!signedIn.getAsBoolean() || !checking.compareAndSet(false, true)) {
            return;
        }
        feedback.runAsync("Checking launcher preferences...",
                () -> {
                    captureLocalSnapshot();
                    return apiClient.getLauncherSettings();
                },
                remote -> {
                    if (!handleRemoteSnapshot(remote)) checking.set(false);
                    drainLocalChanges();
                },
                error -> { checking.set(false); drainLocalChanges(); });
    }

    public void syncAfterLocalChange() {
        pendingLocalChange.set(true);
        drainLocalChanges();
    }

    private void drainLocalChanges() {
        if (!pendingLocalChange.get() || checking.get() || uploading.get()
                || !capturing.compareAndSet(false, true)) return;
        pendingLocalChange.set(false);
        feedback.runAsync("Saving launcher configs...", this::captureLocalSnapshot, local -> {
            capturing.set(false);
            if (pendingRemoteCheck.getAndSet(false)) {
                checkOnSignIn();
                return;
            }
            if (signedIn.getAsBoolean() && !checking.get() && !local.computeHash().equals(lastKnownRemoteHash)) {
                if (canUploadPreferencesOnly(local)) uploadPreferences(local, false);
                else uploadSnapshot(local, false);
            }
            drainLocalChanges();
        }, error -> {
            capturing.set(false);
            if (pendingRemoteCheck.getAndSet(false)) checkOnSignIn();
        });
    }

    private LauncherSettingsSnapshot captureLocalSnapshot() {
        LauncherSettings settings = settingsController.settings();
        try {
            settings.setConfigs(configStore.capture(settings));
            settingsStore.save(settings);
            return LauncherSettingsSnapshot.fromSettings(settings);
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not save launcher configs: " + ex.getMessage(), ex);
        }
    }

    private boolean handleRemoteSnapshot(LauncherSettingsSnapshot remote) {
        LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settingsController.settings());
        if (remote == null || !remote.hasSyncedContent()) {
            uploadSnapshot(local, false);
            return false;
        }

        String localHash = local.computeHash();
        if (hashMatches(remote, localHash)) {
            lastKnownRemoteHash = localHash;
            lastKnownRemoteInstalledProjectsHash = local.installedProjectsHash();
            return false;
        }

        lastKnownRemoteHash = remote.effectiveHash();
        lastKnownRemoteInstalledProjectsHash = remote.installedProjectsHash();
        var choice = promptLoadRemote(remote, local);
        if (choice == net.modtale.launcher.ui.common.StatusModal.Result.PRIMARY) {
            restoreSnapshot(remote);
            return true;
        } else if (choice == net.modtale.launcher.ui.common.StatusModal.Result.SECONDARY) {
            uploadSnapshot(local, true);
        }
        return false;
    }

    private net.modtale.launcher.ui.common.StatusModal.Result promptLoadRemote(LauncherSettingsSnapshot remote, LauncherSettingsSnapshot local) {
        return LauncherPreferenceSyncDialog.showAndWait(
                overlayHost,
                remote.installedProjects().size(),
                local.installedProjects().size(),
                remote.getUpdatedAt(), remote.getConfigs().size(), local.getConfigs().size()
        );
    }

    private void restoreSnapshot(LauncherSettingsSnapshot snapshot) {
        checking.set(true);
        var progress = new net.modtale.launcher.ui.common.TransferLoadingModal(
                "Syncing your launcher", "Restoring settings and configs");
        if (overlayHost.get() != null) overlayHost.get().getChildren().add(progress);
        feedback.runAsync("Loading launcher settings and configs from Modtale...",
                () -> restore(snapshot, progress),
                result -> {
                    LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settingsController.settings());
                    lastKnownRemoteHash = local.computeHash();
                    lastKnownRemoteInstalledProjectsHash = local.installedProjectsHash();
                    progress.update("Syncing your launcher", "Refreshing your Library");
                    settingsController.reloadFromStore();
                    progress.dismiss();
                    checking.set(false);
                    feedback.log("Loaded launcher preferences from Modtale.");
                    feedback.showToast("Preferences loaded", result.message());
                    drainLocalChanges();
                }, error -> { progress.dismiss(); checking.set(false); });
    }

    private RestoreResult restore(LauncherSettingsSnapshot snapshot, net.modtale.launcher.ui.common.TransferLoadingModal progress) {
        LauncherSettings settings = settingsController.settings();
        int restoredConfigs;
        try {
            restoredConfigs = configStore.restore(snapshot.getConfigs(), settings);
            settings.setConfigs(snapshot.getConfigs());
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not restore launcher configs: " + ex.getMessage(), ex);
        }
        boolean reinstallProjects = !snapshot.installedProjectsHash()
                .equals(LauncherSettingsSnapshot.fromSettings(settings).installedProjectsHash());
        List<InstalledProject> previousInstalls = new ArrayList<>(settings.getInstalledProjects());
        Set<String> remoteProjectIds = remoteProjectIds(snapshot);
        List<InstalledProject> preservedLocalInstalls = previousInstalls.stream()
                .filter(project -> !remoteProjectIds.contains(project.projectId()))
                .toList();
        if (reinstallProjects) {
            deleteRecordedFiles(previousInstalls.stream()
                    .filter(project -> remoteProjectIds.contains(project.projectId()))
                    .toList());
            remoteProjectIds.forEach(settingsStore::removeInstalledProject);
        }

        // Device paths are local: a profile from another OS must not redirect config or mod writes.
        String modsPath = settings.getHytaleModsPath();
        String userDataPath = settings.getHytaleUserDataPath();
        String gamePath = settings.getHytaleGamePath();
        String javaPath = settings.getHytaleJavaPath();
        snapshot.applyPreferencesTo(settings);
        settings.setHytaleModsPath(modsPath);
        settings.setHytaleUserDataPath(userDataPath);
        settings.setHytaleGamePath(gamePath);
        settings.setHytaleJavaPath(javaPath);
        if (reinstallProjects) settings.setInstalledProjects(preservedLocalInstalls);
        settingsStore.save(settings);
        if (!reinstallProjects) {
            return new RestoreResult("Restored " + restoredConfigs + " config file" + plural(restoredConfigs)
                    + " and saved preferences. Installed projects were already current.");
        }

        int installed = 0;
        List<String> warnings = new ArrayList<>();
        for (LauncherSettingsSnapshot.InstalledProjectSnapshot projectSnapshot : snapshot.installedProjects()) {
            if (projectSnapshot.getProjectId() == null || projectSnapshot.getProjectId().isBlank()) {
                continue;
            }
            try {
                ProjectDetail project = apiClient.getProject(projectSnapshot.getProjectId());
                ProjectVersion version = resolveVersion(project, projectSnapshot, settings);
                progress.update("Restoring your mods", "Downloading " + project.title()
                        + " • " + (installed + 1) + " of " + snapshot.installedProjects().size());
                InstallResult result = installer.install(project, version, installOptions(settings, projectSnapshot, version));
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
                + preservedMessage(preservedLocalInstalls.size()) + ", " + restoredConfigs + " config file"
                + plural(restoredConfigs) + " and saved preferences.");
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
            LauncherSettingsSnapshot.InstalledProjectSnapshot installed,
            ProjectVersion version
    ) {
        String downloadGameVersion = downloadGameVersion(version, effectiveGameVersion(settings, installed));
        if (installed.getBundledProjects() != null && !installed.getBundledProjects().isEmpty()) {
            return new InstallOptions(
                    settings.hytaleModsDirectory(),
                    downloadGameVersion,
                    true,
                    true,
                    installed.getBundledProjects().stream()
                            .map(net.modtale.launcher.model.install.InstalledProjectReference::toDependency)
                            .toList(),
                    settings.hytaleUserDataDirectory()
            );
        }
        return new InstallOptions(
                settings.hytaleModsDirectory(),
                downloadGameVersion,
                settings.isIncludeDependencies(),
                settings.isIncludeOptionalDependencies(),
                null,
                settings.hytaleUserDataDirectory()
        );
    }

    static String downloadGameVersion(ProjectVersion version, String preferred) {
        if (version.gameVersions().isEmpty() || version.gameVersions().contains(preferred)) return preferred;
        return version.gameVersions().getFirst();
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
                    Path path = Path.of(file).toAbsolutePath().normalize();
                    Path mods = settingsController.settings().hytaleModsDirectory().toAbsolutePath().normalize();
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!mods.equals(path.getParent()) || !(name.endsWith(".jar") || name.endsWith(".zip")
                            || name.endsWith(".hmasset") || name.endsWith(".hymod"))) continue;
                    Files.deleteIfExists(path);
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
        feedback.runAsync("Saving launcher settings and configs to Modtale...",
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
                    if (pendingRemoteCheck.getAndSet(false)) checkOnSignIn();
                    else drainLocalChanges();
                    if (announce) {
                        feedback.log("Saved this device's launcher preferences to Modtale.");
                        feedback.showToast("Preferences saved", "This device is now the account snapshot.");
                    }
                },
                error -> {
                    uploading.set(false);
                    if (pendingRemoteCheck.getAndSet(false)) checkOnSignIn();
                    else drainLocalChanges();
                });
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
