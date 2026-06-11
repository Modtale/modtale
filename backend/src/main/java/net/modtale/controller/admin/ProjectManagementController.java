package net.modtale.controller.admin;

import jakarta.validation.Valid;
import net.modtale.model.dto.admin.AdminProjectDTO;
import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.request.admin.RejectReasonRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.service.admin.ProjectAdminOperationsService;
import net.modtale.service.admin.ProjectReviewAdminService;
import net.modtale.service.user.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class ProjectManagementController {

    private final AccountService accountService;
    private final ProjectReviewAdminService projectReviewAdminService;
    private final ProjectAdminOperationsService projectAdminOperationsService;

    public ProjectManagementController(
            AccountService accountService,
            ProjectReviewAdminService projectReviewAdminService,
            ProjectAdminOperationsService projectAdminOperationsService
    ) {
        this.accountService = accountService;
        this.projectReviewAdminService = projectReviewAdminService;
        this.projectAdminOperationsService = projectAdminOperationsService;
    }

    @GetMapping("/verification/queue")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<List<ProjectSummaryDTO>> getVerificationQueue() {
        return ResponseEntity.ok(projectReviewAdminService.getVerificationQueue());
    }

    @GetMapping("/projects/{id}/review-details")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<AdminProjectReviewDTO> getProjectReviewDetails(@PathVariable String id) {
        return ResponseEntity.ok(projectReviewAdminService.getProjectReviewDetails(id));
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<AdminProjectDTO> getProjectById(@PathVariable String id) {
        return ResponseEntity.ok(projectAdminOperationsService.getProjectById(id));
    }

    @PutMapping("/projects/{id}/raw")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<Void> updateRawProject(@PathVariable String id, @RequestBody Project updatedProject) {
        User currentUser = accountService.requireCurrentUser("editing raw project data");
        projectAdminOperationsService.updateRawProject(currentUser.getId(), id, updatedProject);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/publish")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> publishProject(@PathVariable String id) {
        User currentUser = accountService.requireCurrentUser("publishing projects");
        projectReviewAdminService.publishProject(currentUser, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/versions/{versionId}/approve")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> approveVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = accountService.requireCurrentUser("approving project versions");
        projectReviewAdminService.approveVersion(currentUser, id, versionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/versions/{versionId}/reject")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> rejectVersion(@PathVariable String id, @PathVariable String versionId, @Valid @RequestBody RejectReasonRequest requestPayload) {
        User currentUser = accountService.requireCurrentUser("rejecting project versions");
        projectReviewAdminService.rejectVersion(currentUser, id, versionId, requestPayload.getReason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/reject")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> rejectProject(@PathVariable String id, @Valid @RequestBody RejectReasonRequest requestPayload) {
        User currentUser = accountService.requireCurrentUser("rejecting projects");
        projectReviewAdminService.rejectProject(currentUser, id, requestPayload.getReason());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> deleteProject(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "Administrative action.") String reason
    ) {
        User currentUser = accountService.requireCurrentUser("deleting projects");
        projectAdminOperationsService.deleteProject(currentUser, id, reason);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{id}/hard")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> hardDeleteProject(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "Administrative action.") String reason
    ) {
        User currentUser = accountService.requireCurrentUser("permanently deleting projects");
        projectAdminOperationsService.hardDeleteProject(currentUser, id, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/restore")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> restoreProject(@PathVariable String id, @RequestParam(defaultValue = "PUBLISHED") ProjectStatus status) {
        User currentUser = accountService.requireCurrentUser("restoring projects");
        projectAdminOperationsService.restoreProject(currentUser, id, status);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{id}/unlist")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> unlistProject(@PathVariable String id, @RequestBody(required = false) RejectReasonRequest requestPayload) {
        User currentUser = accountService.requireCurrentUser("unlisting projects");
        String reason = requestPayload != null && requestPayload.getReason() != null && !requestPayload.getReason().isBlank()
                ? requestPayload.getReason()
                : "Administrative action.";
        projectAdminOperationsService.unlistProject(currentUser, id, reason);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{id}/versions/{versionId}")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> deleteProjectVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = accountService.requireCurrentUser("deleting project versions");
        projectAdminOperationsService.deleteProjectVersion(currentUser, id, versionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/projects/search")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<List<ProjectSummaryDTO>> searchProjects(@RequestParam String query, @RequestParam(required = false, defaultValue = "false") boolean deleted) {
        return ResponseEntity.ok(projectAdminOperationsService.searchProjects(query, deleted));
    }

    @PostMapping("/projects/{id}/versions/{versionId}/scan")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> rescanVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = accountService.requireCurrentUser("rescanning project versions");
        projectAdminOperationsService.rescanVersion(currentUser, id, versionId);
        return ResponseEntity.ok().build();
    }
}
