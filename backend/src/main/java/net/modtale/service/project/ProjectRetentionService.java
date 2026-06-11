package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Service
public class ProjectRetentionService {

    private static final EnumSet<ProjectStatus> RESTORABLE_STATUSES = EnumSet.of(
            ProjectStatus.PUBLISHED,
            ProjectStatus.DRAFT,
            ProjectStatus.PRIVATE,
            ProjectStatus.UNLISTED,
            ProjectStatus.ARCHIVED
    );

    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final ProjectDeletionService projectDeletionService;

    public ProjectRetentionService(
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            ProjectDeletionService projectDeletionService
    ) {
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.projectDeletionService = projectDeletionService;
    }

    public void softDeleteProject(String id, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_DELETE",
                "You do not have permission to delete this project.");
        projectMutationGuard.ensureEditable(project);
        projectDeletionService.softDelete(project);
    }

    public void softDelete(Project project) {
        projectDeletionService.softDelete(project);
    }

    public void hardDelete(Project project) {
        if (project.getStatus() != ProjectStatus.DELETED) {
            throw new IllegalArgumentException("Project must be in DELETED state.");
        }
        projectDeletionService.hardDelete(project);
    }

    public void restore(Project project, ProjectStatus targetStatus) {
        if (project.getStatus() != ProjectStatus.DELETED) {
            throw new IllegalArgumentException("Project not in a recoverable state.");
        }
        if (targetStatus == null || !RESTORABLE_STATUSES.contains(targetStatus)) {
            throw new IllegalArgumentException("Invalid status.");
        }
        projectDeletionService.restore(project, targetStatus);
    }
}
