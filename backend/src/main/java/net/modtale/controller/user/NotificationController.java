package net.modtale.controller.user;

import net.modtale.mapper.UserResponseMapper;
import net.modtale.model.user.User;
import net.modtale.model.dto.user.NotificationDTO;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.user.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AccountService accountService;

    public NotificationController(NotificationService notificationService, AccountService accountService) {
        this.notificationService = notificationService;
        this.accountService = accountService;
    }

    @GetMapping
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_READ', authentication)")
    public ResponseEntity<List<NotificationDTO>> getUserNotifications() {
        User user = accountService.requireCurrentUser("viewing notifications");
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getId()).stream()
                .map(UserResponseMapper::toNotificationDTO)
                .toList());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        User user = accountService.requireCurrentUser("updating notifications");
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unread")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<Void> markAsUnread(@PathVariable String id) {
        User user = accountService.requireCurrentUser("updating notifications");
        notificationService.markAsUnread(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<Void> markAllAsRead() {
        User user = accountService.requireCurrentUser("updating notifications");
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_DELETE', authentication)")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        User user = accountService.requireCurrentUser("deleting notifications");
        notificationService.deleteNotification(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear-all")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_DELETE', authentication)")
    public ResponseEntity<Void> clearAll() {
        User user = accountService.requireCurrentUser("clearing notifications");
        notificationService.clearAll(user.getId());
        return ResponseEntity.ok().build();
    }
}
