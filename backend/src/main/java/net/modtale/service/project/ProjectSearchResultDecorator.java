package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectSearchResultDecorator {

    private final UserRepository userRepository;

    public ProjectSearchResultDecorator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<Project> decorateCatalogResults(Page<Project> results) {
        if (results.hasContent()) {
            results.getContent().forEach(this::sanitizeAndPopulateAuthor);
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
        projects.forEach(this::sanitizeAndPopulateAuthor);
        return projects;
    }

    private void sanitizeAndPopulateAuthor(Project project) {
        sanitizeVersionResults(project);
        if (project.getAuthor() == null && project.getAuthorId() != null) {
            userRepository.findById(project.getAuthorId()).ifPresent(user -> project.setAuthor(user.getUsername()));
        }
    }

    private void sanitizeVersionResults(Project project) {
        if (project.getVersions() != null) {
            project.getVersions().forEach(version -> version.setScanResult(null));
        }
    }
}
