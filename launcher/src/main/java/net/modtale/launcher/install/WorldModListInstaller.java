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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WorldModListInstaller {

    private static final Logger LOG = LogManager.getLogger(WorldModListInstaller.class);

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

        LOG.info("Starting shared list install listId=" + list.id()
                + " title=\"" + list.title() + "\""
                + " modsDirectory=" + settings.hytaleModsDirectory()
                + " itemCount=" + list.mods().size());
        DownloadedFile download = apiClient.download("/lists/" + encodePathSegment(list.id()) + "/download");
        try {
            LOG.info("Extracting shared list archive listId=" + list.id()
                    + " filename=" + download.filename()
                    + " temp=" + download.path());
            List<Path> installedFiles = archiveInstaller.extractInstallableEntries(download.path(), settings.hytaleModsDirectory());
            if (installedFiles.isEmpty()) {
                LOG.warn("Shared list archive had no installable files listId=" + list.id());
                throw new ModtaleApiException("This shared list did not include any installable files.");
            }
            LOG.info("Completed shared list install listId=" + list.id()
                    + " fileCount=" + installedFiles.size()
                    + " files=" + installedFiles);
            return new WorldModListInstallResult(list, installedFiles);
        } catch (IOException ex) {
            LOG.warn("Could not install shared list listId=" + list.id()
                    + " into " + settings.hytaleModsDirectory(), ex);
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
            LOG.warn("Could not delete temporary shared list archive " + path);
            // Temporary download cleanup is best-effort.
        }
    }
}
