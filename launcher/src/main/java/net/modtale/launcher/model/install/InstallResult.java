package net.modtale.launcher.model.install;

import java.nio.file.Path;
import java.util.List;

public record InstallResult(InstalledProject installedProject, List<Path> installedFiles, List<String> warnings) {
    public InstallResult {
        installedFiles = installedFiles == null ? List.of() : List.copyOf(installedFiles);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
