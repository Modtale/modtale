package net.modtale.service.communication;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import net.modtale.model.project.Project;
import net.modtale.model.user.Notification;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.NotificationRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationService service;
    private ProjectNotificationService projectNotificationService;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private MongoTemplate mongoTemplate;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        projectService = mock(ProjectService.class);
        Executor executor = Runnable::run;
        NotificationDeliveryService notificationDeliveryService = new NotificationDeliveryService(notificationRepository, userRepository);

        service = new NotificationService(
                notificationRepository,
                userRepository,
                mongoTemplate,
                notificationDeliveryService
        );
        projectNotificationService = new ProjectNotificationService(
                userRepository,
                projectRepository,
                notificationDeliveryService,
                projectService,
                executor
        );
    }

    @Test
    void sendNotificationExpandsOrganizationTargetsToAdminMembers() {
        User organization = new User();
        organization.setId("org-1");
        organization.setUsername("Sky Forge");
        organization.setAccountType(User.AccountType.ORGANIZATION);
        User.OrganizationMember adminMember = new User.OrganizationMember("admin-1", null);
        adminMember.setRole("ADMIN");
        User.OrganizationMember viewerMember = new User.OrganizationMember("viewer-1", null);
        viewerMember.setRole("VIEWER");
        organization.setOrganizationMembers(List.of(adminMember, viewerMember));

        User directUser = new User();
        directUser.setId("user-1");

        when(userRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(directUser));

        service.sendNotifcation(
                List.of("org-1", "user-1"),
                "Release Ready",
                "A new build is available.",
                URI.create("/dashboard/releases"),
                "https://cdn.example/icon.png",
                NotificationType.INFO,
                Map.of("source", "release")
        );

        ArgumentCaptor<List<Notification>> notifications = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notifications.capture());

        List<Notification> saved = notifications.getValue();
        assertEquals(2, saved.size());
        assertIterableEquals(List.of("admin-1", "user-1"), saved.stream().map(Notification::getUserId).toList());
        assertEquals("[Sky Forge] Release Ready", saved.getFirst().getTitle());
        assertEquals("Release Ready", saved.get(1).getTitle());
    }

    @Test
    void deleteNotificationVoidsOrganizationInvitesBeforeRemovingTheNotification() {
        Notification notification = new Notification(
                "user-1",
                "Organization Invite",
                "Join us",
                URI.create("/dashboard/orgs"),
                "https://cdn.example/org.png",
                NotificationType.ORG_INVITE,
                Map.of("orgId", "org-1")
        );
        notification.setId("notification-1");

        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));

        service.deleteNotification("notification-1", "user-1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(User.class));
        verify(notificationRepository).delete(notification);

        assertEquals("org-1", queryCaptor.getValue().getQueryObject().get("_id"));
        assertTrue(updateCaptor.getValue().getUpdateObject().toJson().contains("pendingOrgInvites"));
    }

    @Test
    void notifyUpdatesOnlySendsNotificationsToUsersWhoOptIn() {
        Project project = new Project();
        project.setId("project-1");
        project.setTitle("Sky Tools");
        project.setImageUrl("https://cdn.example/project.png");

        User optedIn = new User();
        optedIn.setId("user-1");
        User optedOut = new User();
        optedOut.setId("user-2");
        optedOut.getNotificationPreferences().setProjectUpdates(User.NotificationLevel.OFF);

        when(userRepository.findByLikedModIdsContaining("project-1")).thenReturn(List.of(optedIn, optedOut));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(optedIn));
        when(projectService.getProjectLink(project)).thenReturn("/mod/sky-tools~project-1");

        projectNotificationService.notifyUpdates(project, "1.2.0");

        ArgumentCaptor<List<Notification>> notifications = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notifications.capture());

        List<Notification> saved = notifications.getValue();
        assertEquals(1, saved.size());
        assertEquals("user-1", saved.getFirst().getUserId());
        assertEquals("Update: Sky Tools", saved.getFirst().getTitle());
    }
}
