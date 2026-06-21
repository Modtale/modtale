package net.modtale.service.project.lifecycle;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

@Service
public class LifecycleService {

    private final ProjectDraftWorkflowService projectDraftWorkflowService;
    private final ProjectPublicationService projectPublicationService;

    public LifecycleService(
            ProjectDraftWorkflowService projectDraftWorkflowService,
            ProjectPublicationService projectPublicationService
    ) {
        this.projectDraftWorkflowService = projectDraftWorkflowService;
        this.projectPublicationService = projectPublicationService;
    }

    public Project createDraft(String title, String description, ProjectClassification classification,
                               User user, String ownerId, String customSlug) {
        return projectDraftWorkflowService.createDraft(title, description, classification, user, ownerId, customSlug);
    }

    public void submitProject(String id, User user) {
        projectDraftWorkflowService.submitProject(id, user);
    }

    public void revertProjectToDraft(String id, User user) {
        projectPublicationService.revertProjectToDraft(id, user);
    }

    public void archiveProject(String id, User user) {
        projectPublicationService.archiveProject(id, user);
    }

    public void unlistProject(String id, User user) {
        projectPublicationService.unlistProject(id, user);
    }

    public void privateProject(String id, User user) {
        projectPublicationService.privateProject(id, user);
    }

    public void publishProject(String id, User user) {
        projectPublicationService.publishProject(id, user);
    }

    public void updateProjectStatus(String id, ProjectStatus status, User user, String permissionRequired) {
        projectPublicationService.updateProjectStatus(id, status, user, permissionRequired);
    }
}
