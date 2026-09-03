package net.modtale.launcher.ui.library;

import java.util.List;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.UpdateCandidate;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.settings.LauncherSettings;

record LibraryDetailModel(
        InstalledProject installed,
        ProjectDetail detail,
        UpdateCandidate update,
        boolean loading,
        LauncherSettings settings,
        List<InstalledProject> installedProjects,
        List<LibraryWorldToggle> worldToggles
) {
    LibraryDetailModel {
        installedProjects = installedProjects == null ? List.of() : List.copyOf(installedProjects);
        worldToggles = worldToggles == null ? List.of() : List.copyOf(worldToggles);
    }
}
