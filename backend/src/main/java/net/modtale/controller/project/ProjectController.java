package net.modtale.controller.project;

import net.modtale.exception.ErrorMessageUtils;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.project.ProjectMetaDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.request.project.CreateProjectRequest;
import net.modtale.model.dto.request.project.UpdateProjectRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.*;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    @Autowired private ProjectService projectService;
    @Autowired private SearchService searchService;
    @Autowired private LifecycleService lifecycleService;
    @Autowired private MetadataService metadataService;
    @Autowired private ValidationService validationService;
    @Autowired private GameVersionService gameVersionService;
    @Autowired private AccessControlService accessControlService;
    @Autowired private AccountService accountService;
    @Autowired private UserRepository userRepository;

    @GetMapping("/projects")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<Page<ProjectSummaryDTO>> getProjects(
            @RequestParam(required = false) String tags, @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "relevance") String sort, @RequestParam(required = false) String gameVersion,
            @RequestParam(required = false, name = "classification") String classification,
            @RequestParam(required = false) Integer minDownloads, @RequestParam(required = false) Integer minFavorites,
            @RequestParam(required = false) String category, @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) String author, @RequestParam(required = false) String creator
    ) {
        List<String> tagList = tags != null && !tags.trim().isEmpty() ? Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()) : null;
        String effectiveAuthor = author != null && !author.isEmpty() ? author : creator;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isApiKeyUser = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API"));

        Page<Project> data = searchService.searchProjects(tagList, search, page, size, sort, gameVersion, classification, minDownloads, minFavorites, isApiKeyUser ? null : category, dateRange, effectiveAuthor);

        CacheControl cacheControl = ("Favorites".equals(isApiKeyUser ? null : category) || "Your Projects".equals(isApiKeyUser ? null : category)) ? CacheControl.noCache() : CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
        return ResponseEntity.ok().cacheControl(cacheControl).body(data.map(ProjectMapper::toSummaryDTO));
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<?> getProject(@PathVariable String id) {
        Project project = projectService.getProjectById(id);
        if (project == null) return ErrorMessageUtils.notFound("We couldn't find a project with that ID.");

        if (project.getStatus() == ProjectStatus.DRAFT || project.getStatus() == ProjectStatus.PENDING) {
            User user = accountService.getCurrentUser();
            if (!accessControlService.isAdmin(user) && (user == null || !accessControlService.hasProjectPermission(project, user, "PROJECT_READ"))) {
                return ErrorMessageUtils.forbidden("This draft or pending project is only visible to collaborators and administrators.");
            }
        }

        User user = accountService.getCurrentUser();
        if (user != null) {
            project.setCanEdit(accessControlService.hasEditPermission(project, user));
            project.setIsOwner(accessControlService.isOwner(project, user));
        }

        return ResponseEntity.ok(ProjectMapper.toDTO(project, false, user != null ? user.getId() : null));
    }

    @GetMapping("/projects/{id}/meta")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<ProjectMetaDTO> getProjectMeta(@PathVariable String id) {
        Project project = projectService.getProjectById(id);
        if (project == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(ProjectMapper.toMetaDTO(project));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic()).body(validationService.getAllowedTags());
    }

    @GetMapping("/meta/classifications")
    public ResponseEntity<List<String>> getClassifications() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic()).body(validationService.getAllowedClassifications());
    }

    @GetMapping("/meta/game-versions")
    public ResponseEntity<List<String>> getGameVersions() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic()).body(validationService.getAllowedGameVersions());
    }

    @GetMapping("/meta/game-versions/catalog")
    public ResponseEntity<GameVersionService.GameVersionCatalog> getGameVersionCatalog() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(gameVersionService.getCatalog());
    }

    @PostMapping("/projects")
    @PreAuthorize("@apiSecurity.hasCreateProjectPerm(#requestPayload.owner, authentication)")
    public ResponseEntity<?> createProject(@ModelAttribute CreateProjectRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before creating a project draft.");
        try {
            ProjectClassification classificationEnum = ProjectClassification.valueOf(requestPayload.getClassification().toUpperCase());
            Project project = lifecycleService.createDraft(
                    requestPayload.getTitle(),
                    requestPayload.getDescription(),
                    classificationEnum,
                    user,
                    requestPayload.getOwner(),
                    requestPayload.getSlug()
            );
            project.setCanEdit(true);
            project.setIsOwner(true);
            return ResponseEntity.ok(ProjectMapper.toDTO(project, false));
        }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to create a draft for that owner."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not create that project draft."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to create project draft."); }
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_EDIT_METADATA', authentication)")
    public ResponseEntity<?> updateProject(@PathVariable String id, @RequestBody UpdateProjectRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before editing project metadata.");
        try {
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

            if (updated.getDescription() != null && updated.getDescription().length() > 250) return ErrorMessageUtils.badRequest("The short summary cannot exceed 250 characters.");
            if (updated.getAbout() != null && updated.getAbout().length() > 50000) return ErrorMessageUtils.badRequest("The full description cannot exceed 50,000 characters.");
            metadataService.updateMetadata(id, updated, user);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) { return ErrorMessageUtils.badRequest(e, "We could not update that project."); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to edit this project."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to update project."); }
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_DELETE', authentication)")
    public ResponseEntity<?> deleteProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before deleting a project.");
        try { lifecycleService.softDeleteProject(id, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to delete this project."); }
        catch (IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not delete that project."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to delete project."); }
    }

    @PostMapping("/projects/{id}/submit")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_SUBMIT', authentication)")
    public ResponseEntity<?> submitProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before submitting a project for review.");
        try { lifecycleService.submitProject(id, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to submit this project."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not submit that project for review."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to submit project."); }
    }

    @PostMapping("/projects/{id}/revert")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_REVERT', authentication)")
    public ResponseEntity<?> revertProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before reverting a project to draft.");
        try { lifecycleService.updateProjectStatus(id, ProjectStatus.DRAFT, user, "PROJECT_STATUS_REVERT"); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to revert this project."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not revert that project to draft."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to revert project to draft."); }
    }

    @PostMapping("/projects/{id}/archive")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_ARCHIVE', authentication)")
    public ResponseEntity<?> archiveProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before archiving a project.");
        try { lifecycleService.updateProjectStatus(id, ProjectStatus.ARCHIVED, user, "PROJECT_STATUS_ARCHIVE"); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to archive this project."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not archive that project."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to archive project."); }
    }

    @PostMapping("/projects/{id}/unlist")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_UNLIST', authentication)")
    public ResponseEntity<?> unlistProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before unlisting a project.");
        try { lifecycleService.updateProjectStatus(id, ProjectStatus.UNLISTED, user, "PROJECT_STATUS_UNLIST"); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to unlist this project."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not unlist that project."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to unlist project."); }
    }

    @PostMapping("/projects/{id}/publish")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_PUBLISH', authentication)")
    public ResponseEntity<?> publishProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before publishing a project.");
        try { lifecycleService.publishProject(id, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to publish this project."); }
        catch (IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not publish that project."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to publish project."); }
    }
}
