package net.modtale.controller.admin;

import net.modtale.mapper.ProjectMapper;
import net.modtale.model.admin.AdminLog;
import net.modtale.model.dto.admin.AdminAuthorStatsDTO;
import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.dto.request.admin.RejectReasonRequest;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.admin.AdminLogRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.admin.ProjectManagementService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.LifecycleService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.SearchService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.ScanService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class ProjectManagementController {

    @Autowired private AccountService accountService;
    @Autowired private ProjectService projectService;
    @Autowired private SearchService searchService;
    @Autowired private ProjectManagementService projectManagementService;
    @Autowired private LifecycleService lifecycleService;
    @Autowired private ScanService scanService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AdminLogRepository adminLogRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private AccessControlService accessControlService;

    private User getSafeUser() {
        try {
            return accountService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    private void logAction(String adminId, String action, String targetId, String targetType, String details) {
        adminLogRepository.save(new AdminLog(adminId, action, targetId, targetType, details));
    }

    @GetMapping("/verification/queue")
    public ResponseEntity<List<ProjectSummaryDTO>> getVerificationQueue() {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(searchService.getVerificationQueue().stream()
                .map(p -> ProjectMapper.toSummaryDTO(p, true))
                .toList());
    }

    @GetMapping("/projects/{id}/review-details")
    public ResponseEntity<?> getProjectReviewDetails(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Project project = projectService.getRawProjectById(id);
        if (project == null) return ResponseEntity.notFound().build();

        User author = userRepository.findByUsernameIgnoreCase(project.getAuthor()).orElse(null);
        if (author == null) {
            author = userRepository.findById(project.getAuthor()).orElse(null);
        }

        AdminAuthorStatsDTO authorStats = new AdminAuthorStatsDTO(
                author != null ? author.getCreatedAt() : "Unknown",
                author != null ? author.getTier().name() : "Unknown",
                author != null ? (author.getAvatarUrl() != null ? author.getAvatarUrl() : "") : "",
                author != null ? searchService.getCreatorProjects(author.getId(), PageRequest.of(0, 10000)).getTotalElements() : 0
        );

        return ResponseEntity.ok(new AdminProjectReviewDTO(ProjectMapper.toAdminDTO(project), authorStats));
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<?> getProjectById(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Project project = projectService.getAdminProjectDetails(id);
        if (project == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ProjectMapper.toAdminDTO(project));
    }

    @PutMapping("/projects/{id}/raw")
    public ResponseEntity<?> updateRawProject(@PathVariable String id, @RequestBody Project updatedProject) {
        User currentUser = getSafeUser();
        if (!accessControlService.isSuperAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Project existing = projectService.getRawProjectById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        updatedProject.setId(existing.getId());
        projectRepository.save(updatedProject);

        logAction(currentUser.getId(), "RAW_UPDATE_PROJECT", existing.getId(), "PROJECT", "Updated via Raw JSON");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/publish")
    public ResponseEntity<?> publishProject(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            lifecycleService.publishProject(id, currentUser);
            logAction(currentUser.getId(), "PUBLISH_PROJECT", id, "PROJECT", null);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/versions/{versionId}/approve")
    public ResponseEntity<?> approveVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            projectManagementService.approveVersion(id, versionId);
            logAction(currentUser.getId(), "APPROVE_VERSION", id, "VERSION", "VerID: " + versionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/versions/{versionId}/reject")
    public ResponseEntity<?> rejectVersion(@PathVariable String id, @PathVariable String versionId, @RequestBody RejectReasonRequest requestPayload) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            projectManagementService.rejectVersion(id, versionId, requestPayload.getReason());
            logAction(currentUser.getId(), "REJECT_VERSION", id, "VERSION", "VerID: " + versionId + ", Reason: " + requestPayload.getReason());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/reject")
    public ResponseEntity<?> rejectProject(@PathVariable String id, @RequestBody RejectReasonRequest requestPayload) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            projectManagementService.rejectProject(id, requestPayload.getReason());
            logAction(currentUser.getId(), "REJECT_PROJECT", id, "PROJECT", "Reason: " + requestPayload.getReason());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<?> deleteProject(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "Administrative action.") String reason
    ) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            Project targetProject = projectService.getRawProjectById(id);
            if (targetProject == null) return ResponseEntity.notFound().build();

            projectManagementService.adminDeleteProject(id);

            notificationService.sendNotification(
                    List.of(targetProject.getAuthorId()),
                    "Project Deleted",
                    "Your project '" + targetProject.getTitle() + "' was deleted by an administrator. Reason: " + reason,
                    URI.create("/"),
                    null
            );

            logAction(currentUser.getId(), "DELETE_PROJECT", id, "PROJECT", "Reason: " + reason);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/projects/{id}/hard")
    public ResponseEntity<?> hardDeleteProject(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "Administrative action.") String reason
    ) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            Project targetProject = projectService.getRawProjectById(id);
            if (targetProject == null) return ResponseEntity.notFound().build();

            projectManagementService.adminHardDeleteProject(id);

            notificationService.sendNotification(
                    List.of(targetProject.getAuthorId()),
                    "Project Permanently Deleted",
                    "Your project '" + targetProject.getTitle() + "' was permanently removed by an administrator. Reason: " + reason,
                    URI.create("/"),
                    null
            );

            logAction(currentUser.getId(), "HARD_DELETE_PROJECT", id, "PROJECT", "Reason: " + reason);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/restore")
    public ResponseEntity<?> restoreProject(@PathVariable String id, @RequestParam(defaultValue = "PUBLISHED") String status) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            projectManagementService.adminRestoreProject(id, status);
            logAction(currentUser.getId(), "RESTORE_PROJECT", id, "PROJECT", "To Status: " + status);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/unlist")
    public ResponseEntity<?> unlistProject(@PathVariable String id, @RequestBody(required = false) RejectReasonRequest requestPayload) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            String reason = (requestPayload != null && requestPayload.getReason() != null && !requestPayload.getReason().isBlank())
                    ? requestPayload.getReason()
                    : "Administrative action.";
            Project targetProject = projectService.getRawProjectById(id);
            if (targetProject == null) return ResponseEntity.notFound().build();

            projectManagementService.adminUnlistProject(id);

            notificationService.sendNotification(
                    List.of(targetProject.getAuthorId()),
                    "Project Unlisted",
                    "Your project '" + targetProject.getTitle() + "' was unlisted from the public directory by an administrator. Reason: " + reason,
                    URI.create("/mod/" + targetProject.getSlug()),
                    null
            );

            logAction(currentUser.getId(), "UNLIST_PROJECT", id, "PROJECT", "Reason: " + reason);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/projects/{id}/versions/{versionId}")
    public ResponseEntity<?> deleteProjectVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            projectManagementService.adminDeleteVersion(id, versionId);
            logAction(currentUser.getId(), "DELETE_VERSION", id, "VERSION", "VerID: " + versionId);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/projects/search")
    public ResponseEntity<?> searchProjects(@RequestParam String query, @RequestParam(required = false, defaultValue = "false") boolean deleted) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        if (deleted) {
            return ResponseEntity.ok(searchService.searchDeletedProjects(query, PageRequest.of(0, 10)).getContent().stream()
                    .map(p -> ProjectMapper.toSummaryDTO(p, true))
                    .toList());
        } else {
            return ResponseEntity.ok(searchService.searchProjects(null, query, 0, 10, "relevance", null, null, null, null, null, null, null).getContent().stream()
                    .map(p -> ProjectMapper.toSummaryDTO(p, true))
                    .toList());
        }
    }

    @PostMapping("/projects/{id}/versions/{versionId}/scan")
    public ResponseEntity<?> rescanVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            scanService.triggerRescan(id, versionId);
            logAction(currentUser.getId(), "RESCAN_VERSION", id, "VERSION", "VerID: " + versionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
