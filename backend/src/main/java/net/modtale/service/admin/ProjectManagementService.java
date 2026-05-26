package net.modtale.service.admin;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.storage.StorageService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.LifecycleService;
import net.modtale.service.security.SecurityIssueAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;

@Service
public class ProjectManagementService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;
    @Autowired private LifecycleService lifecycleService;
    @Autowired private NotificationService notificationService;
    @Autowired private StorageService storageService;
    @Autowired private SecurityIssueAnalysisService securityIssueAnalysisService;

    public void approveVersion(String id, String versionId) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) throw new IllegalArgumentException("Project not found");
        ProjectVersion ver = project.getVersions().stream().filter(v -> v.getId().equals(versionId)).findFirst().orElseThrow();
        securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(ver);
        ver.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        ver.setRejectionReason(null);
        ver.setScheduledPublishDate(null);
        project.setUpdatedAt(LocalDateTime.now().toString());
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        notificationService.notifyUpdates(project, ver.getVersionNumber());
        notificationService.notifyDependents(project, ver.getVersionNumber());
    }

    public void rejectVersion(String id, String versionId, String reason) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) throw new IllegalArgumentException("Project not found");
        ProjectVersion ver = project.getVersions().stream().filter(v -> v.getId().equals(versionId)).findFirst().orElseThrow();
        ver.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);
        ver.setRejectionReason(reason);
        ver.setScheduledPublishDate(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        if (project.getAuthorId() != null) {
            User author = userRepository.findById(project.getAuthorId()).orElse(null);
            if (author != null) {
                notificationService.sendNotification(java.util.List.of(author.getId()), "Version Rejected", "Version " + ver.getVersionNumber() + " of " + project.getTitle() + " was rejected. Reason: " + reason, URI.create("/dashboard/projects"), project.getImageUrl());
            }
        }
    }

    public void rejectProject(String id, String reason) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) throw new IllegalArgumentException("Project not found.");
        project.setStatus(ProjectStatus.DRAFT);
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        if (project.getAuthorId() != null) {
            User author = userRepository.findById(project.getAuthorId()).orElse(null);
            if (author != null) {
                notificationService.sendNotification(java.util.List.of(author.getId()), "Project Returned", "Submission '" + project.getTitle() + "' returned to drafts. Reason: " + (reason != null ? reason : "Quality Standards"), URI.create("/dashboard/projects"), project.getImageUrl());
            }
        }
    }

    public void adminDeleteProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) throw new IllegalArgumentException("Project not found");
        lifecycleService.performDeletionStrategy(project);
    }

    public void adminHardDeleteProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || project.getStatus() != ProjectStatus.DELETED) throw new IllegalArgumentException("Project must be in DELETED state.");
        lifecycleService.performHardDelete(project);
    }

    public void adminRestoreProject(String id, String targetStatus) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || project.getStatus() != ProjectStatus.DELETED) throw new IllegalArgumentException("Project not in a recoverable state.");

        ProjectStatus statusEnum = ProjectStatus.valueOf(targetStatus.toUpperCase());
        if (!java.util.List.of(ProjectStatus.PUBLISHED, ProjectStatus.DRAFT, ProjectStatus.UNLISTED, ProjectStatus.ARCHIVED).contains(statusEnum)) {
            throw new IllegalArgumentException("Invalid status.");
        }
        project.setStatus(statusEnum);
        project.setDeletedAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void adminUnlistProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) throw new IllegalArgumentException("Project not found");
        project.setStatus(ProjectStatus.UNLISTED);
        project.setExpiresAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void adminDeleteVersion(String id, String versionId) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) throw new IllegalArgumentException("Project not found");
        if (project.getVersions().removeIf(v -> {
            if (v.getId().equals(versionId)) {
                if (v.getFileUrl() != null) storageService.deleteFile(v.getFileUrl());
                return true;
            }
            return false;
        })) {
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } else throw new IllegalArgumentException("Version not found.");
    }
}
