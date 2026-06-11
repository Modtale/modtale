package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.auth.ApiKeyService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceTest {

    private TeamService service;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private ProjectService projectService;
    private ProjectMutationGuard projectMutationGuard;
    private NotificationService notificationService;
    private ApiKeyService apiKeyService;
    private AccessControlService accessControlService;
    private ProjectAccessService projectAccessService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        projectService = mock(ProjectService.class);
        projectMutationGuard = new ProjectMutationGuard();
        notificationService = mock(NotificationService.class);
        apiKeyService = mock(ApiKeyService.class);
        accessControlService = mock(AccessControlService.class);
        projectAccessService = new ProjectAccessService(projectService, accessControlService);
        TeamNotificationService teamNotificationService = new TeamNotificationService(notificationService, projectService);

        TeamTransferService teamTransferService = new TeamTransferService(
                projectRepository,
                userRepository,
                projectService,
                projectAccessService,
                projectMutationGuard,
                teamNotificationService,
                apiKeyService,
                accessControlService
        );
        TeamRoleService teamRoleService = new TeamRoleService(
                projectRepository,
                projectService,
                projectAccessService,
                apiKeyService
        );
        TeamMembershipService teamMembershipService = new TeamMembershipService(
                projectRepository,
                userRepository,
                projectService,
                projectAccessService,
                projectMutationGuard,
                teamNotificationService,
                apiKeyService,
                accessControlService
        );
        service = new TeamService(
                teamTransferService,
                teamRoleService,
                teamMembershipService
        );
    }

    @Test
    void requestTransferSetsPendingTargetAndSendsANotification() {
        Project project = project("project-1");
        project.setAuthorId("owner-1");

        User requester = user("owner-1", "Owner");
        User target = user("user-2", "Target");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, requester, "PROJECT_TRANSFER_REQUEST")).thenReturn(true);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(userRepository.findById("owner-1")).thenReturn(Optional.of(requester));

        service.requestTransfer("project-1", "user-2", requester);

        assertEquals("user-2", project.getPendingTransferTo());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(notificationService).sendNotifcation(
                eq(List.of("user-2")),
                eq("Transfer Request"),
                eq("Owner wants to transfer 'Sky Tools' to you."),
                eq(java.net.URI.create("/dashboard/projects")),
                eq(project.getImageUrl()),
                eq(net.modtale.model.user.NotificationType.TRANSFER_REQUEST),
                eq(java.util.Map.of("projectId", "project-1", "action", "TRANSFER_REQUEST"))
        );
    }

    @Test
    void removeContributorAllowsMembersToRemoveThemselvesAndClearsApiKeyPermissions() {
        Project project = project("project-1");
        project.setTeamMembers(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-1"))));

        User requester = user("user-2", "Ada");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, requester, "PROJECT_TEAM_REMOVE")).thenReturn(false);

        service.removeContributor("project-1", "user-2", requester);

        assertTrue(project.getTeamMembers().isEmpty());
        verify(apiKeyService).syncUserProjectPermissions(eq("user-2"), eq("project-1"), argThat(Set::isEmpty));
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void resolveTransferAcceptsOwnershipTransferAndRemovesTheNewOwnerFromTeamMembers() {
        Project project = project("project-1");
        project.setAuthorId("owner-1");
        project.setPendingTransferTo("user-2");
        project.setTeamMembers(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-1"))));

        User responder = user("user-2", "Bea");
        User oldOwner = user("owner-1", "Owner");
        User newOwner = user("user-2", "Bea");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(userRepository.findById("owner-1")).thenReturn(Optional.of(oldOwner));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(newOwner));

        service.resolveTransfer("project-1", true, responder);

        assertEquals("user-2", project.getAuthorId());
        assertNull(project.getPendingTransferTo());
        assertTrue(project.getTeamMembers().isEmpty());
        verify(apiKeyService).syncUserProjectPermissions(eq("owner-1"), eq("project-1"), argThat(Set::isEmpty));
        verify(notificationService).sendNotifcation(
                eq(List.of("owner-1")),
                eq("Transfer Accepted"),
                eq("Sky Tools transferred to Bea"),
                eq(java.net.URI.create("/projects/project-1")),
                eq(project.getImageUrl())
        );
    }

    @Test
    void acceptInviteMovesTheInviteToTeamMembersAndNotifiesTheOwner() {
        Project project = project("project-1");
        project.setAuthorId("owner-1");
        project.setTeamInvites(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-1"))));

        User invitee = user("user-2", "Ada");
        invitee.setAvatarUrl("https://cdn.example/ada.png");
        User owner = user("owner-1", "Owner");

        when(userRepository.findById("user-2")).thenReturn(Optional.of(invitee));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(userRepository.findById("owner-1")).thenReturn(Optional.of(owner));
        when(projectService.getProjectLink(project)).thenReturn("/mod/sky-tools~project-1");

        service.acceptInvite("project-1", "user-2");

        assertTrue(project.getTeamInvites().isEmpty());
        assertEquals(1, project.getTeamMembers().size());
        assertEquals("user-2", project.getTeamMembers().getFirst().getUserId());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(notificationService).sendNotifcation(
                eq(List.of("owner-1")),
                eq("Invite Accepted"),
                eq("Ada joined Sky Tools"),
                eq(java.net.URI.create("/mod/sky-tools~project-1")),
                eq("https://cdn.example/ada.png")
        );
        assertNull(project.getPendingTransferTo());
    }

    private static Project project(String id) {
        Project project = new Project();
        project.setId(id);
        project.setTitle("Sky Tools");
        project.setImageUrl("https://cdn.example/project.png");
        project.setTeamMembers(new ArrayList<>());
        project.setTeamInvites(new ArrayList<>());
        return project;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
