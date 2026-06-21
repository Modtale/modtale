package net.modtale.launcher.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.modtale.launcher.api.ModtaleApiClient.DownloadedFile;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.logging.LogSanitizer;
import net.modtale.launcher.model.install.InstallOptions;
import net.modtale.launcher.model.install.InstallResult;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.project.DownloadUrlResponse;
import net.modtale.launcher.model.project.ProjectClassification;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.project.VersionDependenciesView;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModInstaller {

    private static final Logger LOG = LogManager.getLogger(ModInstaller.class);

    private final ModtaleApiClient apiClient;
    private final SettingsStore settingsStore;
    private final ArchiveInstaller archiveInstaller;

    public ModInstaller(ModtaleApiClient apiClient, SettingsStore settingsStore) {
        this(apiClient, settingsStore, new ArchiveInstaller());
    }

    ModInstaller(ModtaleApiClient apiClient, SettingsStore settingsStore, ArchiveInstaller archiveInstaller) {
        this.apiClient = apiClient;
        this.settingsStore = settingsStore;
        this.archiveInstaller = archiveInstaller;
    }

    public InstallResult installLatest(ProjectDetail project, LauncherSettings settings) {
        ProjectVersion version = VersionSelector.latestCompatible(project, settings.getGameVersion())
                .orElseThrow(() -> new ModtaleApiException("No compatible version was found for " + project.title()));
        return install(project, version, optionsFrom(settings));
    }

    public InstallResult install(ProjectDetail project, ProjectVersion version, InstallOptions options) {
        if (project == null || version == null) {
            throw new ModtaleApiException("Select a project and version before installing.");
        }
        LOG.info("Starting install projectId=" + project.id()
                + " title=\"" + project.title() + "\""
                + " classification=" + project.classification()
                + " version=" + version.versionNumber()
                + " versionId=" + version.id()
                + " gameVersion=" + options.gameVersion()
                + " modsDirectory=" + options.modsDirectory()
                + " includeDependencies=" + options.includeDependencies()
                + " includeOptionalDependencies=" + options.includeOptionalDependencies()
                + " selectedDependencies=" + (options.selectedDependencies() == null ? 0 : options.selectedDependencies().size()));
        try {
            Files.createDirectories(options.modsDirectory());
        } catch (IOException ex) {
            LOG.warn("Could not create mods directory " + options.modsDirectory(), ex);
            throw new ModtaleApiException("Could not create Hytale mods directory " + options.modsDirectory(), ex);
        }

        List<ProjectDependency> dependencies = dependencies(project, version, options);
        boolean exactDependencySelection = options.hasSelectedDependencies();
        boolean includeOptional = exactDependencySelection || options.includeOptionalDependencies();
        List<String> selectedModtaleDependencies = selectedModtaleDependencies(dependencies, includeOptional);
        List<ProjectDependency> selectedExternalDependencies = selectedExternalDependencies(dependencies, includeOptional);
        boolean hasDependencySelection = exactDependencySelection
                ? !dependencies.isEmpty()
                : options.includeDependencies();
        boolean isBundle = hasDependencySelection && !selectedModtaleDependencies.isEmpty();
        boolean isModpack = ProjectClassification.isModpack(project.classification());
        List<InstalledProjectReference> selectedReferences = selectedDependencyReferences(dependencies, includeOptional);
        LOG.info("Resolved dependencies projectId=" + project.id()
                + " total=" + dependencies.size()
                + " selectedModtale=" + selectedModtaleDependencies.size()
                + " selectedExternal=" + selectedExternalDependencies.size()
                + " installMode=" + (isModpack ? "MODPACK" : isBundle ? "BUNDLE" : "DIRECT"));

        List<Path> installedFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> externalNames = new ArrayList<>();

        DownloadUrlResponse downloadUrl = isBundle
                ? apiClient.getBundleDownloadUrl(project.id(), version.versionNumber(), selectedModtaleDependencies, options.gameVersion())
                : apiClient.getDownloadUrl(project.id(), version.versionNumber(), options.gameVersion());
        LOG.info("Resolved download URL projectId=" + project.id()
                + " mode=" + (isBundle ? "BUNDLE" : "DIRECT")
                + " url=" + LogSanitizer.url(downloadUrl == null ? "" : downloadUrl.downloadUrl()));

        DownloadedFile mainDownload = apiClient.download(downloadUrl.downloadUrl());
        boolean unpackMainDownload = isBundle || looksLikeGeneratedArchive(mainDownload);
        LOG.info("Installing main download projectId=" + project.id()
                + " filename=" + mainDownload.filename()
                + " contentType=" + mainDownload.contentType()
                + " unpack=" + unpackMainDownload
                + " temp=" + mainDownload.path());
        try {
            if (isModpack || isBundle) {
                installedFiles.addAll(archiveInstaller.installModpackArchive(mainDownload.path(), options.modsDirectory()));
            } else {
                installedFiles.addAll(archiveInstaller.installDownloadedFile(
                        mainDownload.path(),
                        mainDownload.filename(),
                        options.modsDirectory(),
                        unpackMainDownload
                ));
            }
            LOG.info("Installed main download projectId=" + project.id()
                    + " fileCount=" + installedFiles.size()
                    + " files=" + installedFiles);
        } catch (IOException ex) {
            LOG.warn("Could not install main download projectId=" + project.id()
                    + " into " + options.modsDirectory(), ex);
            throw new ModtaleApiException("Could not install " + project.title() + " into " + options.modsDirectory(), ex);
        } finally {
            deleteTemp(mainDownload.path());
        }

        for (ProjectDependency dependency : selectedExternalDependencies) {
            if (dependency.externalFileUrl() == null || dependency.externalFileUrl().isBlank()) {
                warnings.add("External dependency needs manual install: " + displayName(dependency));
                LOG.warn("External dependency missing file URL: " + displayName(dependency));
                continue;
            }
            LOG.info("Downloading external dependency " + displayName(dependency)
                    + " url=" + LogSanitizer.url(dependency.externalFileUrl()));
            DownloadedFile externalDownload = apiClient.download(dependency.externalFileUrl());
            try {
                String filename = dependency.externalFileName() == null || dependency.externalFileName().isBlank()
                        ? externalDownload.filename()
                        : dependency.externalFileName();
                installedFiles.addAll(archiveInstaller.installDownloadedFile(
                        externalDownload.path(),
                        filename,
                        options.modsDirectory(),
                        false
                ));
                externalNames.add(displayName(dependency));
                LOG.info("Installed external dependency " + displayName(dependency)
                        + " filename=" + filename
                        + " totalFileCount=" + installedFiles.size());
            } catch (IOException ex) {
                warnings.add("External dependency failed: " + displayName(dependency) + " (" + ex.getMessage() + ")");
                LOG.warn("External dependency failed: " + displayName(dependency), ex);
            } finally {
                deleteTemp(externalDownload.path());
            }
        }

        InstalledProject installedProject = new InstalledProject(
                project.id(),
                project.slug(),
                project.title(),
                project.classification(),
                version.versionNumber(),
                version.id(),
                options.gameVersion(),
                Instant.now(),
                Instant.now(),
                installedFiles.stream().map(Path::toString).toList(),
                selectedModtaleDependencies,
                externalNames,
                InstalledProject.SOURCE_MODTALE,
                isModpack ? InstalledProject.INSTALL_MODPACK : isBundle ? InstalledProject.INSTALL_BUNDLE : InstalledProject.INSTALL_DIRECT,
                false,
                selectedReferences
        );
        LOG.info("Completed install projectId=" + project.id()
                + " installedVersion=" + version.versionNumber()
                + " fileCount=" + installedFiles.size()
                + " warnings=" + warnings.size());
        return new InstallResult(installedProject, installedFiles, warnings);
    }

    public InstallResult installAndRecord(ProjectDetail project, LauncherSettings settings) {
        ProjectVersion version = VersionSelector.latestCompatible(project, settings.getGameVersion())
                .orElseThrow(() -> new ModtaleApiException("No compatible version was found for " + project.title()));
        InstalledProject previous = removePreviousInstall(project.id(), settings);
        InstallResult result = install(project, version, optionsFrom(settings));
        return recordInstall(result, settings, previous);
    }

    public InstallResult installAndRecord(ProjectDetail project, ProjectVersion version, LauncherSettings settings, String gameVersion) {
        InstalledProject previous = removePreviousInstall(project.id(), settings);
        InstallResult result = install(project, version, new InstallOptions(
                settings.hytaleModsDirectory(),
                gameVersion == null || gameVersion.isBlank() ? settings.getGameVersion() : gameVersion,
                settings.isIncludeDependencies(),
                settings.isIncludeOptionalDependencies()
        ));
        return recordInstall(result, settings, previous);
    }

    public InstallResult installAndRecord(
            ProjectDetail project,
            ProjectVersion version,
            LauncherSettings settings,
            String gameVersion,
            List<ProjectDependency> selectedDependencies
    ) {
        InstalledProject previous = removePreviousInstall(project.id(), settings);
        List<ProjectDependency> dependencies = selectedDependencies == null ? null : List.copyOf(selectedDependencies);
        InstallResult result = install(project, version, new InstallOptions(
                settings.hytaleModsDirectory(),
                gameVersion == null || gameVersion.isBlank() ? settings.getGameVersion() : gameVersion,
                dependencies != null && !dependencies.isEmpty(),
                true,
                dependencies
        ));
        return recordInstall(result, settings, previous);
    }

    public InstallResult updateAndRecord(ProjectDetail project, ProjectVersion version, LauncherSettings settings) {
        InstalledProject previous = existingInstall(project.id(), settings).orElse(null);
        removePreviousInstall(project.id(), settings);
        InstallResult result = install(project, version, previous == null ? optionsFrom(settings) : optionsFrom(settings, previous));
        return recordInstall(result, settings, previous);
    }

    public InstallResult switchVersionAndRecord(
            InstalledProject installed,
            ProjectDetail project,
            ProjectVersion version,
            LauncherSettings settings
    ) {
        return switchVersionAndRecord(installed, project, version, settings, "");
    }

    public InstallResult switchVersionAndRecord(
            InstalledProject installed,
            ProjectDetail project,
            ProjectVersion version,
            LauncherSettings settings,
            String gameVersion
    ) {
        if (installed == null) {
            return updateAndRecord(project, version, settings);
        }
        removePreviousInstall(installed.projectId(), settings);
        InstallResult result = install(project, version, optionsFrom(settings, installed, gameVersion));
        return recordInstall(result, settings, installed);
    }

    public void uninstallAndRecord(InstalledProject installed, LauncherSettings settings) {
        if (installed == null || settings == null) {
            return;
        }
        deleteRecordedFiles(installed);
        settings.removeInstalledProject(installed.projectId());
        settingsStore.removeInstalledProject(installed.projectId());
        settingsStore.save(settings);
    }

    private InstallResult recordInstall(InstallResult result, LauncherSettings settings, InstalledProject previous) {
        InstalledProject recorded = mergeInstallMetadata(result.installedProject(), previous);
        settings.upsertInstalledProject(recorded);
        settingsStore.save(settings);
        return new InstallResult(recorded, result.installedFiles(), result.warnings());
    }

    private InstalledProject mergeInstallMetadata(InstalledProject fresh, InstalledProject previous) {
        if (previous == null) {
            return fresh;
        }
        boolean unlocked = previous.modpackUnlocked() && (fresh.isModpack() || !fresh.bundledProjects().isEmpty());
        return new InstalledProject(
                fresh.projectId(),
                fresh.slug(),
                fresh.title(),
                fresh.classification(),
                fresh.installedVersion(),
                fresh.installedVersionId(),
                fresh.gameVersion(),
                previous.installedAt(),
                fresh.updatedAt(),
                fresh.files(),
                fresh.dependencyProjectIds(),
                fresh.externalDependencies(),
                fresh.source(),
                fresh.installType(),
                unlocked,
                fresh.bundledProjects()
        );
    }

    private Optional<InstalledProject> existingInstall(String projectId, LauncherSettings settings) {
        if (projectId == null || settings == null) {
            return Optional.empty();
        }
        return settings.getInstalledProjects().stream()
                .filter(installed -> projectId.equals(installed.projectId()))
                .findFirst();
    }

    private InstalledProject removePreviousInstall(String projectId, LauncherSettings settings) {
        InstalledProject previous = existingInstall(projectId, settings).orElse(null);
        if (previous != null) {
            LOG.info("Removing previous install projectId=" + projectId
                    + " fileCount=" + previous.files().size());
            deleteRecordedFiles(previous);
        }
        return previous;
    }

    private static void deleteRecordedFiles(InstalledProject installed) {
        installed.files().forEach(file -> {
            try {
                Files.deleteIfExists(Path.of(file));
            } catch (IOException ignored) {
                LOG.warn("Could not delete stale installed file " + file);
                // Stale files should not block an update; the new install can still succeed.
            }
        });
    }

    private static InstallOptions optionsFrom(LauncherSettings settings) {
        return new InstallOptions(
                settings.hytaleModsDirectory(),
                settings.getGameVersion(),
                settings.isIncludeDependencies(),
                settings.isIncludeOptionalDependencies()
        );
    }

    private static InstallOptions optionsFrom(LauncherSettings settings, InstalledProject installed) {
        return optionsFrom(settings, installed, "");
    }

    private static InstallOptions optionsFrom(LauncherSettings settings, InstalledProject installed, String selectedGameVersion) {
        String gameVersion = selectedGameVersion == null || selectedGameVersion.isBlank()
                ? installed.gameVersion()
                : selectedGameVersion.trim();
        if (gameVersion == null || gameVersion.isBlank()) {
            gameVersion = settings.getGameVersion();
        }
        if (!installed.bundledProjects().isEmpty()) {
            return new InstallOptions(
                    settings.hytaleModsDirectory(),
                    gameVersion,
                    true,
                    true,
                    installed.bundledProjects().stream()
                            .map(InstalledProjectReference::toDependency)
                            .toList()
            );
        }
        return new InstallOptions(
                settings.hytaleModsDirectory(),
                gameVersion,
                settings.isIncludeDependencies(),
                settings.isIncludeOptionalDependencies()
        );
    }

    private List<ProjectDependency> dependencies(ProjectDetail project, ProjectVersion version, InstallOptions options) {
        if (options.hasSelectedDependencies()) {
            return options.selectedDependencies();
        }
        VersionDependenciesView dependenciesView = options.includeDependencies()
                ? apiClient.getDependencies(project.id(), version.versionNumber(), options.gameVersion())
                : new VersionDependenciesView(List.of());
        LOG.info("Loaded dependency view projectId=" + project.id()
                + " version=" + version.versionNumber()
                + " count=" + dependenciesView.dependencies().size());
        return dependenciesView.dependencies();
    }

    private static List<InstalledProjectReference> selectedDependencyReferences(
            List<ProjectDependency> dependencies,
            boolean includeOptional
    ) {
        return dependencies.stream()
                .filter(dependency -> !dependency.isEmbedded())
                .filter(dependency -> includeOptional || !dependency.isOptional())
                .map(InstalledProjectReference::fromDependency)
                .toList();
    }

    private static List<String> selectedModtaleDependencies(List<ProjectDependency> dependencies, boolean includeOptional) {
        Set<String> ids = new LinkedHashSet<>();
        for (ProjectDependency dependency : dependencies) {
            if (dependency.isExternal() || dependency.isEmbedded() || dependency.projectId() == null || dependency.projectId().isBlank()) {
                continue;
            }
            if (dependency.isOptional() && !includeOptional) {
                continue;
            }
            ids.add(dependency.projectId());
        }
        return List.copyOf(ids);
    }

    private static List<ProjectDependency> selectedExternalDependencies(List<ProjectDependency> dependencies, boolean includeOptional) {
        return dependencies.stream()
                .filter(ProjectDependency::isExternal)
                .filter(dependency -> !dependency.isEmbedded())
                .filter(dependency -> includeOptional || !dependency.isOptional())
                .toList();
    }

    private static boolean looksLikeGeneratedArchive(DownloadedFile file) {
        String lowerName = file.filename() == null ? "" : file.filename().toLowerCase(java.util.Locale.ROOT);
        String lowerType = file.contentType() == null ? "" : file.contentType().toLowerCase(java.util.Locale.ROOT);
        return lowerName.endsWith("-unzip-me.zip")
                || lowerName.contains("modpack")
                || lowerType.contains("application/zip");
    }

    private static void deleteTemp(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            LOG.warn("Could not delete temporary download " + path);
            // Temporary download cleanup is best-effort.
        }
    }

    private static String displayName(ProjectDependency dependency) {
        if (dependency.projectTitle() != null && !dependency.projectTitle().isBlank()) {
            return dependency.projectTitle();
        }
        if (dependency.title() != null && !dependency.title().isBlank()) {
            return dependency.title();
        }
        if (dependency.externalId() != null && !dependency.externalId().isBlank()) {
            return dependency.source() + ":" + dependency.externalId();
        }
        return dependency.id() == null ? "external dependency" : dependency.id();
    }
}
