package net.modtale.controller.project;

import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.ProjectDTO;
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
import java.util.Map;
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
    @Autowired private AccessControlService accessControlService;
    @Autowired private AccountService accountService;
    @Autowired private UserRepository userRepository;

    @GetMapping("/projects")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<Page<ProjectDTO>> getProjects(
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
        return ResponseEntity.ok().cacheControl(cacheControl).body(data.map(p -> ProjectMapper.toDTO(p, true)));
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<?> getProject(@PathVariable String id) {
        Project project = projectService.getProjectById(id);
        if (project == null) return ResponseEntity.notFound().build();

        if (project.getStatus() == ProjectStatus.DRAFT || project.getStatus() == ProjectStatus.PENDING) {
            User user = accountService.getCurrentUser();
            if (!accessControlService.isAdmin(user) && (user == null || !accessControlService.hasProjectPermission(project, user, "PROJECT_READ"))) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        User user = accountService.getCurrentUser();
        if (user != null) {
            project.setCanEdit(accessControlService.hasEditPermission(project, user));
            project.setIsOwner(accessControlService.isOwner(project, user));
        }

        return ResponseEntity.ok(ProjectMapper.toDTO(project, false));
    }

    @GetMapping("/projects/{id}/meta")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<Map<String, Object>> getProjectMeta(@PathVariable String id) {
        Project project = projectService.getProjectById(id);
        if (project == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("title", project.getTitle(), "description", project.getDescription() != null ? project.getDescription() : "", "icon", project.getImageUrl() != null ? project.getImageUrl() : "", "author", project.getAuthor(), "classification", project.getClassification().name(), "downloads", project.getDownloadCount(), "repositoryUrl", project.getRepositoryUrl() != null ? project.getRepositoryUrl() : "", "slug", project.getSlug() != null ? project.getSlug() : project.getId()));
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

    @PostMapping("/projects")
    @PreAuthorize("@apiSecurity.hasCreateProjectPerm(#owner, authentication)")
    public ResponseEntity<?> createProject(@RequestParam("title") String title, @RequestParam("classification") String classification, @RequestParam("description") String description, @RequestParam(value = "owner", required = false) String owner, @RequestParam(value = "slug", required = false) String slug) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            ProjectClassification classificationEnum = ProjectClassification.valueOf(classification.toUpperCase());
            return ResponseEntity.ok(lifecycleService.createDraft(title, description, classificationEnum, user, owner, slug));
        }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_EDIT_METADATA', authentication)")
    public ResponseEntity<?> updateProject(@PathVariable String id, @RequestBody Project updated) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            if (updated.getDescription() != null && updated.getDescription().length() > 250) return ResponseEntity.badRequest().body("Short Summary cannot exceed 250 characters.");
            if (updated.getAbout() != null && updated.getAbout().length() > 50000) return ResponseEntity.badRequest().body("Full Description cannot exceed 50,000 characters.");
            metadataService.updateMetadata(id, updated, user);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        catch (SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_DELETE', authentication)")
    public ResponseEntity<?> deleteProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { lifecycleService.softDeleteProject(id, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/submit")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_SUBMIT', authentication)")
    public ResponseEntity<?> submitProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { lifecycleService.submitProject(id, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/revert")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_REVERT', authentication)")
    public ResponseEntity<?> revertProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { lifecycleService.updateProjectStatus(id, ProjectStatus.DRAFT, user, "PROJECT_STATUS_REVERT"); return ResponseEntity.ok().build(); }
        catch (Exception e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/archive")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_ARCHIVE', authentication)")
    public ResponseEntity<?> archiveProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { lifecycleService.updateProjectStatus(id, ProjectStatus.ARCHIVED, user, "PROJECT_STATUS_ARCHIVE"); return ResponseEntity.ok().build(); }
        catch (Exception e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/unlist")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_UNLIST', authentication)")
    public ResponseEntity<?> unlistProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { lifecycleService.updateProjectStatus(id, ProjectStatus.UNLISTED, user, "PROJECT_STATUS_UNLIST"); return ResponseEntity.ok().build(); }
        catch (Exception e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/publish")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_STATUS_PUBLISH', authentication)")
    public ResponseEntity<?> publishProject(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { lifecycleService.publishProject(id, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }
}