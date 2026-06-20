package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VersionDependenciesView(List<ProjectDependency> dependencies) {
    public VersionDependenciesView {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
