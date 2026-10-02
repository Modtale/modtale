package net.modtale.service.project.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.auth.ApiKeyService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceTest {

    private TeamService service;
    private net.modtale.service.admin.review.ProjectReviewPersistence reviewPersistence;
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
        reviewPersistence = mock(net.modtale.service.admin.review.ProjectReviewPersistence.class);
        when(reviewPersistence.capture(anyString(), anyString())).thenAnswer(invocation ->
                new net.modtale.service.admin.review.ProjectReviewPersistence.Snapshot(new org.bson.Document(), projectService.getRawProjectById(invocation.getArgument(0))));
        when(reviewPersistence.applyTeam(any())).thenReturn(true);
        userRepository = mock(UserRepository.class);
        projectService = mock(ProjectService.class);
        projectMutationGuard = new ProjectMutationGuard();
        notificationService = mock(NotificationService.class);
        apiKeyService = mock(ApiKeyService.class);
        accessControlService = mock(AccessControlService.class);
        projectAccessService = new ProjectAccessService(projectService, accessControlService);
        TeamNotificationService teamNotificationService = new TeamNotificationService(notificationService, projectService);

        TeamTransferService teamTransferService = new TeamTransferService(
                reviewPersistence,
                userRepository,
                projectService,
                projectAccessService,
                projectMutationGuard,
                teamNotificationService,
                apiKeyService,
                accessControlService
        );
        TeamRoleService teamRoleService = new TeamRoleService(
                reviewPersistence,
                projectService,
                projectAccessService,
                apiKeyService
        );
        TeamMembershipService teamMembershipService = new TeamMembershipService(
                reviewPersistence,
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
        verify(reviewPersistence).applyTeam(any());
        verify(projectService).evictProjectCache(project);
        verify(notificationService).sendNotifcation(
                eq(List.of("user-2")),
                eq("Transfer Request"),
                eq("Owner wants to transfer 'Sky Tools' to you."),
                eq(java.net.URI.create("/dashboard/projects")),
                eq(project.getImageUrl()),
                eq(net.modtale.model.user.NotificationType.TRANSFER_REQUEST),
                eq(java.util.Map.of("projectId", "project-1", "action", "TRANSFER_REQUEST",
                        "requestId", project.getPendingTransferRequestId(), "targetUserId", "user-2"))
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
        verify(reviewPersistence).applyTeam(any());
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void inviteContributorPersistsTheRoleAndSendsCanonicalProjectMetadata() {
        Project project = project("project-1");
        project.setProjectRoles(new ArrayList<>(List.of(new Project.ProjectRole(
                "role-1",
                "Writer",
                "#112233",
                Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA)
        ))));
        User requester = user("owner-1", "Owner");
        User invitee = user("user-2", "Ada");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, requester, "PROJECT_TEAM_INVITE")).thenReturn(true);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(invitee));

        service.inviteContributor("project-1", "user-2", "role-1", requester);

        assertEquals(1, project.getTeamInvites().size());
        assertEquals("role-1", project.getTeamInvites().getFirst().getRoleId());
        verify(reviewPersistence).applyTeam(any());
        verify(notificationService).sendNotifcation(
                eq(List.of("user-2")),
                eq("Contributor Invite"),
                eq("You have been invited to Sky Tools"),
                eq(java.net.URI.create("/dashboard/projects")),
                eq(project.getImageUrl()),
                eq(net.modtale.model.user.NotificationType.CONTRIBUTOR_INVITE),
                eq(java.util.Map.of("projectId", "project-1", "action", "CONTRIBUTOR_INVITE",
                        "requestId", project.getTeamInvites().getFirst().getRequestId()))
        );
    }

    @Test
    void resolveTransferAcceptsOwnershipTransferAndRemovesTheNewOwnerFromTeamMembers() {
        Project project = project("project-1");
        project.setAuthorId("owner-1");
        project.setPendingTransferTo("user-2");
        project.setPendingTransferRequestId("request-1"); project.setPendingTransferOwnerId("owner-1");
        project.setPendingTransferExpiresAt(System.currentTimeMillis() + 60000);
        project.setTeamMembers(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-1"))));

        User responder = user("user-2", "Bea");
        User oldOwner = user("owner-1", "Owner");
        User newOwner = user("user-2", "Bea");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(userRepository.findById("owner-1")).thenReturn(Optional.of(oldOwner));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(newOwner));

        when(reviewPersistence.resolveTransfer(any(), eq("request-1"))).thenReturn(false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.resolveTransfer("project-1", true, "request-1", responder));
        verifyNoInteractions(apiKeyService, notificationService);
        project.setAuthorId("owner-1"); project.setPendingTransferTo("user-2");
        project.setPendingTransferRequestId("request-1"); project.setPendingTransferOwnerId("owner-1");
        project.setPendingTransferExpiresAt(System.currentTimeMillis() + 60000);
        project.setTeamMembers(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-1"))));
        clearInvocations(reviewPersistence);
        when(reviewPersistence.resolveTransfer(any(), eq("request-1"))).thenReturn(true);

        service.resolveTransfer("project-1", true, "request-1", responder);

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
        project.setProjectRoles(new ArrayList<>(List.of(new Project.ProjectRole(
                "role-1",
                "Writer",
                "#112233",
                Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA)
        ))));

        User invitee = user("user-2", "Ada");
        invitee.setAvatarUrl("https://cdn.example/ada.png");
        User owner = user("owner-1", "Owner");

        when(userRepository.findById("user-2")).thenReturn(Optional.of(invitee));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(userRepository.findById("owner-1")).thenReturn(Optional.of(owner));
        when(projectService.getProjectLink(project)).thenReturn("/mod/sky-tools~project-1");

        var invitation = project.getTeamInvites().getFirst(); invitation.setRequestId("invite-1");
        invitation.setRequestExpiresAt(System.currentTimeMillis()+60000); invitation.setRequestOwnerId(project.getAuthorId());
        invitation.setRequestPermissions(Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA));
        when(reviewPersistence.resolveContributorInvite(any(), any(), any(), anyBoolean())).thenReturn(true);
        service.acceptInvite("project-1", "user-2", "invite-1");

        assertTrue(project.getTeamInvites().isEmpty());
        assertEquals(1, project.getTeamMembers().size());
        assertEquals("user-2", project.getTeamMembers().getFirst().getUserId());
        verify(reviewPersistence).resolveContributorInvite(any(), eq("user-2"), eq("invite-1"), eq(true));
        verify(projectService).evictProjectCache(project);
        verify(apiKeyService).syncUserProjectPermissions(
                "user-2",
                "project-1",
                Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA)
        );
        verify(notificationService).sendNotifcation(
                eq(List.of("owner-1")),
                eq("Invite Accepted"),
                eq("Ada joined Sky Tools"),
                eq(java.net.URI.create("/mod/sky-tools~project-1")),
                eq("https://cdn.example/ada.png")
        );
        assertNull(project.getPendingTransferTo());
    }

    @Test
    void acceptInviteRejectsMissingInvitesInsteadOfReportingFalseSuccess() {
        Project project = project("project-1");
        User invitee = user("user-2", "Ada");

        when(userRepository.findById("user-2")).thenReturn(Optional.of(invitee));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);

        assertThrows(
                InvalidProjectRequestException.class,
                () -> service.acceptInvite("project-1", "user-2", "invite-1")
        );
    }

    @Test void failedRoleChangeDoesNotSynchronizeKeysOrNotify() {
        var project = project("project-1");
        project.setProjectRoles(new ArrayList<>(List.of(new Project.ProjectRole("role-1", "Writer", "#fff", Set.of()))));
        project.setTeamMembers(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-1"))));
        var owner = user("owner-1", "Owner");
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, owner, "PROJECT_MEMBER_EDIT_ROLE")).thenReturn(true);
        when(reviewPersistence.applyTeam(any())).thenReturn(false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.updateProjectRole(
                "project-1", "role-1", null, null, Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA), owner));
        verifyNoInteractions(apiKeyService, notificationService);
        verify(projectService, never()).evictProjectCache(any());
    }
    @Test void failedContributorRemovalDoesNotRevokeKeys() {
        var project = project("project-1");
        project.setTeamMembers(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-1"))));
        var member = user("user-2", "Member");
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(reviewPersistence.applyTeam(any())).thenReturn(false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.removeContributor("project-1", "user-2", member));
        verifyNoInteractions(apiKeyService, notificationService);
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
    @Test void staleExpiredAndOwnerChangedTransferResponsesCannotResolve() {
        Project project = project("project-1"); project.setAuthorId("owner-1"); project.setPendingTransferTo("user-2");
        project.setPendingTransferRequestId("new-request"); project.setPendingTransferOwnerId("owner-1");
        project.setPendingTransferExpiresAt(System.currentTimeMillis() + 60000);
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        User responder = user("user-2", "Bea");
        assertThrows(net.modtale.exception.InvalidProjectRequestException.class, () -> service.resolveTransfer("project-1", true, "old-request", responder));
        project.setPendingTransferExpiresAt(1);
        assertThrows(net.modtale.exception.InvalidProjectRequestException.class, () -> service.resolveTransfer("project-1", true, "new-request", responder));
        project.setPendingTransferExpiresAt(System.currentTimeMillis() + 60000); project.setAuthorId("new-owner");
        assertThrows(net.modtale.exception.InvalidProjectRequestException.class, () -> service.resolveTransfer("project-1", true, "new-request", responder));
        verify(reviewPersistence, never()).resolveTransfer(any(), any()); verifyNoInteractions(apiKeyService, notificationService);
    }

}
