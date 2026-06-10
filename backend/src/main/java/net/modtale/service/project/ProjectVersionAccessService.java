package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

@Service
public class ProjectVersionAccessService {

    private final ValidationService validationService;

    public ProjectVersionAccessService(ValidationService validationService) {
        this.validationService = validationService;
    }

    public ProjectVersion findById(Project project, String versionId) {
        if (project == null || project.getVersions() == null) {
            return null;
        }
        return project.getVersions().stream()
                .filter(version -> version.getId().equals(versionId))
                .findFirst()
                .orElse(null);
    }

    public ProjectVersion requireById(
            Project project,
            String versionId,
            Supplier<? extends RuntimeException> exceptionSupplier
    ) {
        ProjectVersion version = findById(project, versionId);
        if (version == null) {
            throw exceptionSupplier.get();
        }
        return version;
    }

    public ProjectVersion findByVersionNumber(Project project, String versionNumber, String gameVersion) {
        if (project == null || project.getVersions() == null || project.getVersions().isEmpty()) {
            return null;
        }
        if ("latest".equalsIgnoreCase(versionNumber)) {
            return project.getVersions().getFirst();
        }

        List<ProjectVersion> matches = project.getVersions().stream()
                .filter(version -> version.getVersionNumber() != null && version.getVersionNumber().equalsIgnoreCase(versionNumber))
                .toList();
        if (matches.isEmpty()) {
            return null;
        }
        if (gameVersion != null && !gameVersion.isBlank()) {
            return matches.stream()
                    .filter(version -> version.getGameVersions() != null
                            && version.getGameVersions().stream().anyMatch(existing -> gameVersion.equalsIgnoreCase(existing)))
                    .findFirst()
                    .orElse(null);
        }
        return selectForLatestGameVersion(matches);
    }

    public ProjectVersion requireByVersionNumber(
            Project project,
            String versionNumber,
            String gameVersion,
            Supplier<? extends RuntimeException> exceptionSupplier
    ) {
        ProjectVersion version = findByVersionNumber(project, versionNumber, gameVersion);
        if (version == null) {
            throw exceptionSupplier.get();
        }
        return version;
    }

    private ProjectVersion selectForLatestGameVersion(List<ProjectVersion> versions) {
        List<String> allowed = validationService.getAllowedGameVersions();
        if (allowed != null && !allowed.isEmpty()) {
            for (String gameVersion : allowed) {
                ProjectVersion match = versions.stream()
                        .filter(version -> version.getGameVersions() != null
                                && version.getGameVersions().stream().anyMatch(existing -> gameVersion.equalsIgnoreCase(existing)))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    return match;
                }
            }
        }
        return versions.stream()
                .max(Comparator.comparing(ProjectVersion::getReleaseDate, Comparator.nullsLast(String::compareTo)))
                .orElse(versions.getFirst());
    }
}
