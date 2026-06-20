package net.modtale.launcher.ui.library;

import java.util.List;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;

record LibraryWorldModel(
        HytaleWorld world,
        String meta,
        int enabledProjectCount,
        int totalProjectCount,
        List<LibraryWorldProjectModel> projects
) {
    LibraryWorldModel {
        meta = meta == null ? "" : meta.trim();
        projects = projects == null ? List.of() : List.copyOf(projects);
        totalProjectCount = Math.max(totalProjectCount, projects.size());
        enabledProjectCount = Math.max(0, Math.min(enabledProjectCount, totalProjectCount));
    }
}
