package net.modtale.service.project;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.auth.ApiKeyService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@Service
public class TeamRoleService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ApiKeyService apiKeyService;

    public TeamRoleService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ApiKeyService apiKeyService
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.apiKeyService = apiKeyService;
    }

    public Project createProjectRole(String id, String name, String color, Set<ApiKey.ApiPermission> perms, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_MEMBER_EDIT_ROLE",
                "You do not have permission to manage project roles.");
        if (project.getProjectRoles() == null) {
            project.setProjectRoles(new ArrayList<>());
        }
        if (project.getProjectRoles().size() >= 20) {
            throw new InvalidProjectRequestException("Projects can have at most 20 custom roles.");
        }

        project.getProjectRoles().add(new Project.ProjectRole(UUID.randomUUID().toString(), name, color, perms));
        saveProject(project);
        return project;
    }

    public Project updateProjectRole(String id, String roleId, String name, String color, Set<ApiKey.ApiPermission> perms, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_MEMBER_EDIT_ROLE",
                "You do not have permission to manage project roles.");

        Project.ProjectRole role = project.getProjectRoles().stream()
                .filter(existingRole -> existingRole.getId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new InvalidProjectRequestException("We couldn't find that project role."));
        if (name != null) {
            role.setName(name);
        }
        if (color != null) {
            role.setColor(color);
        }
        if (perms != null) {
            role.setPermissions(perms);
            if (project.getTeamMembers() != null) {
                project.getTeamMembers().stream()
                        .filter(member -> roleId.equals(member.getRoleId()))
                        .forEach(member -> apiKeyService.syncUserProjectPermissions(member.getUserId(), id, perms));
            }
        }
        saveProject(project);
        return project;
    }

    public Project deleteProjectRole(String id, String roleId, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_MEMBER_EDIT_ROLE",
                "You do not have permission to manage project roles.");

        boolean inUse = (project.getTeamMembers() != null && project.getTeamMembers().stream().anyMatch(member -> roleId.equals(member.getRoleId())))
                || (project.getTeamInvites() != null && project.getTeamInvites().stream().anyMatch(invite -> roleId.equals(invite.getRoleId())));
        if (inUse) {
            throw new InvalidProjectRequestException("You can't delete a role while contributors or invites are still assigned to it.");
        }

        project.getProjectRoles().removeIf(role -> role.getId().equals(roleId));
        saveProject(project);
        return project;
    }

    private void saveProject(Project project) {
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }
}
