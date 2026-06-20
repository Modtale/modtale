package net.modtale.launcher.model.worldlist;

import java.nio.file.Path;
import java.util.List;

public record WorldModListInstallResult(
        WorldModList list,
        List<Path> installedFiles
) {
    public WorldModListInstallResult {
        installedFiles = installedFiles == null ? List.of() : List.copyOf(installedFiles);
    }
}
