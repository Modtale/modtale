package net.modtale.controller.user;

import java.net.URI;
import java.util.List;
import java.util.Map;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.dto.user.NotificationDTO;
import net.modtale.model.user.Notification;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    private NotificationController controller;
    private NotificationService notificationService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        accountService = mock(AccountService.class);
        controller = new NotificationController(notificationService, accountService);
    }

    @Test
    void getUserNotificationsRequiresAuthenticatedUser() {
        when(accountService.requireCurrentUser("viewing notifications"))
                .thenThrow(new UnauthorizedException("You need to sign in before viewing notifications."));

        assertThrows(UnauthorizedException.class, () -> controller.getUserNotifications());
    }

    @Test
    void getUserNotificationsMapsDomainNotificationsToDtos() {
        User user = user("user-1");
        Notification notification = new Notification(
                "user-1",
                "Build complete",
                "Your project is ready.",
                URI.create("https://modtale.net/mod/sky-tools"),
                "https://cdn.modtale.net/icon.png",
                NotificationType.INFO,
                Map.of("projectId", "project-1")
        );
        notification.setId("notif-1");
        notification.setRead(true);

        when(accountService.requireCurrentUser("viewing notifications")).thenReturn(user);
        when(notificationService.getUserNotifications("user-1")).thenReturn(List.of(notification));

        var response = controller.getUserNotifications();

        assertEquals(200, response.getStatusCode().value());

        List<?> body = assertInstanceOf(List.class, response.getBody());
        NotificationDTO dto = assertInstanceOf(NotificationDTO.class, body.getFirst());
        assertEquals("notif-1", dto.id());
        assertEquals("Build complete", dto.title());
        assertEquals(true, dto.read());
        assertEquals("project-1", dto.metadata().get("projectId"));
    }

    @Test
    void markAllAsReadUsesCurrentUserId() {
        User user = user("user-1");
        when(accountService.requireCurrentUser("updating notifications")).thenReturn(user);

        var response = controller.markAllAsRead();

        assertEquals(200, response.getStatusCode().value());
        verify(notificationService).markAllAsRead("user-1");
    }

    @Test
    void deleteNotificationUsesCurrentUserId() {
        User user = user("user-1");
        when(accountService.requireCurrentUser("deleting notifications")).thenReturn(user);

        var response = controller.deleteNotification("notif-1");

        assertEquals(200, response.getStatusCode().value());
        verify(notificationService).deleteNotification("notif-1", "user-1");
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
