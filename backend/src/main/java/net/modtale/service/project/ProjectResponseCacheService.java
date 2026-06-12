package net.modtale.service.project;

import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.project.ProjectMetaDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectResponseCacheService {

    private final ProjectService projectService;
    private final SearchService searchService;

    public ProjectResponseCacheService(ProjectService projectService, SearchService searchService) {
        this.projectService = projectService;
        this.searchService = searchService;
    }

    @Cacheable(
            value = "projectSummarySearch",
            key = "T(java.util.Arrays).asList(#tags, #search, #page, #size, #sortBy, #gameVersion, #classification, #minDownloads, #minFavorites, #viewCategory, #dateRange, #authorId)",
            condition = "#viewCategory == null || !#viewCategory.personalView",
            sync = true
    )
    public Page<ProjectSummaryDTO> searchPublicProjectSummaries(
            List<String> tags,
            String search,
            int page,
            int size,
            ProjectSort sortBy,
            String gameVersion,
            ProjectClassification classification,
            Integer minDownloads,
            Integer minFavorites,
            ProjectViewCategory viewCategory,
            String dateRange,
            String authorId
    ) {
        return searchService.searchProjects(
                tags,
                search,
                page,
                size,
                sortBy,
                gameVersion,
                classification,
                minDownloads,
                minFavorites,
                viewCategory,
                dateRange,
                authorId,
                null
        ).map(ProjectMapper::toSummaryDTO);
    }

    @Cacheable(value = "projectDetailDtos", key = "'public:' + #id", unless = "#result == null")
    public ProjectDTO getPublicProjectDto(String id) {
        Project project = projectService.getPublicProjectById(id);
        return project == null ? null : ProjectMapper.toDTO(project, false, null);
    }

    @Cacheable(value = "projectDetailDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectDTO getPublicProjectDtoByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toDTO(project, false, null);
    }

    @Cacheable(value = "projectMetaDtos", key = "'public:' + #id", unless = "#result == null")
    public ProjectMetaDTO getPublicProjectMeta(String id) {
        Project project = projectService.getPublicProjectById(id);
        return project == null ? null : ProjectMapper.toMetaDTO(project);
    }

    @Cacheable(value = "projectMetaDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectMetaDTO getPublicProjectMetaByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toMetaDTO(project);
    }
}
