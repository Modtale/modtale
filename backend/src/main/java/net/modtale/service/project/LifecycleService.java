package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.storage.StorageService;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.SecurityIssueAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class LifecycleService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;
    @Autowired private ValidationService validationService;
    @Autowired private NotificationService notificationService;
    @Autowired private WebhookService webhookService;
    @Autowired private TrackingService trackingService;
    @Autowired private SanitizationService sanitizer;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private StorageService storageService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessControlService accessControlService;
    @Autowired private SecurityIssueAnalysisService securityIssueAnalysisService;

    @Value("${app.limits.max-projects-per-user:50}")
    private int maxProjectsPerUser;

    public void ensureEditable(Project project) {
        if (project.getStatus() == ProjectStatus.PENDING) throw new IllegalStateException("Pending projects cannot be modified. Revert to draft first.");
        if (project.getStatus() == ProjectStatus.ARCHIVED) throw new IllegalStateException("Archived projects are read-only.");
    }

    public Project createDraft(String title, String description, ProjectClassification classification, User user, String ownerId, String customSlug) {
        if (!user.isEmailVerified()) throw new SecurityException("Email verification required.");
        if(projectRepository.existsByTitleIgnoreCase(title)) throw new IllegalArgumentException("Title taken.");

        String finalAuthorId = user.getId();
        String finalAuthorName = user.getUsername();

        if (ownerId != null && !ownerId.isEmpty() && !ownerId.equals(user.getId())) {
            User org = userRepository.findById(ownerId).orElse(null);
            if (org == null || org.getAccountType() != User.AccountType.ORGANIZATION || org.getOrganizationMembers().stream().noneMatch(m -> m.getUserId().equals(user.getId()))) {
                throw new SecurityException("No permission.");
            }
            finalAuthorId = org.getId(); finalAuthorName = org.getUsername();
        }

        if (projectRepository.countByAuthorId(finalAuthorId) >= maxProjectsPerUser) throw new IllegalStateException("Limit reached.");
        if (customSlug != null && !customSlug.isEmpty()) {
            validationService.validateSlug(customSlug);
            if (projectRepository.existsBySlug(customSlug)) throw new IllegalArgumentException("Slug taken.");
        }

        Project project = new Project();
        project.setId(UUID.randomUUID().toString());
        project.setTitle(sanitizer.sanitizePlainText(title));
        project.setDescription(sanitizer.sanitizePlainText(description));
        project.setClassification(classification);
        project.setAuthorId(finalAuthorId);
        project.setAuthor(finalAuthorName);
        if (customSlug != null && !customSlug.isEmpty()) project.setSlug(customSlug.toLowerCase());
        project.setStatus(ProjectStatus.DRAFT);
        project.setVersions(new ArrayList<>());
        project.setAllowModpacks(true);
        project.setAllowComments(true);
        project.setTags(new ArrayList<>());

        Project.ProjectRole adminRole = new Project.ProjectRole(UUID.randomUUID().toString(), "Admin", "#fbbf24", List.of("PROJECT_EDIT_METADATA", "VERSION_CREATE", "VERSION_EDIT", "VERSION_DELETE", "PROJECT_TEAM_INVITE", "PROJECT_TEAM_REMOVE", "PROJECT_MEMBER_EDIT_ROLE"));
        Project.ProjectRole devRole = new Project.ProjectRole(UUID.randomUUID().toString(), "Developer", "#3b82f6", List.of("VERSION_CREATE"));
        project.setProjectRoles(new ArrayList<>(List.of(adminRole, devRole)));
        project.setTeamMembers(new ArrayList<>());
        project.setTeamInvites(new ArrayList<>());

        return projectRepository.save(project);
    }

    public void submitProject(String id, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_SUBMIT")) throw new SecurityException("Denied.");
        if (!user.isEmailVerified()) throw new SecurityException("Email verification required.");
        this.ensureEditable(project);

        if(project.getVersions().isEmpty() && project.getClassification() != ProjectClassification.MODPACK) throw new IllegalArgumentException("Need 1 version.");
        if(project.getDescription() == null || project.getDescription().length() < 10) throw new IllegalArgumentException("Desc >= 10 chars.");
        if(project.getTags() == null || project.getTags().isEmpty()) throw new IllegalArgumentException("Need 1 tag.");
        if(project.getSlug() != null) validationService.validateSlug(project.getSlug());
        validationService.validateRepositoryUrl(project.getRepositoryUrl());
        if (project.getClassification() != ProjectClassification.MODPACK && (project.getLicense() == null || project.getLicense().isEmpty())) throw new IllegalArgumentException("License required.");

        project.setStatus(ProjectStatus.PENDING);
        project.setExpiresAt(null);
        if (project.getVersions() != null) project.getVersions().forEach(v -> { if (v.getReviewStatus() == null || v.getReviewStatus() == ProjectVersion.ReviewStatus.REJECTED) v.setReviewStatus(ProjectVersion.ReviewStatus.PENDING); });

        projectRepository.save(project);
        projectService.evictProjectCache(project);
        if (project.getVersions() == null || project.getVersions().stream().noneMatch(v -> v.getScanResult() != null && v.getScanResult().getStatus() == ScanStatus.SCANNING)) {
            webhookService.triggerAdminNewProjectWebhook(project);
        }
    }

    public void revertProjectToDraft(String id, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_REVERT")) throw new SecurityException("Denied.");
        if (project.getStatus() != ProjectStatus.PENDING) throw new IllegalArgumentException("Only pending projects revertible.");
        project.setStatus(ProjectStatus.DRAFT);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void archiveProject(String id, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_ARCHIVE")) throw new SecurityException("Denied.");
        if (project.getStatus() != ProjectStatus.PUBLISHED && project.getStatus() != ProjectStatus.UNLISTED) throw new IllegalArgumentException("Must be published/unlisted.");
        project.setStatus(ProjectStatus.ARCHIVED);
        project.setExpiresAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void unlistProject(String id, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_UNLIST")) throw new SecurityException("Denied.");
        if (project.getStatus() != ProjectStatus.PUBLISHED && project.getStatus() != ProjectStatus.ARCHIVED) throw new IllegalArgumentException("Must be published/archived.");
        project.setStatus(ProjectStatus.UNLISTED);
        project.setExpiresAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void publishProject(String id, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) throw new IllegalArgumentException("Not found.");
        boolean isAdmin = accessControlService.isAdmin(user);
        boolean isRestoration = project.getStatus() == ProjectStatus.ARCHIVED || project.getStatus() == ProjectStatus.UNLISTED;

        if (isRestoration) { if (!accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_PUBLISH")) throw new SecurityException("Denied."); }
        else if (!isAdmin) throw new SecurityException("Admins only.");

        boolean isNew = project.getStatus() == ProjectStatus.PENDING || project.getCreatedAt() == null;
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setExpiresAt(null);
        project.setUpdatedAt(LocalDateTime.now().toString());

        if (project.getVersions() != null) project.getVersions().forEach(v -> {
            if (v.getReviewStatus() == ProjectVersion.ReviewStatus.PENDING || v.getReviewStatus() == ProjectVersion.ReviewStatus.SCHEDULED) {
                v.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
                v.setScheduledPublishDate(null);
            }
            if (v.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED) {
                securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(v);
            }
        });

        if (isNew) project.setCreatedAt(LocalDateTime.now().toString());
        if (!isRestoration && isAdmin && user != null) project.setApprovedBy(user.getUsername());
        if (project.getImageUrl() == null || project.getImageUrl().isEmpty()) project.setImageUrl("https://modtale.net/assets/favicon.svg");

        Project saved = projectRepository.save(project);
        projectService.evictProjectCache(saved);

        if (isNew) {
            notificationService.notifyNewProject(saved);
            webhookService.triggerWebhook(saved);
            webhookService.triggerDiscordWebhook(saved);
            trackingService.logNewProject(saved.getId());
        }
    }

    public void updateProjectStatus(String id, ProjectStatus status, User user, String permissionRequired) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, permissionRequired)) throw new SecurityException("Denied.");
        project.setStatus(status);
        project.setExpiresAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void softDeleteProject(String id, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "PROJECT_DELETE")) throw new SecurityException("Denied.");
        this.ensureEditable(project);
        performDeletionStrategy(project);
    }

    public void performDeletionStrategy(Project project) {
        ProjectStatus oldStatus = project.getStatus();
        project.setStatus(ProjectStatus.DELETED);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        if (oldStatus == ProjectStatus.PUBLISHED || oldStatus == ProjectStatus.UNLISTED || oldStatus == ProjectStatus.ARCHIVED) trackingService.logDeletedProject(project.getId());
    }

    public void performHardDelete(Project project) {
        if (!projectRepository.findByDependency(project.getId()).isEmpty()) {
            project.setTitle("Deleted Project");
            project.setDescription("This project has been deleted.");
            project.setAbout("This project was deleted by the author but is retained for dependency resolution.");
            if (project.getImageUrl() != null) storageService.deleteFile(project.getImageUrl());
            if (project.getBannerUrl() != null) storageService.deleteFile(project.getBannerUrl());
            project.setImageUrl(null); project.setBannerUrl(null); project.setSlug(null);
            if (project.getGalleryImages() != null) { project.getGalleryImages().forEach(storageService::deleteFile); project.getGalleryImages().clear(); }
            project.setTeamMembers(new ArrayList<>()); project.setTeamInvites(new ArrayList<>());
            project.setProjectRoles(new ArrayList<>()); project.setComments(new ArrayList<>()); project.setTags(new ArrayList<>());
            project.setDeletedAt(null);
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } else {
            trackingService.deleteProjectAnalytics(project.getId());
            Set<String> depIds = new HashSet<>();
            if (project.getVersions() != null) project.getVersions().forEach(v -> {
                if (v.getFileUrl() != null) storageService.deleteFile(v.getFileUrl());
                if (v.getDependencies() != null) v.getDependencies().forEach(d -> depIds.add(d.getModId()));
            });
            if (project.getModIds() != null) depIds.addAll(project.getModIds());
            if (project.getImageUrl() != null) storageService.deleteFile(project.getImageUrl());
            if (project.getBannerUrl() != null) storageService.deleteFile(project.getBannerUrl());
            if (project.getGalleryImages() != null) project.getGalleryImages().forEach(storageService::deleteFile);

            mongoTemplate.updateMulti(new Query(Criteria.where("likedProjectIds").is(project.getId())), new Update().pull("likedProjectIds", project.getId()), User.class);
            projectRepository.delete(project);
            projectService.evictProjectCache(project);
            depIds.forEach(this::cleanupOrphanedDependency);
        }
    }

    private void cleanupOrphanedDependency(String id) {
        Project proj = projectService.getRawProjectById(id);
        if (proj != null && proj.getStatus() == ProjectStatus.DELETED && projectRepository.findByDependency(id).isEmpty()) performHardDelete(proj);
    }
}
