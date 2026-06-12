package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.ProjectRouteService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class PermissionProjectLookupService {

    private final ProjectRepository projectRepository;
    private final ProjectRouteService projectRouteService;

    public PermissionProjectLookupService(ProjectRepository projectRepository, ProjectRouteService projectRouteService) {
        this.projectRepository = projectRepository;
        this.projectRouteService = projectRouteService;
    }

    @Cacheable(value = "projectPermissionSnapshots", key = "#projectId", unless = "#result == null")
    public Project findProject(String projectId) {
        if (projectId == null || projectId.isBlank()) return null;

        String normalized = projectId.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String extractedId = projectRouteService.extractProjectId(normalized);
            Project byId = projectRepository.findById(extractedId).orElse(null);
            if (byId != null) return byId;
            return projectRepository.findBySlug(normalized).orElse(null);
        }

        Project bySlug = projectRepository.findBySlug(normalized).orElse(null);
        if (bySlug != null) return bySlug;

        return projectRepository.findById(normalized).orElse(null);
    }
}
