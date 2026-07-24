package net.modtale.launcher.model.install;

import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.model.project.ProjectDependency;

public record InstallOptions(
        Path modsDirectory,
        String gameVersion,
        boolean includeDependencies,
        boolean includeOptionalDependencies,
        List<ProjectDependency> selectedDependencies
) {
    public InstallOptions {
        selectedDependencies = selectedDependencies == null ? null : List.copyOf(selectedDependencies);
    }

    public InstallOptions(
            Path modsDirectory,
            String gameVersion,
            boolean includeDependencies,
            boolean includeOptionalDependencies
    ) {
        this(modsDirectory, gameVersion, includeDependencies, includeOptionalDependencies, null);
    }

    public boolean hasSelectedDependencies() {
        return selectedDependencies != null;
    }
}
