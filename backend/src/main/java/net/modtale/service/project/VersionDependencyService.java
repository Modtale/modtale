package net.modtale.service.project;

import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VersionDependencyService {

    private final ProjectService projectService;

    public VersionDependencyService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public ResolvedDependencies resolveRequestedDependencies(
            List<String> dependencyEntries,
            boolean isModpack,
            boolean allowDraftDependencies
    ) {
        if (dependencyEntries == null) {
            return new ResolvedDependencies(List.of(), List.of());
        }

        List<ProjectDependency> dependencies = new ArrayList<>();
        List<String> simpleProjectIds = new ArrayList<>();

        for (String entry : dependencyEntries) {
            String[] parts = entry.split(":");
            if (parts.length < 2) {
                throw new InvalidVersionRequestException("Dependency entries must use the format projectId:version.");
            }

            String dependencyProjectId = parts[0].trim();
            String dependencyVersion = parts[1].trim();
            Project dependencyProject = projectService.getRawProjectById(dependencyProjectId);

            if (dependencyProject == null
                    || (!allowDraftDependencies && dependencyProject.getStatus() == ProjectStatus.DRAFT)
                    || dependencyProject.getVersions() == null
                    || dependencyProject.getVersions().stream().noneMatch(version ->
                    version.getVersionNumber() != null && version.getVersionNumber().equalsIgnoreCase(dependencyVersion))) {
                throw new InvalidVersionRequestException("One or more selected dependencies could not be found.");
            }

            boolean optional = !isModpack && hasDependencyFlag(parts, "optional");
            boolean embedded = hasDependencyFlag(parts, "embedded");
            dependencies.add(new ProjectDependency(
                    dependencyProject.getId(),
                    dependencyProject.getTitle(),
                    dependencyVersion,
                    optional,
                    embedded
            ));
            simpleProjectIds.add(dependencyProject.getId());
        }

        if (isModpack && dependencies.size() < 2) {
            throw new InvalidVersionRequestException("Modpacks must include at least two dependencies.");
        }

        return new ResolvedDependencies(dependencies, simpleProjectIds);
    }

    private boolean hasDependencyFlag(String[] parts, String flag) {
        for (int i = 2; i < parts.length; i++) {
            if (flag.equalsIgnoreCase(parts[i].trim())) {
                return true;
            }
        }
        return false;
    }

    public record ResolvedDependencies(List<ProjectDependency> dependencies, List<String> simpleProjectIds) {
    }
}
