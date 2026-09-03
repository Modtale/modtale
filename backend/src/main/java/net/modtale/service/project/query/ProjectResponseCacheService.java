package net.modtale.service.project.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectCommentsDTO;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.project.ProjectGalleryDTO;
import net.modtale.model.dto.project.ProjectMarqueeDTO;
import net.modtale.model.dto.project.ProjectMetaDTO;
import net.modtale.model.dto.project.ProjectPageDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.project.ProjectTeamDTO;
import net.modtale.model.dto.project.ProjectVersionsDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.util.MongoIdUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ProjectResponseCacheService {

    private final ProjectService projectService;
    private final SearchService searchService;
    private final ProjectRepository projectRepository;
    private final ProjectRouteService projectRouteService;
    private final MongoTemplate mongoTemplate;

    public ProjectResponseCacheService(
            ProjectService projectService,
            SearchService searchService,
            ProjectRepository projectRepository,
            ProjectRouteService projectRouteService,
            MongoTemplate mongoTemplate
    ) {
        this.projectService = projectService;
        this.searchService = searchService;
        this.projectRepository = projectRepository;
        this.projectRouteService = projectRouteService;
        this.mongoTemplate = mongoTemplate;
    }

    @Cacheable(
            value = "projectSummarySearch",
            key = "T(java.util.Arrays).asList(#tags, #search, #page, #size, #sortBy, #gameVersion, #classification, #minDownloads, #minFavorites, #viewCategory, #dateRange, #authorId, #openSource)",
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
            String authorId,
            Boolean openSource
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
                openSource,
                null
        ).map(ProjectMapper::toSummaryDTO);
    }

    @Cacheable(
            value = "projectMarqueeSummarySearch",
            key = "T(java.util.Arrays).asList(#tags, #search, #page, #size, #sortBy, #gameVersion, #classification, #minDownloads, #minFavorites, #viewCategory, #dateRange, #authorId, #openSource)",
            condition = "#viewCategory == null || !#viewCategory.personalView",
            sync = true
    )
    public Page<ProjectMarqueeDTO> searchPublicProjectMarquee(
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
            String authorId,
            Boolean openSource
    ) {
        return searchService.searchProjectMarquee(
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
                openSource
        ).map(ProjectMapper::toMarqueeDTO);
    }

    @Cacheable(value = "projectDetailDtos", key = "'public:' + #id", unless = "#result == null")
    public ProjectDTO getPublicProjectDto(String id) {
        Project project = projectService.getPublicProjectDetailsById(id);
        return project == null ? null : ProjectMapper.toDTO(project, false, null, false);
    }

    @Cacheable(value = "projectDetailDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectDTO getPublicProjectDtoByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectDetailsByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toDTO(project, false, null, false);
    }

    @Cacheable(value = "projectPageDtos", key = "'public:' + #id", unless = "#result == null")
    public ProjectPageDTO getPublicProjectPageDto(String id) {
        Project project = projectService.getPublicProjectPageShellByRouteKey(id);
        return project == null ? null : ProjectMapper.toPageDTO(project);
    }

    @Cacheable(value = "projectPageDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectPageDTO getPublicProjectPageDtoByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectPageShellByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toPageDTO(project);
    }

    @Cacheable(value = "projectVersionDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectVersionsDTO getPublicProjectVersionsByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectVersionsByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toVersionsDTO(project);
    }

    @Cacheable(value = "projectCommentDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectCommentsDTO getPublicProjectCommentsByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectCommentsByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toCommentsDTO(project, null);
    }

    @Cacheable(value = "projectGalleryDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectGalleryDTO getPublicProjectGalleryByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectGalleryByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toGalleryDTO(project);
    }

    @Cacheable(value = "projectTeamDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectTeamDTO getPublicProjectTeamByRouteKey(String routeKey) {
        Project project = projectService.getPublicProjectTeamByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toTeamDTO(project);
    }

    @Cacheable(value = "projectMetaDtos", key = "'public:' + #id", unless = "#result == null")
    public ProjectMetaDTO getPublicProjectMeta(String id) {
        if (id == null || id.isBlank()) return null;
        Project project = projectRepository.findPublicMetaById(id).orElse(null);
        return project == null ? null : ProjectMapper.toMetaDTO(project);
    }

    @Cacheable(value = "projectMetaDtos", key = "'public:' + #routeKey", unless = "#result == null")
    public ProjectMetaDTO getPublicProjectMetaByRouteKey(String routeKey) {
        Project project = resolvePublicMetaByRouteKey(routeKey);
        return project == null ? null : ProjectMapper.toMetaDTO(project);
    }

    public Map<String, ProjectMetaDTO> getPublicProjectMetaByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();

        List<String> normalizedIds = ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) return Map.of();

        Query query = Query.query(Criteria.where("_id").in(MongoIdUtils.expandIds(normalizedIds))
                .and("status").in("PUBLISHED", "UNLISTED", "ARCHIVED")
                .and("deletedAt").is(null));
        query.fields()
                .include("_id")
                .include("slug")
                .include("title")
                .include("description")
                .include("imageUrl")
                .include("author")
                .include("classification")
                .include("downloadCount")
                .include("repositoryUrl")
                .include("status");

        Map<String, ProjectMetaDTO> metas = new LinkedHashMap<>();
        for (Project project : mongoTemplate.find(query, Project.class)) {
            metas.put(project.getId(), ProjectMapper.toMetaDTO(project));
        }
        return metas;
    }

    private Project resolvePublicMetaByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project byId = projectRepository.findPublicMetaById(projectId).orElse(null);
            if (byId != null) return byId;
            return projectRepository.findPublicMetaBySlug(normalized).orElse(null);
        }

        Project bySlug = projectRepository.findPublicMetaBySlug(normalized).orElse(null);
        if (bySlug != null) return bySlug;

        return projectRepository.findPublicMetaById(normalized).orElse(null);
    }

}
