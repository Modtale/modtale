package net.modtale.launcher.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ModtaleApiClient.DownloadedFile;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.model.worldlist.WorldModList;
import net.modtale.launcher.model.worldlist.WorldModListInstallResult;
import net.modtale.launcher.settings.LauncherSettings;

public final class WorldModListInstaller {

    private final ModtaleApiClient apiClient;
    private final ArchiveInstaller archiveInstaller;

    public WorldModListInstaller(ModtaleApiClient apiClient) {
        this(apiClient, new ArchiveInstaller());
    }

    WorldModListInstaller(ModtaleApiClient apiClient, ArchiveInstaller archiveInstaller) {
        this.apiClient = apiClient;
        this.archiveInstaller = archiveInstaller;
    }

    public WorldModListInstallResult install(WorldModList list, LauncherSettings settings) {
        if (list == null || list.id().isBlank()) {
            throw new ModtaleApiException("Select a shared mod list before installing.");
        }
        if (settings == null) {
            throw new ModtaleApiException("Launcher settings are unavailable.");
        }

        DownloadedFile download = apiClient.download("/lists/" + encodePathSegment(list.id()) + "/download");
        try {
            List<Path> installedFiles = archiveInstaller.extractInstallableEntries(download.path(), settings.hytaleModsDirectory());
            if (installedFiles.isEmpty()) {
                throw new ModtaleApiException("This shared list did not include any installable files.");
            }
            return new WorldModListInstallResult(list, installedFiles);
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not install " + list.title() + " into " + settings.hytaleModsDirectory(), ex);
        } finally {
            deleteTemp(download.path());
        }
    }

    private static String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value.trim(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static void deleteTemp(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary download cleanup is best-effort.
        }
    }
}
