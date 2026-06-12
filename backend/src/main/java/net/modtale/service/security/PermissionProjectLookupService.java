package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class PermissionProjectLookupService {

    private final ProjectRepository projectRepository;

    public PermissionProjectLookupService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Cacheable(value = "projectPermissionSnapshots", key = "#projectId", unless = "#result == null")
    public Project findProject(String projectId) {
        return projectRepository.findById(projectId).orElse(null);
    }
}
