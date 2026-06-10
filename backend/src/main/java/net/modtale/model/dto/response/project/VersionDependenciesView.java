package net.modtale.model.dto.response.project;

import net.modtale.model.dto.project.ProjectDependencyDTO;

import java.util.List;

public record VersionDependenciesView(List<ProjectDependencyDTO> dependencies) {

    public VersionDependenciesView {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
