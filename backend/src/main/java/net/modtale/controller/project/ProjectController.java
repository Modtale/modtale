package net.modtale.controller.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.project.ProjectMetaDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.request.project.CreateProjectRequest;
import net.modtale.model.dto.request.project.UpdateProjectRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.user.User;
import net.modtale.service.project.GameVersionService;
import net.modtale.service.project.LifecycleService;
import net.modtale.service.project.MetadataService;
import net.modtale.service.project.ProjectRetentionService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.SearchService;
import net.modtale.service.project.ValidationService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final AccessControlService accessControlService;
    private final AccountService accountService;

    public ProjectController(
            ProjectService projectService,
            SearchService searchService,
            LifecycleService lifecycleService,
            ProjectRetentionService projectRetentionService,
            MetadataService metadataService,
            ValidationService validationService,
            GameVersionService gameVersionService,
            AccessControlService accessControlService,
            AccountService accountService
    ) {
        this.projectService = projectService;
        this.searchService = searchService;
        this.lifecycleService = lifecycleService;
        this.projectRetentionService = projectRetentionService;
        this.metadataService = metadataService;
        this.validationService = validationService;
        this.gameVersionService = gameVersionService;
        this.accessControlService = accessControlService;
        this.accountService = accountService;
    }

    @GetMapping("/projects")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<Page<ProjectSummaryDTO>> getProjects(
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be 0 or greater.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Size must be at least 1.") @Max(value = 100, message = "Size must be 100 or less.") int size,
            @RequestParam(defaultValue = "relevance")
            @Pattern(
                    regexp = "(?i)relevance|downloads|updated|new|newest|favorites",
                    message = "Sort must be relevance, downloads, updated, new, newest, or favorites."
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
            @RequestParam(required = false) String authorId,
            Authentication authentication
    ) {
        List<String> tagList = tags != null && !tags.trim().isEmpty()
                ? Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                : null;

        User currentUser = accountService.getCurrentUser(authentication);
        boolean apiKeyRequest = hasApiRole(authentication);
        String effectiveCategory = apiKeyRequest ? null : category;

        Page<Project> data = searchService.searchProjects(
                tagList,
                search,
                page,
                size,
                sort,
                gameVersion,
                classification,
                minDownloads,
                minFavorites,
                effectiveCategory,
                dateRange,
                authorId,
                currentUser
        );

        CacheControl cacheControl = ("Favorites".equals(effectiveCategory) || "Your Projects".equals(effectiveCategory))
                ? CacheControl.noCache()
                : CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(data.map(ProjectMapper::toSummaryDTO));
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable String id, Authentication authentication) {
        User currentUser = accountService.getCurrentUser(authentication);
        Project project = projectService.getProjectById(id, currentUser);
        if (project == null) {
            throw new ResourceNotFoundException("We couldn't find a project with that ID.");
        }

        if (currentUser != null) {
            project.setCanEdit(accessControlService.hasEditPermission(project, currentUser));
            project.setIsOwner(accessControlService.isOwner(project, currentUser));
        }

        boolean publicProject = accessControlService.isPubliclyReadable(project);
        CacheControl cacheControl = currentUser == null && publicProject
                ? CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic()
                : CacheControl.noCache();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(ProjectMapper.toDTO(project, false, currentUser != null ? currentUser.getId() : null));
    }

    @GetMapping("/projects/{id}/meta")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectMetaDTO> getProjectMeta(@PathVariable String id, Authentication authentication) {
        Project project = projectService.getProjectById(id, accountService.getCurrentUser(authentication));
        if (project == null) {
            throw new ResourceNotFoundException("We couldn't find a project with that ID.");
        }
        return ResponseEntity.ok(ProjectMapper.toMetaDTO(project));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(validationService.getAllowedTags());
    }

    @GetMapping("/meta/classifications")
    public ResponseEntity<List<String>> getClassifications() {
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
    public ResponseEntity<GameVersionService.GameVersionCatalog> getGameVersionCatalog() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(gameVersionService.getCatalog());
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
                resolveClassification(requestPayload.getClassification()),
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

    @PostMapping("/projects/{id}/publish")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_PUBLISH', authentication)")
    public ResponseEntity<Void> publishProject(@PathVariable String id, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "publishing a project");
        lifecycleService.publishProject(id, user);
        return ResponseEntity.ok().build();
    }

    private boolean hasApiRole(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_API"));
    }

    private ProjectClassification resolveClassification(String rawClassification) {
        try {
            return ProjectClassification.valueOf(rawClassification.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidProjectRequestException("Project classifications must be PLUGIN, DATA, ART, SAVE, or MODPACK.");
        }
    }
}
