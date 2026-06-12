package net.modtale.model.dto.response.project;

import java.util.List;
import net.modtale.model.dto.project.ProjectDependencyDTO;

public record VersionDependenciesView(List<ProjectDependencyDTO> dependencies) {

    public VersionDependenciesView {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
