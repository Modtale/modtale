package net.modtale.service.project.query;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class ProjectSearchResultDecorator {

    private final UserRepository userRepository;

    public ProjectSearchResultDecorator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<Project> decorateCatalogResults(Page<Project> results) {
        if (results.hasContent()) {
            hydrateAuthors(results.getContent());
            results.getContent().forEach(this::sanitizeVersionResults);
        }
        return results;
    }

    public Page<Project> decorateCreatorResults(Page<Project> results, User creator, boolean sanitizeVersions) {
        if (results.hasContent()) {
            results.getContent().forEach(project -> {
                if (sanitizeVersions) {
                    sanitizeVersionResults(project);
                }
                project.setAuthor(creator.getUsername());
            });
        }
        return results;
    }

    public List<Project> decorateContributedProjects(List<Project> projects) {
        hydrateAuthors(projects);
        projects.forEach(this::sanitizeVersionResults);
        return projects;
    }

    private void hydrateAuthors(List<Project> projects) {
        Set<String> authorIds = projects.stream()
                .filter(project -> project.getAuthor() == null || project.getAuthor().isBlank())
                .map(Project::getAuthorId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) {
            return;
        }

        Map<String, String> usernamesById = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(user -> usernamesById.put(user.getId(), user.getUsername()));

        projects.forEach(project -> {
            if (project.getAuthor() == null && project.getAuthorId() != null) {
                String username = usernamesById.get(project.getAuthorId());
                if (username != null) {
                    project.setAuthor(username);
                }
            }
        });
    }

    private void sanitizeVersionResults(Project project) {
        if (project.getVersions() != null) {
            project.getVersions().forEach(version -> version.setScanResult(null));
        }
    }
}
