package net.modtale.controller.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ResourceNotFoundException;
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
import net.modtale.model.dto.project.ProjectVersionChangelogDTO;
import net.modtale.model.dto.request.project.CreateProjectRequest;
import net.modtale.model.dto.request.project.UpdateProjectRequest;
import net.modtale.model.dto.response.project.GameVersionCatalogView;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.model.user.User;
import net.modtale.service.project.catalog.GameVersionService;
import net.modtale.service.project.lifecycle.LifecycleService;
import net.modtale.service.project.lifecycle.ProjectRetentionService;
import net.modtale.service.project.metadata.MetadataService;
import net.modtale.service.project.query.ProjectResponseCacheService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.query.SearchService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.access.PermissionProjectLookupService;
import net.modtale.service.user.account.AccountService;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService projectService;
    private final SearchService searchService;
    private final LifecycleService lifecycleService;
    private final ProjectRetentionService projectRetentionService;
    private final MetadataService metadataService;
    private final ValidationService validationService;
    private final GameVersionService gameVersionService;
    private final ProjectResponseCacheService projectResponseCacheService;
    private final AccessControlService accessControlService;
    private final AccountService accountService;
    private final PermissionProjectLookupService permissionProjectLookupService;

    public ProjectController(
            ProjectService projectService,
            SearchService searchService,
            LifecycleService lifecycleService,
            ProjectRetentionService projectRetentionService,
            MetadataService metadataService,
            ValidationService validationService,
            GameVersionService gameVersionService,
            ProjectResponseCacheService projectResponseCacheService,
            AccessControlService accessControlService,
            AccountService accountService,
            PermissionProjectLookupService permissionProjectLookupService
    ) {
        this.projectService = projectService;
        this.searchService = searchService;
        this.lifecycleService = lifecycleService;
        this.projectRetentionService = projectRetentionService;
        this.metadataService = metadataService;
        this.validationService = validationService;
        this.gameVersionService = gameVersionService;
        this.projectResponseCacheService = projectResponseCacheService;
        this.accessControlService = accessControlService;
        this.accountService = accountService;
        this.permissionProjectLookupService = permissionProjectLookupService;
    }

    @GetMapping("/projects")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<Page<?>> getProjects(
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be 0 or greater.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Size must be at least 1.") @Max(value = 100, message = "Size must be 100 or less.") int size,
            @RequestParam(defaultValue = "relevance")
            @Pattern(
                    regexp = "(?i)relevance|downloads|updated|new|newest|favorites|popular|trending",
                    message = "Sort must be relevance, downloads, updated, new, newest, favorites, popular, or trending."
            )
            String sort,
            @RequestParam(required = false) String gameVersion,
            @RequestParam(required = false, name = "classification")
            @Pattern(
                    regexp = "(?i)PLUGIN|DATA|ART|SAVE|MODPACK",
                    message = "Classification must be PLUGIN, DATA, ART, SAVE, or MODPACK."
            )
            String classification,
            @RequestParam(required = false) Integer minDownloads,
            @RequestParam(required = false) Integer minFavorites,
            @RequestParam(required = false) String category,
            @RequestParam(required = false)
            @Pattern(
                    regexp = "(?i)7d|30d|90d|1y|all|\\d{4}-\\d{2}-\\d{2}.*",
                    message = "Date ranges must be 7d, 30d, 90d, 1y, all, or an ISO-8601 date."
            )
            String dateRange,
            @RequestParam(required = false) Boolean openSource,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false, defaultValue = "catalog")
            @Pattern(
                    regexp = "(?i)catalog|marquee",
                    message = "View must be catalog or marquee."
            )
            String view,
            Authentication authentication
    ) {
        List<String> tagList = tags != null && !tags.trim().isEmpty()
                ? Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().sorted().collect(Collectors.toList())
                : null;

        boolean apiKeyRequest = hasApiRole(authentication);
        ProjectSort sortEnum = ProjectSort.fromQueryValue(sort);
        ProjectViewCategory effectiveCategory = apiKeyRequest
                ? ProjectViewCategory.ALL
                : ProjectViewCategory.fromQueryValue(category);
        ProjectClassification classificationEnum = classification != null ? resolveClassification(classification) : null;
        Boolean openSourceFilter = Boolean.TRUE.equals(openSource) ? Boolean.TRUE : null;
        User currentUser = shouldResolveCatalogViewer(effectiveCategory)
                ? accountService.getCurrentUser(authentication)
                : null;

        boolean marqueeView = "marquee".equalsIgnoreCase(view);
        boolean publicCatalogRequest = currentUser == null && !effectiveCategory.isPersonalView();

        if (marqueeView && publicCatalogRequest) {
            Page<ProjectMarqueeDTO> responsePage = projectResponseCacheService.searchPublicProjectMarquee(
                    tagList,
                    search,
                    page,
                    size,
                    sortEnum,
                    gameVersion,
                    classificationEnum,
                    minDownloads,
                    minFavorites,
                    effectiveCategory,
                    dateRange,
                    authorId,
                    openSourceFilter
            );

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .body(responsePage);
        }

        Page<ProjectSummaryDTO> responsePage = publicCatalogRequest
                ? projectResponseCacheService.searchPublicProjectSummaries(
                        tagList,
                        search,
                        page,
                        size,
                        sortEnum,
                        gameVersion,
                        classificationEnum,
                        minDownloads,
                        minFavorites,
                        effectiveCategory,
                        dateRange,
                        authorId,
                        openSourceFilter
                )
                : searchService.searchProjects(
                        tagList,
                        search,
                        page,
                        size,
                        sortEnum,
                        gameVersion,
                        classificationEnum,
                        minDownloads,
                        minFavorites,
                        effectiveCategory,
                        dateRange,
                        authorId,
                        openSourceFilter,
                        currentUser
                ).map(ProjectMapper::toSummaryDTO);

        CacheControl cacheControl = effectiveCategory.isPersonalView()
                ? CacheControl.noCache()
                : CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(responsePage);
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectPageDTO> getProject(@PathVariable String id, Authentication authentication) {
        ProjectAccess access = resolveProjectAccess(id, authentication);
        if (access.usePublicCache()) {
            ProjectPageDTO project = projectResponseCacheService.getPublicProjectPageDtoByRouteKey(id);
            if (project == null) {
                throwProjectNotFound();
            }
            return publicProjectResponse(project);
        }

        Project project = projectService.getProjectPageShellByRouteKey(id, access.currentUser());
        if (project == null) {
            throwProjectNotFound();
        }

        decoratePrivilegedProject(project, access);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(ProjectMapper.toPageDTO(project));
    }

    @GetMapping("/projects/{id}/details")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectDTO> getProjectDetails(@PathVariable String id, Authentication authentication) {
        ProjectAccess access = resolveProjectAccess(id, authentication);
        if (access.usePublicCache()) {
            ProjectDTO project = projectResponseCacheService.getPublicProjectDtoByRouteKey(id);
            if (project == null) {
                throwProjectNotFound();
            }
            return publicProjectResponse(project);
        }

        Project project = projectService.getProjectDetailsByRouteKey(id, access.currentUser());
        if (project == null) {
            throwProjectNotFound();
        }

        decoratePrivilegedProject(project, access);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(ProjectMapper.toDTO(project, false, access.currentUser() != null ? access.currentUser().getId() : null, false));
    }

    @GetMapping("/projects/{id}/versions")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectVersionsDTO> getProjectVersions(@PathVariable String id, Authentication authentication) {
        ProjectAccess access = resolveProjectAccess(id, authentication);
        if (access.usePublicCache()) {
            ProjectVersionsDTO versions = projectResponseCacheService.getPublicProjectVersionsByRouteKey(id);
            if (versions == null) {
                throwProjectNotFound();
            }
            return publicProjectResponse(versions);
        }

        Project project = projectService.getProjectVersionsByRouteKey(id, access.currentUser());
        if (project == null) {
            throwProjectNotFound();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(ProjectMapper.toVersionsDTO(project));
    }

    @GetMapping("/projects/{id}/comments")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectCommentsDTO> getProjectComments(@PathVariable String id, Authentication authentication) {
        ProjectAccess access = resolveProjectAccess(id, authentication);
        if (access.currentUser() == null) {
            ProjectCommentsDTO comments = projectResponseCacheService.getPublicProjectCommentsByRouteKey(id);
            if (comments == null) {
                throwProjectNotFound();
            }
            return publicProjectResponse(comments);
        }

        Project project = projectService.getProjectCommentsByRouteKey(id, access.currentUser());
        if (project == null) {
            throwProjectNotFound();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(ProjectMapper.toCommentsDTO(project, access.currentUser() != null ? access.currentUser().getId() : null));
    }

    @GetMapping("/projects/{id}/gallery")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectGalleryDTO> getProjectGallery(@PathVariable String id, Authentication authentication) {
        ProjectAccess access = resolveProjectAccess(id, authentication);
        if (access.usePublicCache()) {
            ProjectGalleryDTO gallery = projectResponseCacheService.getPublicProjectGalleryByRouteKey(id);
            if (gallery == null) {
                throwProjectNotFound();
            }
            return publicProjectResponse(gallery);
        }

        Project project = projectService.getProjectGalleryByRouteKey(id, access.currentUser());
        if (project == null) {
            throwProjectNotFound();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(ProjectMapper.toGalleryDTO(project));
    }

    @GetMapping("/projects/{id}/team")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectTeamDTO> getProjectTeam(@PathVariable String id, Authentication authentication) {
        ProjectAccess access = resolveProjectAccess(id, authentication);
        if (access.usePublicCache()) {
            ProjectTeamDTO team = projectResponseCacheService.getPublicProjectTeamByRouteKey(id);
            if (team == null) {
                throwProjectNotFound();
            }
            return publicProjectResponse(team);
        }

        Project project = projectService.getProjectTeamByRouteKey(id, access.currentUser());
        if (project == null) {
            throwProjectNotFound();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(ProjectMapper.toTeamDTO(project));
    }

    @GetMapping("/projects/{id}/versions/changelogs")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<List<ProjectVersionChangelogDTO>> getProjectVersionChangelogs(
            @PathVariable String id,
            Authentication authentication
    ) {
        User currentUser = accountService.getCurrentUser(authentication);
        List<ProjectVersionChangelogDTO> changelogs = projectService.getVersionChangelogsByRouteKey(id, currentUser);
        if (changelogs == null) {
            throw new ResourceNotFoundException("We couldn't find a project with that ID.");
        }

        CacheControl cacheControl = currentUser == null
                ? CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic()
                : CacheControl.noCache();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(changelogs);
    }

    @GetMapping("/projects/{id}/meta")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectMetaDTO> getProjectMeta(@PathVariable String id, Authentication authentication) {
        User currentUser = accountService.getCurrentUser(authentication);
        if (currentUser == null) {
            ProjectMetaDTO meta = projectResponseCacheService.getPublicProjectMetaByRouteKey(id);
            if (meta == null) {
                throw new ResourceNotFoundException("We couldn't find a project with that ID.");
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                    .body(meta);
        }

        Project permissionSnapshot = permissionProjectLookupService.findProject(id);
        boolean privilegedViewer = permissionSnapshot != null
                && (accessControlService.isAdmin(currentUser) || accessControlService.hasEditPermission(permissionSnapshot, currentUser));

        if (permissionSnapshot != null
                && accessControlService.isPubliclyReadable(permissionSnapshot)
                && !privilegedViewer) {
            ProjectMetaDTO meta = projectResponseCacheService.getPublicProjectMetaByRouteKey(id);
            if (meta == null) {
                throw new ResourceNotFoundException("We couldn't find a project with that ID.");
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                    .body(meta);
        }

        Project project = projectService.getProjectByRouteKey(id, currentUser);
        if (project == null) {
            throw new ResourceNotFoundException("We couldn't find a project with that ID.");
        }
        return ResponseEntity.ok(ProjectMapper.toMetaDTO(project));
    }

    @GetMapping("/projects/meta")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<Map<String, ProjectMetaDTO>> getProjectMetaBatch(@RequestParam String ids) {
        if (ids == null || ids.isBlank()) {
            return ResponseEntity.ok(Collections.emptyMap());
        }

        List<String> projectIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .limit(50)
                .collect(Collectors.toList());

        Map<String, ProjectMetaDTO> metas = projectIds.isEmpty()
                ? Collections.emptyMap()
                : projectResponseCacheService.getPublicProjectMetaByIds(projectIds);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(new LinkedHashMap<>(metas));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(validationService.getAllowedTags());
    }

    @GetMapping("/meta/classifications")
    public ResponseEntity<List<ProjectClassification>> getClassifications() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(validationService.getAllowedClassifications());
    }

    @GetMapping("/meta/game-versions")
    public ResponseEntity<List<String>> getGameVersions() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(validationService.getAllowedGameVersions());
    }

    @GetMapping("/meta/game-versions/catalog")
    public ResponseEntity<GameVersionCatalogView> getGameVersionCatalog() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(GameVersionCatalogView.from(gameVersionService.getCatalog()));
    }

    @PostMapping("/projects")
    @PreAuthorize("@apiSecurity.hasCreateProjectPerm(#requestPayload.owner, authentication)")
    public ResponseEntity<ProjectDTO> createProject(
            @Valid @ModelAttribute CreateProjectRequest requestPayload,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "creating a project draft");
        Project project = lifecycleService.createDraft(
                requestPayload.getTitle(),
                requestPayload.getDescription(),
                requestPayload.getClassification(),
                user,
                requestPayload.getOwner(),
                requestPayload.getSlug()
        );
        project.setCanEdit(true);
        project.setIsOwner(true);
        return ResponseEntity.ok(ProjectMapper.toDTO(project, false));
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_EDIT_METADATA', authentication)")
    public ResponseEntity<Void> updateProject(
            @PathVariable String id,
            @Valid @RequestBody UpdateProjectRequest requestPayload,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "editing project metadata");
        Project existing = projectService.getRawProjectById(id);
        Project updated = new Project();
        updated.setTitle(requestPayload.getTitle());
        updated.setSlug(requestPayload.getSlug());
        updated.setDescription(requestPayload.getDescription());
        updated.setAbout(requestPayload.getAbout());
        updated.setTags(requestPayload.getTags());
        updated.setLinks(requestPayload.getLinks());
        updated.setRepositoryUrl(requestPayload.getRepositoryUrl());
        updated.setLicense(requestPayload.getLicense());
        if (requestPayload.getAllowModpacks() != null) updated.setAllowModpacks(requestPayload.getAllowModpacks());
        else if (existing != null) updated.setAllowModpacks(existing.isAllowModpacks());
        if (requestPayload.getAllowComments() != null) updated.setAllowComments(requestPayload.getAllowComments());
        else if (existing != null) updated.setAllowComments(existing.isAllowComments());
        if (requestPayload.getHmWikiEnabled() != null) updated.setHmWikiEnabled(requestPayload.getHmWikiEnabled());
        else if (existing != null) updated.setHmWikiEnabled(existing.isHmWikiEnabled());
        if (requestPayload.getHmWikiSlug() != null) updated.setHmWikiSlug(requestPayload.getHmWikiSlug());
        else if (existing != null) updated.setHmWikiSlug(existing.getHmWikiSlug());
        if (requestPayload.getGalleryCarouselEnabled() != null) updated.setGalleryCarouselEnabled(requestPayload.getGalleryCarouselEnabled());
        else if (existing != null) updated.setGalleryCarouselEnabled(existing.isGalleryCarouselEnabled());
        metadataService.updateMetadata(id, updated, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_DELETE', authentication)")
    public ResponseEntity<Void> deleteProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "deleting a project");
        projectRetentionService.softDeleteProject(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/submit")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_SUBMIT', authentication)")
    public ResponseEntity<Void> submitProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "submitting a project for review");
        lifecycleService.submitProject(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/revert")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_REVERT', authentication)")
    public ResponseEntity<Void> revertProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "reverting a project to draft");
        lifecycleService.revertProjectToDraft(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/archive")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_ARCHIVE', authentication)")
    public ResponseEntity<Void> archiveProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "archiving a project");
        lifecycleService.archiveProject(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/unlist")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_UNLIST', authentication)")
    public ResponseEntity<Void> unlistProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "unlisting a project");
        lifecycleService.unlistProject(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/private")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_UNLIST', authentication)")
    public ResponseEntity<Void> privateProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "making a project private");
        lifecycleService.privateProject(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/publish")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_PUBLISH', authentication)")
    public ResponseEntity<Void> publishProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "publishing a project");
        lifecycleService.publishProject(id, user);
        return ResponseEntity.ok().build();
    }

    private ProjectAccess resolveProjectAccess(String id, Authentication authentication) {
        User currentUser = accountService.getCurrentUser(authentication);
        if (currentUser == null) {
            return new ProjectAccess(null, null, false, true);
        }

        Project permissionSnapshot = permissionProjectLookupService.findProject(id);
        boolean privilegedViewer = permissionSnapshot != null
                && (accessControlService.isAdmin(currentUser) || accessControlService.hasEditPermission(permissionSnapshot, currentUser));
        boolean publicReader = permissionSnapshot != null
                && accessControlService.isPubliclyReadable(permissionSnapshot)
                && !privilegedViewer;

        return new ProjectAccess(currentUser, permissionSnapshot, privilegedViewer, publicReader);
    }

    private void decoratePrivilegedProject(Project project, ProjectAccess access) {
        if (!access.privilegedViewer() || access.currentUser() == null) {
            return;
        }

        project.setCanEdit(accessControlService.hasEditPermission(project, access.currentUser()));
        project.setIsOwner(accessControlService.isOwner(project, access.currentUser()));
    }

    private <T> ResponseEntity<T> publicProjectResponse(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(body);
    }

    private void throwProjectNotFound() {
        throw new ResourceNotFoundException("We couldn't find a project with that ID.");
    }

    private record ProjectAccess(User currentUser, Project permissionSnapshot, boolean privilegedViewer, boolean publicReader) {
        boolean usePublicCache() {
            return currentUser == null || publicReader;
        }
    }

    private boolean hasApiRole(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_API"));
    }

    private boolean shouldResolveCatalogViewer(ProjectViewCategory viewCategory) {
        return viewCategory.isPersonalView();
    }

    private ProjectClassification resolveClassification(String rawClassification) {
        try {
            return ProjectClassification.valueOf(rawClassification.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidProjectRequestException("Project classifications must be PLUGIN, DATA, ART, SAVE, or MODPACK.");
        }
    }
}
