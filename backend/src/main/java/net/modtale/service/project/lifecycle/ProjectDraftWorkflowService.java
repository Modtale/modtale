package net.modtale.service.project.lifecycle;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.project.version.VersionMutationOrchestrationService;
import net.modtale.service.security.validation.SanitizationService;
import org.springframework.stereotype.Service;

@Service
public class ProjectDraftWorkflowService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ValidationService validationService;
    private final WebhookService webhookService;
    private final SanitizationService sanitizer;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final VersionMutationOrchestrationService versionMutationOrchestrationService;
    private final int maxProjectsPerUser;

    public ProjectDraftWorkflowService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ValidationService validationService,
            WebhookService webhookService,
            SanitizationService sanitizer,
            UserRepository userRepository,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            VersionMutationOrchestrationService versionMutationOrchestrationService,
            AppLimitProperties limitProperties
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.validationService = validationService;
        this.webhookService = webhookService;
        this.sanitizer = sanitizer;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.versionMutationOrchestrationService = versionMutationOrchestrationService;
        this.maxProjectsPerUser = limitProperties.maxProjectsPerUser();
    }

    public Project createDraft(String title, String description, ProjectClassification classification,
                               User user, String ownerId, String customSlug) {
        requireVerifiedEmail(user, "create projects");
        if (projectRepository.existsByTitleIgnoreCase(title)) {
            throw new InvalidProjectRequestException("A project with this title already exists.");
        }

        String finalAuthorId = user.getId();
        String finalAuthorName = user.getUsername();

        if (ownerId != null && !ownerId.isEmpty() && !ownerId.equals(user.getId())) {
            User org = userRepository.findById(ownerId)
                    .filter(candidate -> candidate.getAccountType() == User.AccountType.ORGANIZATION)
                    .orElseThrow(() -> new ProjectOperationForbiddenException(
                            "You do not have permission to create a project for that owner."));
            boolean organizationMember = org.getOrganizationMembers() != null
                    && org.getOrganizationMembers().stream().anyMatch(member -> member.getUserId().equals(user.getId()));
            if (!organizationMember) {
                throw new ProjectOperationForbiddenException(
                        "You do not have permission to create a project for that owner.");
            }
            finalAuthorId = org.getId();
            finalAuthorName = org.getUsername();
        }

        if (projectRepository.countByAuthorId(finalAuthorId) >= maxProjectsPerUser) {
            throw new InvalidProjectRequestException(
                    "This owner has already reached the project limit of " + maxProjectsPerUser + ".");
        }
        if (customSlug != null && !customSlug.isEmpty()) {
            validationService.validateSlug(customSlug);
            if (projectRepository.existsBySlug(customSlug)) {
                throw new InvalidProjectRequestException("That project slug is already taken.");
            }
        }

        Project project = new Project();
        project.setId(UUID.randomUUID().toString());
        project.setTitle(sanitizer.sanitizePlainText(title));
        project.setDescription(sanitizer.sanitizePlainText(description));
        project.setClassification(classification);
        project.setAuthorId(finalAuthorId);
        project.setAuthor(finalAuthorName);
        if (customSlug != null && !customSlug.isEmpty()) {
            project.setSlug(customSlug.toLowerCase());
        }
        project.setStatus(ProjectStatus.DRAFT);
        project.setVersions(new ArrayList<>());
        project.setAllowModpacks(true);
        project.setAllowComments(true);
        project.setTags(new ArrayList<>());

        Project.ProjectRole adminRole = new Project.ProjectRole(
                UUID.randomUUID().toString(),
                "Admin",
                "#fbbf24",
                EnumSet.of(
                        ApiKey.ApiPermission.PROJECT_EDIT_METADATA,
                        ApiKey.ApiPermission.VERSION_CREATE,
                        ApiKey.ApiPermission.VERSION_EDIT,
                        ApiKey.ApiPermission.VERSION_DELETE,
                        ApiKey.ApiPermission.PROJECT_TEAM_INVITE,
                        ApiKey.ApiPermission.PROJECT_TEAM_REMOVE,
                        ApiKey.ApiPermission.PROJECT_MEMBER_EDIT_ROLE
                )
        );
        Project.ProjectRole devRole = new Project.ProjectRole(
                UUID.randomUUID().toString(),
                "Developer",
                "#3b82f6",
                EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE)
        );
        project.setProjectRoles(new ArrayList<>(List.of(adminRole, devRole)));
        project.setTeamMembers(new ArrayList<>());
        project.setTeamInvites(new ArrayList<>());

        return projectRepository.save(project);
    }

    public void submitProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_SUBMIT",
                "You do not have permission to submit this project.");
        requireVerifiedEmail(user, "submit a project for review");
        projectMutationGuard.ensureEditable(project);

        if (project.getVersions().isEmpty() && project.getClassification() != ProjectClassification.MODPACK) {
            throw new InvalidProjectRequestException("Add at least one version before submitting this project.");
        }
        if (project.getDescription() == null || project.getDescription().length() < 10) {
            throw new InvalidProjectRequestException(
                    "Project descriptions must be at least 10 characters long before submission.");
        }
        if (project.getTags() == null || project.getTags().isEmpty()) {
            throw new InvalidProjectRequestException("Add at least one tag before submitting this project.");
        }
        if (project.getSlug() != null) {
            validationService.validateSlug(project.getSlug());
        }
        validationService.validateRepositoryUrl(project.getRepositoryUrl());
        if (project.getClassification() != ProjectClassification.MODPACK
                && (project.getLicense() == null || project.getLicense().isEmpty())) {
            throw new InvalidProjectRequestException("Select a license before submitting this project.");
        }

        project.setStatus(ProjectStatus.PENDING);
        project.setExpiresAt(null);
        List<ProjectVersion> scansQueuedForSubmission = new ArrayList<>();
        if (project.getVersions() != null) {
            project.getVersions().forEach(version -> {
                if (version.getReviewStatus() == null
                        || version.getReviewStatus() == ProjectVersion.ReviewStatus.REJECTED) {
                    version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
                }
                if (versionMutationOrchestrationService.queueSubmissionScanIfNeeded(project, version)) {
                    scansQueuedForSubmission.add(version);
                }
            });
        }

        projectRepository.save(project);
        projectService.evictProjectCache(project);
        scansQueuedForSubmission.forEach(version -> versionMutationOrchestrationService.enqueueSubmissionScan(project, version));
        if (project.getVersions() == null
                || project.getVersions().stream().noneMatch(version -> version.getScanResult() != null
                && version.getScanResult().getStatus() == ScanStatus.SCANNING)) {
            webhookService.triggerAdminNewProjectWebhook(project);
        }
    }

    private void requireVerifiedEmail(User user, String action) {
        if (!user.isEmailVerified()) {
            throw new ProjectOperationForbiddenException(
                    "Verify your email address before you can " + action + ".");
        }
    }
}
