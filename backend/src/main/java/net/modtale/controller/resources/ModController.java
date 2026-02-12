package net.modtale.controller.resources;

import net.modtale.model.user.User;
import net.modtale.model.resources.*;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.AnalyticsService;
import net.modtale.service.DownloadTokenService;
import net.modtale.service.user.UserService;
import net.modtale.service.resources.ModService;
import net.modtale.service.resources.StorageService;
import net.modtale.service.security.FileValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ModController {

    private static final Logger logger = LoggerFactory.getLogger(ModController.class);
    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    @Autowired private ModService modService;
    @Autowired private UserService userService;
    @Autowired private StorageService storageService;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private FileValidationService validationService;
    @Autowired private UserRepository userRepository;
    @Autowired private DownloadTokenService downloadTokenService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private boolean isAdminOrSuper(User user) {
        if (user == null) return false;
        if (SUPER_ADMIN_ID.equals(user.getId())) return true;
        return user.getRoles() != null && user.getRoles().contains("ADMIN");
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    @PutMapping("/projects/{id}/icon")
    public ResponseEntity<?> updateProjectIcon(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            validationService.validateIcon(file);
            modService.updateProjectIcon(id, file);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/projects/{id}/banner")
    public ResponseEntity<?> updateProjectBanner(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            validationService.validateBanner(file);
            modService.updateProjectBanner(id, file);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/projects/{id}/versions/{version}/download-url")
    public ResponseEntity<?> getDownloadUrl(@PathVariable String id, @PathVariable String version) {
        try {
            Mod mod = modService.getModById(id);
            if (mod == null) return ResponseEntity.notFound().build();

            if ("DRAFT".equals(mod.getStatus()) || "PENDING".equals(mod.getStatus())) {
                User user = userService.getCurrentUser();
                if (!isAdminOrSuper(user)) {
                    if (user == null || !modService.hasEditPermission(mod, user)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    }
                }
            }

            ModVersion targetVersion = mod.getVersions().stream()
                    .filter(v -> v.getVersionNumber().equalsIgnoreCase(version))
                    .findFirst()
                    .orElse(null);

            if (targetVersion == null) return ResponseEntity.notFound().build();

            String token = downloadTokenService.generateToken(id, version);
            String downloadUrl = "/download/" + token;

            return ResponseEntity.ok(Map.of(
                    "downloadUrl", downloadUrl,
                    "expiresIn", 300
            ));
        } catch (Exception e) {
            logger.error("Error generating download URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> downloadWithToken(@PathVariable String token, HttpServletRequest request) {
        try {
            DownloadTokenService.DownloadToken downloadToken = downloadTokenService.validateAndConsume(token);

            if (downloadToken == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(null);
            }

            String id = downloadToken.getProjectId();
            String version = downloadToken.getVersion();

            Mod mod = modService.getModById(id);
            if (mod == null) return ResponseEntity.notFound().build();

            boolean isApi = false;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API"))) {
                isApi = true;
            } else {
                String referer = request.getHeader("Referer");
                if (referer == null || !referer.startsWith(frontendUrl)) {
                    isApi = true;
                }
            }

            String clientIp = getClientIp(request);

            if ("MODPACK".equals(mod.getClassification())) {
                ModVersion targetVersion = modService.findVersion(mod, version);
                if (targetVersion == null) return ResponseEntity.notFound().build();

                modService.incrementDownloadCount(mod.getId());
                analyticsService.logDownload(mod.getId(), targetVersion.getId(), mod.getAuthor(), isApi, clientIp);

                if (targetVersion.getDependencies() != null) {
                    for (ModDependency dep : targetVersion.getDependencies()) {
                        modService.incrementDownloadCount(dep.getModId());
                        analyticsService.logDownload(dep.getModId(), null, null, isApi, clientIp);
                    }
                }

                byte[] zipData = modService.generateModpackZip(mod, targetVersion);
                ByteArrayResource resource = new ByteArrayResource(zipData);
                String zipFilename = mod.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + "-" + targetVersion.getVersionNumber() + ".zip";

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            }

            ModVersion targetVersion = mod.getVersions().stream()
                    .filter(v -> v.getVersionNumber().equalsIgnoreCase(version))
                    .findFirst()
                    .orElse(null);

            if (targetVersion == null) return ResponseEntity.notFound().build();

            byte[] data = storageService.download(targetVersion.getFileUrl());
            ByteArrayResource resource = new ByteArrayResource(data);

            modService.incrementDownloadCount(mod.getId());
            analyticsService.logDownload(mod.getId(), targetVersion.getId(), mod.getAuthor(), isApi, clientIp);

            String originalPath = targetVersion.getFileUrl();
            String filename = originalPath.contains("/") ? originalPath.substring(originalPath.lastIndexOf('/') + 1) : originalPath;
            if (filename.length() > 37 && filename.charAt(36) == '-') {
                filename = filename.substring(37);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error processing download", e);
            return ResponseEntity.notFound().build();
        }
    }

    @Deprecated
    @GetMapping("/projects/{id}/versions/{version}/download")
    public ResponseEntity<Resource> downloadVersion(@PathVariable String id, @PathVariable String version, HttpServletRequest request) {
        try {
            Mod mod = modService.getModById(id);
            if (mod == null) return ResponseEntity.notFound().build();

            if ("DRAFT".equals(mod.getStatus()) || "PENDING".equals(mod.getStatus())) {
                User user = userService.getCurrentUser();
                if (!isAdminOrSuper(user)) {
                    if (user == null || !modService.hasEditPermission(mod, user)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    }
                }
            }

            boolean isApi = false;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API"))) {
                isApi = true;
            } else {
                String referer = request.getHeader("Referer");
                if (referer == null || !referer.startsWith(frontendUrl)) {
                    isApi = true;
                }
            }

            String clientIp = getClientIp(request);

            if ("MODPACK".equals(mod.getClassification())) {
                ModVersion targetVersion = modService.findVersion(mod, version);
                if (targetVersion == null) return ResponseEntity.notFound().build();

                modService.incrementDownloadCount(mod.getId());
                analyticsService.logDownload(mod.getId(), targetVersion.getId(), mod.getAuthor(), isApi, clientIp);

                if (targetVersion.getDependencies() != null) {
                    for (ModDependency dep : targetVersion.getDependencies()) {
                        modService.incrementDownloadCount(dep.getModId());
                        analyticsService.logDownload(dep.getModId(), null, null, isApi, clientIp);
                    }
                }

                byte[] zipData = modService.generateModpackZip(mod, targetVersion);
                ByteArrayResource resource = new ByteArrayResource(zipData);
                String zipFilename = mod.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + "-" + targetVersion.getVersionNumber() + ".zip";

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            }

            ModVersion targetVersion = mod.getVersions().stream()
                    .filter(v -> v.getVersionNumber().equalsIgnoreCase(version))
                    .findFirst()
                    .orElse(null);

            if (targetVersion == null) return ResponseEntity.notFound().build();

            byte[] data = storageService.download(targetVersion.getFileUrl());
            ByteArrayResource resource = new ByteArrayResource(data);

            modService.incrementDownloadCount(mod.getId());
            analyticsService.logDownload(mod.getId(), targetVersion.getId(), mod.getAuthor(), isApi, clientIp);

            String originalPath = targetVersion.getFileUrl();
            String filename = originalPath.contains("/") ? originalPath.substring(originalPath.lastIndexOf('/') + 1) : originalPath;
            if (filename.length() > 37 && filename.charAt(36) == '-') {
                filename = filename.substring(37);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<Page<Mod>> getProjects(
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(required = false) String gameVersion,
            @RequestParam(required = false, name = "classification") String classification,
            @RequestParam(required = false) Integer minDownloads,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String creator
    ) {
        try {
            List<String> tagList = null;
            if (tags != null && !tags.trim().isEmpty()) {
                tagList = Arrays.stream(tags.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            String effectiveAuthor = author != null && !author.isEmpty() ? author : creator;
            String authorId = null;
            if (effectiveAuthor != null) {
                Optional<User> u = userRepository.findByUsernameIgnoreCase(effectiveAuthor);
                if (u.isPresent()) authorId = u.get().getId();
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isApiKeyUser = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_API"));

            String effectiveCategory = isApiKeyUser ? null : category;

            Page<Mod> data = modService.getMods(
                    tagList,
                    search,
                    page,
                    size,
                    sort,
                    gameVersion,
                    classification,
                    null,
                    minDownloads,
                    effectiveCategory,
                    dateRange,
                    authorId
            );

            CacheControl cacheControl;
            if ("Favorites".equals(effectiveCategory) || "Your Projects".equals(effectiveCategory)) {
                cacheControl = CacheControl.noCache();
            } else {
                cacheControl = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
            }
            return ResponseEntity.ok().cacheControl(cacheControl).body(data);
        } catch (Exception e) {
            logger.error("Error in getProjects", e);
            return ResponseEntity.ok(Page.empty());
        }
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<?> getProject(@PathVariable String id, HttpServletRequest request) {
        Mod mod = modService.getModById(id);
        if (mod == null) return ResponseEntity.notFound().build();

        if ("DRAFT".equals(mod.getStatus()) || "PENDING".equals(mod.getStatus())) {
            User user = userService.getCurrentUser();
            if (!isAdminOrSuper(user)) {
                if (user == null || !modService.hasEditPermission(mod, user)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        }

        User user = userService.getCurrentUser();
        if (user != null) {
            mod.setCanEdit(modService.hasEditPermission(mod, user));
            mod.setIsOwner(modService.isOwner(mod, user));
        }

        return ResponseEntity.ok(mod);
    }

    @GetMapping("/projects/{id}/meta")
    public ResponseEntity<Map<String, Object>> getProjectMeta(@PathVariable String id) {
        Mod mod = modService.getModById(id);
        if (mod == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "title", mod.getTitle(),
                "description", mod.getDescription() != null ? mod.getDescription() : "",
                "icon", mod.getImageUrl() != null ? mod.getImageUrl() : "",
                "author", mod.getAuthor(),
                "classification", mod.getClassification(),
                "downloads", mod.getDownloadCount(),
                "repositoryUrl", mod.getRepositoryUrl() != null ? mod.getRepositoryUrl() : "",
                "slug", mod.getSlug() != null ? mod.getSlug() : mod.getId()
        ));
    }

    @GetMapping("/projects/{id}/versions/{version}/dependencies")
    public ResponseEntity<List<ModDependency>> getProjectDependencies(@PathVariable String id, @PathVariable String version) {
        Mod mod = modService.getModById(id);
        if (mod == null) return ResponseEntity.notFound().build();
        ModVersion targetVersion = mod.getVersions().stream()
                .filter(v -> v.getVersionNumber().equalsIgnoreCase(version))
                .findFirst()
                .orElse(null);
        if (targetVersion == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(targetVersion.getDependencies() != null ? targetVersion.getDependencies() : List.of());
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(modService.getAllowedTags());
    }

    @GetMapping("/meta/classifications")
    public ResponseEntity<List<String>> getClassifications() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(modService.getAllowedClassifications());
    }

    @GetMapping("/meta/game-versions")
    public ResponseEntity<List<String>> getHytaleVersions() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic()).body(modService.getAllHytaleVersions());
    }

    @GetMapping("/creators/search")
    public ResponseEntity<List<User>> searchCreators(@RequestParam String query) {
        List<User> creators = modService.searchCreators(query);
        creators.forEach(u -> { u.setEmail(null); u.setGithubAccessToken(null); u.setLikedModIds(null); });
        return ResponseEntity.ok(creators);
    }

    @GetMapping("/creators/{username}/projects")
    public ResponseEntity<Page<Mod>> getCreatorProjects(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User currentUser = userService.getCurrentUser();
        User targetUser = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean hasPrivilege = false;

        if (currentUser != null) {
            if (currentUser.getId().equals(targetUser.getId())) {
                hasPrivilege = true;
            } else if (targetUser.getAccountType() == User.AccountType.ORGANIZATION) {
                hasPrivilege = targetUser.getOrganizationMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(currentUser.getId()));
            }
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        Page<Mod> pageResult;
        if (hasPrivilege) {
            pageResult = modService.getPrivilegedCreatorProjects(targetUser.getId(), pageable);
        } else {
            pageResult = modService.getCreatorProjects(targetUser.getId(), pageable);
        }

        if (currentUser != null) {
            pageResult.getContent().forEach(m -> {
                m.setCanEdit(modService.hasEditPermission(m, currentUser));
                m.setIsOwner(modService.isOwner(m, currentUser));
            });
        }

        return ResponseEntity.ok(pageResult);
    }

    @GetMapping("/projects/user/contributed")
    public ResponseEntity<Page<Mod>> getContributedProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Page<Mod> pageResult = modService.getContributedProjects(user.getId(), PageRequest.of(page, size, Sort.by("updatedAt").descending()));

        pageResult.getContent().forEach(m -> {
            m.setCanEdit(modService.hasEditPermission(m, user));
            m.setIsOwner(modService.isOwner(m, user));
        });

        return ResponseEntity.ok(pageResult);
    }

    @PostMapping("/projects/{id}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        modService.toggleFavorite(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable String id, @RequestBody Map<String, Object> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        String content = (String) body.get("content");
        try {
            modService.addComment(id, user.getUsername(), content);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PutMapping("/projects/{id}/comments/{commentId}")
    public ResponseEntity<?> editComment(@PathVariable String id, @PathVariable String commentId, @RequestBody Map<String, Object> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        String content = (String) body.get("content");
        try {
            modService.editComment(id, commentId, user.getUsername(), content);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @DeleteMapping("/projects/{id}/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable String id, @PathVariable String commentId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            modService.deleteComment(id, commentId, user.getUsername());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/comments/{commentId}/reply")
    public ResponseEntity<?> replyToComment(@PathVariable String id, @PathVariable String commentId, @RequestBody Map<String, Object> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        String reply = (String) body.get("reply");
        try {
            modService.replyToComment(id, commentId, reply, user.getUsername());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping("/projects")
    public ResponseEntity<?> createProject(
            @RequestParam("title") String title,
            @RequestParam("classification") String classification,
            @RequestParam("description") String description,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "slug", required = false) String slug
    ) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            Mod newMod = modService.createDraft(title, description, classification, user, owner, slug);
            return ResponseEntity.ok(newMod);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/projects/{id}")
    public ResponseEntity<?> updateProject(@PathVariable String id, @RequestBody Mod updatedMod) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            if (updatedMod.getDescription() != null && updatedMod.getDescription().length() > 250) {
                return ResponseEntity.badRequest().body("Short Summary cannot exceed 250 characters.");
            }
            if (updatedMod.getAbout() != null && updatedMod.getAbout().length() > 50000) {
                return ResponseEntity.badRequest().body("Full Description cannot exceed 50,000 characters.");
            }

            modService.updateMod(id, updatedMod);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/projects/{id}/versions/{versionId}")
    public ResponseEntity<?> updateVersion(@PathVariable String id, @PathVariable String versionId, @RequestBody Map<String, Object> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            @SuppressWarnings("unchecked")
            List<String> modIds = (List<String>) body.get("modIds");
            modService.updateVersionDependencies(id, versionId, modIds);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) { return ResponseEntity.status(403).body(e.getMessage()); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        catch (Exception e) { return ResponseEntity.status(500).body(e.getMessage()); }
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            modService.deleteMod(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/submit")
    public ResponseEntity<?> submitProject(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            modService.submitMod(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/revert")
    public ResponseEntity<?> revertProjectToDraft(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            modService.revertModToDraft(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/archive")
    public ResponseEntity<?> archiveProject(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            modService.archiveMod(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/unlist")
    public ResponseEntity<?> unlistProject(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            modService.unlistMod(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/publish")
    public ResponseEntity<?> publishProject(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            modService.publishMod(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/versions")
    public ResponseEntity<?> addVersion(
            @PathVariable String id,
            @RequestParam("versionNumber") String versionNumber,
            @RequestParam(value = "gameVersions", required = false) List<String> gameVersions,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "modIds", required = false) List<String> modIds,
            @RequestParam(value = "changelog", required = false) String changelog,
            @RequestParam(value = "channel", required = false, defaultValue = "RELEASE") String channel
    ) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            if (modIds != null && modIds.size() == 1 && modIds.get(0).contains(",")) {
                modIds = Arrays.stream(modIds.get(0).split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            ModVersion.Channel channelEnum = ModVersion.Channel.valueOf(channel.toUpperCase());
            modService.addVersionToMod(id, versionNumber, gameVersions, file, changelog, modIds, channelEnum);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/projects/{id}/versions/{versionId}")
    public ResponseEntity<?> deleteVersion(@PathVariable String id, @PathVariable String versionId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            modService.deleteVersion(id, versionId, user.getId());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/gallery")
    public ResponseEntity<?> addGalleryImage(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            String path = storageService.upload(file, "gallery");
            String url = storageService.getPublicUrl(path);
            modService.addGalleryImage(id, url);
            return ResponseEntity.ok(url);
        } catch (Exception e) { return ResponseEntity.status(500).build(); }
    }

    @DeleteMapping("/projects/{id}/gallery")
    public ResponseEntity<?> removeGalleryImage(@PathVariable String id, @RequestParam("imageUrl") String imageUrl) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        modService.removeGalleryImage(id, imageUrl, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/invite")
    public ResponseEntity<?> inviteContributor(@PathVariable String id, @RequestParam String username) {
        User targetUser = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            modService.inviteContributor(id, targetUser.getId());
            return ResponseEntity.ok().build();
        }
        catch (SecurityException e) { return ResponseEntity.status(403).body(e.getMessage()); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/invite/accept")
    public ResponseEntity<?> acceptInvite(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            modService.acceptInvite(id, user.getId());
            return ResponseEntity.ok().build();
        }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/invite/decline")
    public ResponseEntity<?> declineInvite(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        modService.declineInvite(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{id}/contributors/{username}")
    public ResponseEntity<?> removeContributor(@PathVariable String id, @PathVariable String username) {
        User targetUser = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            modService.removeContributor(id, targetUser.getId());
            return ResponseEntity.ok().build();
        }
        catch (Exception e) { return ResponseEntity.status(403).body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/transfer")
    public ResponseEntity<?> requestTransfer(@PathVariable String id, @RequestBody Map<String, String> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        String targetUsername = body.get("username");

        User targetUser = userRepository.findByUsernameIgnoreCase(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        try {
            modService.requestTransfer(id, targetUser.getId(), user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) { return ResponseEntity.status(403).body(e.getMessage()); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/projects/{id}/transfer/resolve")
    public ResponseEntity<?> resolveTransfer(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        Boolean accept = body.get("accept");
        if (accept == null) return ResponseEntity.badRequest().build();

        try {
            modService.resolveTransfer(id, accept, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) { return ResponseEntity.status(403).body(e.getMessage()); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}