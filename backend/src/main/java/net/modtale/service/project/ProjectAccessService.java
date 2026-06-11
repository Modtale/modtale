package net.modtale.service.project;

import net.modtale.exception.ProjectNotFoundException;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.VersionOperationForbiddenException;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.security.AccessControlService;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

    private final ProjectService projectService;
    private final AccessControlService accessControlService;

    public ProjectAccessService(ProjectService projectService, AccessControlService accessControlService) {
        this.projectService = projectService;
        this.accessControlService = accessControlService;
    }

    public Project requireProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) {
            throw new ProjectNotFoundException("We couldn't find that project.");
        }
        return project;
    }

    public Project requireProjectPermission(String id, User requester, String permission, String failureMessage) {
        Project project = requireProject(id);
        if (!accessControlService.hasProjectPermission(project, requester, permission)) {
            throw new ProjectOperationForbiddenException(failureMessage);
        }
        return project;
    }

    public Project requireVersionProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) {
            throw new ResourceNotFoundException("We couldn't find that project.");
        }
        return project;
    }

    public Project requireVersionPermission(String id, User requester, String permission, String failureMessage) {
        Project project = requireVersionProject(id);
        if (!accessControlService.hasProjectPermission(project, requester, permission)) {
            throw new VersionOperationForbiddenException(failureMessage);
        }
        return project;
    }
}
