package net.modtale.launcher.model.install;

import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.model.project.ProjectDependency;

public record InstallOptions(
        Path modsDirectory,
        String gameVersion,
        boolean includeDependencies,
        boolean includeOptionalDependencies,
        List<ProjectDependency> selectedDependencies,
        Path instanceDirectory
) {
    public InstallOptions {
        selectedDependencies = selectedDependencies == null ? null : List.copyOf(selectedDependencies);
        instanceDirectory = instanceDirectory == null ? modsDirectory.getParent() : instanceDirectory;
    }

    public InstallOptions(
            Path modsDirectory,
            String gameVersion,
            boolean includeDependencies,
            boolean includeOptionalDependencies
    ) {
        this(modsDirectory, gameVersion, includeDependencies, includeOptionalDependencies, null,
                modsDirectory == null ? null : modsDirectory.getParent());
    }

    public InstallOptions(
            Path modsDirectory,
            String gameVersion,
            boolean includeDependencies,
            boolean includeOptionalDependencies,
            List<ProjectDependency> selectedDependencies
    ) {
        this(modsDirectory, gameVersion, includeDependencies, includeOptionalDependencies, selectedDependencies,
                modsDirectory == null ? null : modsDirectory.getParent());
    }

    public boolean hasSelectedDependencies() {
        return selectedDependencies != null;
    }
}
